/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.workflowelement.externalchangelog;



import static org.opends.messages.CoreMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.getExceptionMessage;
import static org.opends.server.util.StaticUtils.needsBase64Encoding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.controls.EntryChangelogNotificationControl;
import org.opends.server.controls.ExternalChangelogRequestControl;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.controls.MatchedValuesControl;
import org.opends.server.controls.PersistentSearchControl;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.PersistentSearch;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.SearchOperationWrapper;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ExternalChangeLogSession;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.ECLUpdateMsg;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.StartECLSessionMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.service.ReplicationDomain;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.Attributes;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.Privilege;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.opends.server.types.operation.PostOperationSearchOperation;
import org.opends.server.types.operation.PreOperationSearchOperation;
import org.opends.server.types.operation.SearchEntrySearchOperation;
import org.opends.server.types.operation.SearchReferenceSearchOperation;
import org.opends.server.util.Base64;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.TimeThread;



/**
 * This class defines an operation used to search for entries in a local backend
 * of the Directory Server.
 */
public class ECLSearchOperation
       extends SearchOperationWrapper
       implements PreOperationSearchOperation, PostOperationSearchOperation,
                  SearchEntrySearchOperation, SearchReferenceSearchOperation
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The ECL Start Session we'll send to the RS.
   */
  private StartECLSessionMsg startECLSessionMsg;

  // The set of objectclasses that will be used in ECL entries.
  private static HashMap<ObjectClass,String> eclObjectClasses;

  // The associated DN.
  private DN rootBaseDN;

  // The cookie received in the ECL request control coming along
  // with the request.
  MultiDomainServerState requestCookie = null;

  /**
   * The replication server in which the search on ECL is to be performed.
   */
  protected ReplicationServer replicationServer;

  /**
   * Indicates whether we should actually process the search.  This should
   * only be false if it's a persistent search with changesOnly=true.
   */
  protected boolean changesOnly;

  /**
   * The client connection for the search operation.
   */
  protected ClientConnection clientConnection;

  /**
   * The base DN for the search.
   */
  protected DN baseDN;

  /**
   * The persistent search request, if applicable.
   */
  protected PersistentSearch persistentSearch;

  /**
   * The filter for the search.
   */
  protected SearchFilter filter;

  private ExternalChangeLogSession eclSession;

  // The set of supported controls for this WE
  private HashSet<String> supportedControls;

  // The set of supported features for this WE
  private HashSet<String> supportedFeatures;

  String privateDomainsBaseDN;

  /**
   * Creates a new operation that may be used to search for entries in a local
   * backend of the Directory Server.
   *
   * @param  search  The operation to process.
   */
  public ECLSearchOperation(SearchOperation search)
  {
    super(search);

    try
    {
      rootBaseDN = DN.decode(ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT);
    }
    catch (Exception e){}

    // Construct the set of objectclasses to include in the base monitor entry.
    eclObjectClasses = new LinkedHashMap<ObjectClass,String>(2);
    ObjectClass topOC = DirectoryServer.getObjectClass(OC_TOP, true);
    eclObjectClasses.put(topOC, OC_TOP);
    ObjectClass eclEntryOC = DirectoryServer.getObjectClass(OC_CHANGELOG_ENTRY,
                                                           true);
    eclObjectClasses.put(eclEntryOC, OC_CHANGELOG_ENTRY);


    // Define an empty sets for the supported controls and features.
    supportedControls = new HashSet<String>(0);
    supportedControls.add(ServerConstants.OID_SERVER_SIDE_SORT_REQUEST_CONTROL);
    supportedControls.add(ServerConstants.OID_VLV_REQUEST_CONTROL);
    supportedFeatures = new HashSet<String>(0);

    ECLWorkflowElement.attachLocalOperation(search, this);
  }



  /**
   * Process this search operation against a local backend.
   *
   * @param wfe
   *          The local backend work-flow element.
   * @throws CanceledOperationException
   *           if this operation should be cancelled
   */
  public void processECLSearch(ECLWorkflowElement wfe)
      throws CanceledOperationException
  {
    boolean executePostOpPlugins = false;
    this.replicationServer = wfe.getReplicationServer();

    clientConnection = getClientConnection();

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
      DirectoryServer.getPluginConfigManager();
    changesOnly = false;

    // Check for a request to cancel this operation.
    checkIfCanceled(false);

    // Create a labeled block of code that we can break out of if a problem is
    // detected.
searchProcessing:
    {
      // Process the search base and filter to convert them from their raw forms
      // as provided by the client to the forms required for the rest of the
      // search processing.
      baseDN = getBaseDN();
      filter = getFilter();

      if ((baseDN == null) || (filter == null)){
        break searchProcessing;
      }

      // Check to see if there are any controls in the request.  If so, then
      // see if there is any special processing required.
      try
      {
        this.requestCookie = null;
        handleRequestControls();
        if (this.requestCookie == null)
        {
          setResponseData(new DirectoryException(
              ResultCode.OPERATIONS_ERROR,
              Message.raw(Category.SYNC, Severity.FATAL_ERROR,
                  "Cookie control expected")));
          break searchProcessing;
        }
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        setResponseData(de);
        break searchProcessing;
      }

      // Check for a request to cancel this operation.
      checkIfCanceled(false);

      // Invoke the pre-operation search plugins.
      executePostOpPlugins = true;
      PluginResult.PreOperation preOpResult =
          pluginConfigManager.invokePreOperationSearchPlugins(this);
      if (!preOpResult.continueProcessing())
      {
        setResultCode(preOpResult.getResultCode());
        appendErrorMessage(preOpResult.getErrorMessage());
        setMatchedDN(preOpResult.getMatchedDN());
        setReferralURLs(preOpResult.getReferralURLs());
        break searchProcessing;
      }


      // Check for a request to cancel this operation.
      checkIfCanceled(false);


      // Test existence of the RS
      if (replicationServer == null)
      {
        setResultCode(ResultCode.OPERATIONS_ERROR);
        appendErrorMessage(ERR_SEARCH_BASE_DOESNT_EXIST.get(
                                String.valueOf(baseDN)));
        break searchProcessing;
      }


      // We'll set the result code to "success".  If a problem occurs, then it
      // will be overwritten.
      setResultCode(ResultCode.SUCCESS);


      // If there's a persistent search, then register it with the server.
      if (persistentSearch != null)
      {
        wfe.registerPersistentSearch(persistentSearch);
        persistentSearch.enable();
      }

      // Process the search.
      try
      {
        processSearch();
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        setResponseData(de);

        if (persistentSearch != null)
        {
          persistentSearch.cancel();
          setSendResponse(true);
        }

        break searchProcessing;
      }
      catch (CanceledOperationException coe)
      {
        if (persistentSearch != null)
        {
          persistentSearch.cancel();
          setSendResponse(true);
        }

        throw coe;
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        setResultCode(DirectoryServer.getServerErrorResultCode());
        appendErrorMessage(ERR_SEARCH_BACKEND_EXCEPTION.get(
                                getExceptionMessage(e)));

        if (persistentSearch != null)
        {
          persistentSearch.cancel();
          setSendResponse(true);
        }

        break searchProcessing;
      }
    }


    // Check for a request to cancel this operation.
    checkIfCanceled(false);

    // Invoke the post-operation search plugins.
    if (executePostOpPlugins)
    {
      PluginResult.PostOperation postOpResult =
           pluginConfigManager.invokePostOperationSearchPlugins(this);
      if (!postOpResult.continueProcessing())
      {
        setResultCode(postOpResult.getResultCode());
        appendErrorMessage(postOpResult.getErrorMessage());
        setMatchedDN(postOpResult.getMatchedDN());
        setReferralURLs(postOpResult.getReferralURLs());
      }
    }
  }


  /**
   * Handles any controls contained in the request.
   *
   * @throws  DirectoryException  If there is a problem with any of the request
   *                              controls.
   */
  protected void handleRequestControls()
          throws DirectoryException
  {
    List<Control> requestControls  = getRequestControls();
    if ((requestControls != null) && (! requestControls.isEmpty()))
    {
      for (int i=0; i < requestControls.size(); i++)
      {
        Control c   = requestControls.get(i);
        String  oid = c.getOID();
        if (! AccessControlConfigManager.getInstance().
                   getAccessControlHandler().isAllowed(baseDN, this, c))
        {
          throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                         ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(oid));
        }

        if (oid.equals(OID_ECL_COOKIE_EXCHANGE_CONTROL))
        {
          ExternalChangelogRequestControl eclControl =
            getRequestControl(ExternalChangelogRequestControl.DECODER);
          this.requestCookie = eclControl.getCookie();
        }
        else if (oid.equals(OID_LDAP_ASSERTION))
        {
          LDAPAssertionRequestControl assertControl =
                getRequestControl(LDAPAssertionRequestControl.DECODER);

          try
          {
            // FIXME -- We need to determine whether the current user has
            //          permission to make this determination.
            SearchFilter assertionFilter = assertControl.getSearchFilter();
            Entry entry;
            try
            {
              entry = DirectoryServer.getEntry(baseDN);
            }
            catch (DirectoryException de)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, de);
              }

              throw new DirectoryException(de.getResultCode(),
                             ERR_SEARCH_CANNOT_GET_ENTRY_FOR_ASSERTION.get(
                                  de.getMessageObject()));
            }

            if (entry == null)
            {
              throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                             ERR_SEARCH_NO_SUCH_ENTRY_FOR_ASSERTION.get());
            }

            if (! assertionFilter.matchesEntry(entry))
            {
              throw new DirectoryException(ResultCode.ASSERTION_FAILED,
                                           ERR_SEARCH_ASSERTION_FAILED.get());
            }
          }
          catch (DirectoryException de)
          {
            if (de.getResultCode() == ResultCode.ASSERTION_FAILED)
            {
              throw de;
            }

            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                           ERR_SEARCH_CANNOT_PROCESS_ASSERTION_FILTER.get(
                                de.getMessageObject()), de);
          }
        }
        else if (oid.equals(OID_PROXIED_AUTH_V1))
        {
          // The requester must have the PROXIED_AUTH privilige in order to be
          // able to use this control.
          if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH, this))
          {
            throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED,
                           ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
          }

          ProxiedAuthV1Control proxyControl =
              getRequestControl(ProxiedAuthV1Control.DECODER);

          Entry authorizationEntry = proxyControl.getAuthorizationEntry();
          setAuthorizationEntry(authorizationEntry);
          if (authorizationEntry == null)
          {
            setProxiedAuthorizationDN(DN.nullDN());
          }
          else
          {
            setProxiedAuthorizationDN(authorizationEntry.getDN());
          }
        }
        else if (oid.equals(OID_PROXIED_AUTH_V2))
        {
          // The requester must have the PROXIED_AUTH privilige in order to be
          // able to use this control.
          if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH, this))
          {
            throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED,
                           ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
          }

          ProxiedAuthV2Control proxyControl =
              getRequestControl(ProxiedAuthV2Control.DECODER);

          Entry authorizationEntry = proxyControl.getAuthorizationEntry();
          setAuthorizationEntry(authorizationEntry);
          if (authorizationEntry == null)
          {
            setProxiedAuthorizationDN(DN.nullDN());
          }
          else
          {
            setProxiedAuthorizationDN(authorizationEntry.getDN());
          }
        }
        else if (oid.equals(OID_PERSISTENT_SEARCH))
        {
          PersistentSearchControl psearchControl =
            getRequestControl(PersistentSearchControl.DECODER);

          persistentSearch = new PersistentSearch(this,
                                      psearchControl.getChangeTypes(),
                                      psearchControl.getReturnECs());

          // If we're only interested in changes, then we don't actually want
          // to process the search now.
          if (psearchControl.getChangesOnly())
          {
            changesOnly = true;
          }
        }
        else if (oid.equals(OID_LDAP_SUBENTRIES))
        {
          setReturnLDAPSubentries(true);
        }
        else if (oid.equals(OID_MATCHED_VALUES))
        {
          MatchedValuesControl matchedValuesControl =
                getRequestControl(MatchedValuesControl.DECODER);
          setMatchedValuesControl(matchedValuesControl);
        }
        else if (oid.equals(OID_ACCOUNT_USABLE_CONTROL))
        {
          setIncludeUsableControl(true);
        }
        else if (oid.equals(OID_REAL_ATTRS_ONLY))
        {
          setRealAttributesOnly(true);
        }
        else if (oid.equals(OID_VIRTUAL_ATTRS_ONLY))
        {
          setVirtualAttributesOnly(true);
        }
        else if (oid.equals(OID_GET_EFFECTIVE_RIGHTS) &&
          DirectoryServer.isSupportedControl(OID_GET_EFFECTIVE_RIGHTS))
        {
          // Do nothing here and let AciHandler deal with it.
        }

        // NYI -- Add support for additional controls.

        else if (c.isCritical())
        {
          if ((replicationServer == null) || (! supportsControl(oid)))
          {
            throw new DirectoryException(
                           ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
                           ERR_SEARCH_UNSUPPORTED_CRITICAL_CONTROL.get(oid));
          }
        }
      }
    }
  }

  private void processSearch()
  throws DirectoryException, CanceledOperationException
  {
    startECLSessionMsg = new StartECLSessionMsg();
    startECLSessionMsg.setECLRequestType(
        StartECLSessionMsg.REQUEST_TYPE_FROM_COOKIE);
    startECLSessionMsg.setChangeNumber(
        new ChangeNumber(TimeThread.getTime(),(short)0, (short)0));
    startECLSessionMsg.setCrossDomainServerState(requestCookie.toString());

    if (persistentSearch==null)
      startECLSessionMsg.setPersistent(StartECLSessionMsg.NON_PERSISTENT);
    else
      if (!changesOnly)
        startECLSessionMsg.setPersistent(StartECLSessionMsg.PERSISTENT);
      else
        startECLSessionMsg.setPersistent(
            StartECLSessionMsg.PERSISTENT_CHANGES_ONLY);

    startECLSessionMsg.setFirstDraftChangeNumber(0);
    startECLSessionMsg.setLastDraftChangeNumber(0);

    // Help correlate with access log with the format: "conn=x op=y msgID=z"
    startECLSessionMsg.setOperationId(
      "conn="+String.valueOf(this.getConnectionID())
      + " op="+String.valueOf(this.getOperationID())
      + " msgID="+String.valueOf(getOperationID()));

    startECLSessionMsg.setExcludedDNs(
        MultimasterReplication.getPrivateDomains());

    // Start session
    eclSession = replicationServer.createECLSession(startECLSessionMsg);

    if (!getScope().equals(SearchScope.SINGLE_LEVEL))
    {
      // Root entry
      Entry entry = createRootEntry();
      if (matchFilter(entry))
        returnEntry(entry, null);
    }

    if (true)
    {
      // Loop on result entries
      int INITIAL=0;
      int PSEARCH=1;
      int phase=INITIAL;
      while (true)
      {

        // Check for a request to cancel this operation.
        checkIfCanceled(false);

        ECLUpdateMsg update = eclSession.getNextUpdate();
        if (update!=null)
        {
          if (phase==INITIAL)
            buildAndReturnEntry(update);
        }
        else
        {
          if (phase==INITIAL)
          {
            if (this.persistentSearch == null)
            {
              eclSession.close();
              break;
            }
            else
            {
              phase=PSEARCH;
              break;
            }
          }
        }
      }
    }
  }

  private boolean supportsControl(String oid)
  {
    return ((supportedControls != null) &&
        supportedControls.contains(oid));
  }

  /**
   * Build an ECL entry from a provided ECL msg and return it.
   * @param eclmsg The provided ECL msg.
   * @throws DirectoryException When an errors occurs.
   */
  private void buildAndReturnEntry(ECLUpdateMsg eclmsg)
  throws DirectoryException
  {
    Entry entry = null;

    // build and filter
    entry = createEntryFromMsg(eclmsg);
    if (matchFilter(entry))
    {
      List<Control> controls = new ArrayList<Control>(0);

      EntryChangelogNotificationControl clrc
      = new EntryChangelogNotificationControl(
          true,eclmsg.getCookie().toString());
      controls.add(clrc);
      boolean entryReturned = returnEntry(entry, controls);
      if (entryReturned==false)
      {
        // FIXME:ECL Handle the error case where returnEntry fails
      }
    }
  }

  /**
   * Test if the provided entry matches the filter, base and scope.
   * @param  entry The provided entry
   * @return whether the entry matches.
   * @throws DirectoryException When a problem occurs.
   */
  private boolean matchFilter(Entry entry)
  throws DirectoryException
  {
    boolean ms = entry.matchesBaseAndScope(getBaseDN(), getScope());
    boolean mf = getFilter().matchesEntry(entry);
    return (ms && mf);
  }

  /**
   * Create an ECL entry from a provided ECL msg.
   *
   * @param  eclmsg the provided ECL msg.
   * @return        the created ECL entry.
   * @throws DirectoryException When an error occurs.
   */
  public static Entry createEntryFromMsg(ECLUpdateMsg eclmsg)
  throws DirectoryException
  {
    Entry clEntry = null;

    // Get the meat fro the ecl msg
    UpdateMsg msg = eclmsg.getUpdateMsg();

    if (msg instanceof AddMsg)
    {
      AddMsg addMsg = (AddMsg)msg;

      // Map the addMsg to an LDIF string for the 'changes' attribute
      String LDIFchanges = addMsgToLDIFString(addMsg);

      clEntry = createChangelogEntry(
          eclmsg.getServiceId(),
          eclmsg.getCookie().toString(),
          DN.decode(addMsg.getDn()),
          addMsg.getChangeNumber(),
          LDIFchanges, // entry as created (in LDIF format)
          addMsg.getUniqueId(),
          null, // real time current entry
          null, // real time attrs names
          null, // hist entry attributes
          0,    // TODO:ECL G Good changelog draft compat. addMsg.getSeqnum()
      "add");

    } else
    if (msg instanceof ModifyMsg)
    {
      ModifyMsg modMsg = (ModifyMsg)msg;
      InternalClientConnection conn =
        InternalClientConnection.getRootConnection();
      try
      {
        // Map the modMsg modifications to an LDIF string
        // for the 'changes' attribute of the CL entry
        ModifyOperation modifyOperation =
          (ModifyOperation)modMsg.createOperation(conn);
        String LDIFchanges = modToLDIF(modifyOperation.getModifications());

        // TODO:ECL G Good changelog draft compat. Hist entry attributes
        // ArrayList<RawAttribute> attributes = modMsg.getEntryAttributes();
        clEntry = createChangelogEntry(
            eclmsg.getServiceId(),
            eclmsg.getCookie().toString(),
            DN.decode(modMsg.getDn()),
            modMsg.getChangeNumber(),
            LDIFchanges,
            modMsg.getUniqueId(),
            null, // real time current entry
            null, // real time attrs names
            null, // hist entry attributes
            0,    // TODO:ECL G Good changelog draft compat. modMsg.getSeqnum()
        "modify");

      }
      catch(Exception e)
      {
        // FIXME:ECL Handle error when createOperation raise DataFormatExceptin
      }
    }
    else if (msg instanceof ModifyDNMsg)
    {
      ModifyDNMsg modDNMsg = (ModifyDNMsg)msg;

      clEntry = createChangelogEntry(
          eclmsg.getServiceId(),
          eclmsg.getCookie().toString(),
          DN.decode(modDNMsg.getDn()),
          modDNMsg.getChangeNumber(),
          null,
          modDNMsg.getUniqueId(),
          null, // real time current entry
          null, // real time attrs names
          null, // hist entry attributes
          0,    // TODO:ECL G Good changelog draft compat. modDNMsg.getSeqnum()
          "modrdn");

      Attribute a = Attributes.create("newrdn", modDNMsg.getNewRDN());
      clEntry.addAttribute(a, null);

      Attribute b = Attributes.create("newsuperior", modDNMsg.getNewSuperior());
      clEntry.addAttribute(b, null);

      Attribute c = Attributes.create("deleteoldrdn",
          String.valueOf(modDNMsg.deleteOldRdn()));
      clEntry.addAttribute(c, null);

    }
    else if (msg instanceof DeleteMsg)
    {
      DeleteMsg delMsg = (DeleteMsg)msg;
      ArrayList<RawAttribute> rattributes = new ArrayList<RawAttribute>();
      String delAttrs = delMsgToLDIFString(rattributes);

      clEntry = createChangelogEntry(
          eclmsg.getServiceId(),
          eclmsg.getCookie().toString(),
          DN.decode(delMsg.getDn()),
          delMsg.getChangeNumber(),
          delAttrs,
          delMsg.getUniqueId(),
          null,
          null,
          null, //rattributes,
          0, // TODO:ECL G Good changelog draft compat. delMsg.getSeqnum()
         "delete");
    }
    return clEntry;
  }

  /**
   * Creates the root entry of the external changelog.
   * @return The root entry created.
   */
  private Entry createRootEntry()
  {
    HashMap<ObjectClass,String> oclasses =
      new LinkedHashMap<ObjectClass,String>(3);
    oclasses.putAll(eclObjectClasses);

    HashMap<AttributeType,List<Attribute>> userAttrs =
      new LinkedHashMap<AttributeType,List<Attribute>>();

    HashMap<AttributeType,List<Attribute>> operationalAttrs =
      new LinkedHashMap<AttributeType,List<Attribute>>();

    // Add to the root entry the replication state of each domain
    // TODO:ECL Put in ECL root entry, the ServerState for each domain
    AttributeType descType = DirectoryServer.getAttributeType("description");
    List<ReplicationDomain> supportedReplicationDomains
     = new ArrayList<ReplicationDomain>();

    for (ReplicationDomain domain : supportedReplicationDomains)
    {
      //    Crappy stuff to return the server state to the client
      LinkedList<Attribute> attrList = new LinkedList<Attribute>();
      attrList.add(Attributes.create("description",
          domain.getServiceID() + "/" + domain.getServerState().toString()));
      userAttrs.put(descType, attrList);
    }
    Entry e = new Entry(this.rootBaseDN, oclasses, userAttrs,
        operationalAttrs);
    return e;
  }

  /**
   * Create an ECL entry.
   * @param serviceID       The provided cookie value.
   * @param cookie          The provided cookie value.
   * @param targetDN        The provided targetDN.
   * @param changeNumber    The provided replication changeNumber.
   * @param clearLDIFchanges     The provided LDIF changes for ADD and MODIFY
   * @param targetUUID      The provided targetUUID.
   * @param entry           The provided related current entry.
   * @param targetAttrNames The provided list of attributes names that should
   *                        be read from the entry (real time values)
   * @param histEntryAttributes TODO:ECL Adress hist entry attributes
   * @param seqnum          The provided RCL int change number.
   * @param changetype      The provided change type (add, ...)
   * @return                The created ECL entry.
   * @throws DirectoryException
   *         When any error occurs.
   */
  public static Entry createChangelogEntry(
      String serviceID,
      String cookie,
      DN targetDN,
      ChangeNumber changeNumber,
      String clearLDIFchanges,
      String targetUUID,
      Entry entry,
      List<String> targetAttrNames,
      List<RawAttribute> histEntryAttributes,
      int seqnum,
      String changetype)
  throws DirectoryException
  {
    String dnString = "cn="+ changeNumber +"," +
      serviceID + "," +
      ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT;

    HashMap<ObjectClass,String> oClasses =
      new LinkedHashMap<ObjectClass,String>(3);
    oClasses.putAll(eclObjectClasses);

    ObjectClass extensibleObjectOC =
      DirectoryServer.getObjectClass(OC_EXTENSIBLE_OBJECT_LC, true);
    oClasses.put(extensibleObjectOC, OC_EXTENSIBLE_OBJECT);

    HashMap<AttributeType,List<Attribute>> uAttrs =
      new LinkedHashMap<AttributeType,List<Attribute>>();

    HashMap<AttributeType,List<Attribute>> operationalAttrs =
      new LinkedHashMap<AttributeType,List<Attribute>>();

    ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);

    // TODO:ECL G Good changelog compat.
    if (seqnum>0)
    {
      Attribute a = Attributes.create("changeNumber", String.valueOf(seqnum));
      attrList = new ArrayList<Attribute>(1);
      attrList.add(a);
      uAttrs.put(a.getAttributeType(), attrList);
    }

    //
    Attribute a = Attributes.create("replicationCSN", changeNumber.toString());
    attrList = new ArrayList<Attribute>(1);
    attrList.add(a);
    uAttrs.put(a.getAttributeType(), attrList);

    //
    SimpleDateFormat dateFormat;
    dateFormat = new SimpleDateFormat(DATE_FORMAT_GMT_TIME);
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC")); // ??
    a = Attributes.create(
        DirectoryServer.getAttributeType("changeTime", true),
        dateFormat.format(new Date(changeNumber.getTime())));
    attrList = new ArrayList<Attribute>(1);
    attrList.add(a);
    uAttrs.put(a.getAttributeType(), attrList);

    /* Change time in a friendly format
    Date date = new Date(changeNumber.getTime());
    a = Attributes.create("clearChangeTime", date.toString());
    attrList = new ArrayList<Attribute>(1);
    attrList.add(a);
    uAttrs.put(a.getAttributeType(), attrList);
    */

    //
    a = Attributes.create("changeType", changetype);
    attrList = new ArrayList<Attribute>(1);
    attrList.add(a);
    uAttrs.put(a.getAttributeType(), attrList);

    //
    a = Attributes.create(
        DirectoryServer.getAttributeType("targetdn", true),
        targetDN.toNormalizedString());
    attrList = new ArrayList<Attribute>(1);
    attrList.add(a);
    uAttrs.put(a.getAttributeType(), attrList);

    //
    a = Attributes.create("replicaIdentifier",
        Short.toString(changeNumber.getServerId()));
    attrList = new ArrayList<Attribute>(1);
    attrList.add(a);
    uAttrs.put(a.getAttributeType(), attrList);

    if (clearLDIFchanges != null)
    {
      if (changetype.equalsIgnoreCase("delete"))
      {
        a = Attributes.create("clearDeletedEntryAttrs", clearLDIFchanges);
      }
      attrList = new ArrayList<Attribute>(1);
      attrList.add(a);
      operationalAttrs.put(a.getAttributeType(), attrList);

      if (changetype.equalsIgnoreCase("delete"))
      {
        a = Attributes.create("deletedentryattrs",
            clearLDIFchanges + "\n"); // force base64
      }
      else
      {
        AttributeType at = DirectoryServer.getAttributeType("changes");
        a = Attributes.create(at, clearLDIFchanges + "\n"); // force base64
      }
      attrList = new ArrayList<Attribute>(1);
      attrList.add(a);
      uAttrs.put(a.getAttributeType(), attrList);
    }

    a = Attributes.create("targetentryuuid", targetUUID);
    attrList = new ArrayList<Attribute>(1);
    attrList.add(a);
    operationalAttrs.put(a.getAttributeType(), attrList);

    a = Attributes.create("cookie", cookie);
    attrList = new ArrayList<Attribute>(1);
    attrList.add(a);
    operationalAttrs.put(a.getAttributeType(), attrList);

    // entryAttribute version
    /*
    if (targetAttrNames != null)
    {
      String sEntryAttrs = null;
      for (String attrName : targetAttrNames)
      {
        List<Attribute> attrs = entry.getAttribute(attrName);
        for (Attribute attr : attrs)
        {
          if (sEntryAttrs==null)
            sEntryAttrs="";
          else
            sEntryAttrs+=";";
          sEntryAttrs += attr.toString();
        }
      }
      if (sEntryAttrs!=null)
      {
        a = Attributes.create("entryAttributes", sEntryAttrs);
        attrList = new ArrayList<Attribute>(1);
        attrList.add(a);
        uAttrs.put(a.getAttributeType(), attrList);
      }
    }
    */

    /*
    if (targetAttrNames != null)
    {
      for (String attrName : targetAttrNames)
      {
        String newName = "target"+attrName;
        List<Attribute> attrs = entry.getAttribute(attrName);
        for (Attribute aa : attrs)
        {
          AttributeBuilder builder = new AttributeBuilder(
            DirectoryServer.getDefaultAttributeType(newName));
          builder.setOptions(aa.getOptions());
          builder.addAll(aa);
          attrList = new ArrayList<Attribute>(1);
          attrList.add(builder.toAttribute());
          uAttrs.put(aa.getAttributeType(), attrList);
        }
      }
    }
    */
    /* TODO: Implement entry attributes historical values
    if (histEntryAttributes != null)
    {
      for (RawAttribute rea : histEntryAttributes)
      {
        // uAttrs.put(ea.getAttributeType(), null);
        // FIXME: ERRONEOUS TYPING !!!!!!!!!!!!!
        try
        {
          String newName = "target"+rea.getAttributeType();
          AttributeType nat=DirectoryServer.getDefaultAttributeType(newName);
          rea.setAttributeType(newName);
          attrList = new ArrayList<Attribute>(1);
          attrList.add(rea.toAttribute());
          uAttrs.put(nat, attrList);
        }
        catch(Exception e)
        {

        }
      }
    }
    */

    // at the end build the CL entry to be returned
    Entry cle = new Entry(
        DN.decode(dnString),
        eclObjectClasses,
        uAttrs,
        operationalAttrs);

    return cle;
  }

  private static String addMsgToLDIFString(AddMsg addMsg)
  {
    StringBuilder modTypeLine = new StringBuilder();
    // LinkedList<StringBuilder> ldifLines =
    //  new LinkedList<StringBuilder>();

    try
    {
      AddOperation addOperation = (AddOperation)addMsg.createOperation(
          InternalClientConnection.getRootConnection());

      Map<AttributeType,List<Attribute>> attributes =
        new HashMap<AttributeType,List<Attribute>>();

      for (RawAttribute a : addOperation.getRawAttributes())
      {
        Attribute attr = a.toAttribute();
        AttributeType attrType = attr.getAttributeType();
        List<Attribute> attrs = attributes.get(attrType);
        if (attrs == null)
        {
          attrs = new ArrayList<Attribute>(1);
          attrs.add(attr);
          attributes.put(attrType, attrs);
        }
        else
        {
          attrs.add(attr);
        }
      }
      for (List<Attribute> attrList : attributes.values())
      {
        for (Attribute a : attrList)
        {
          StringBuilder attrName = new StringBuilder(a.getName());
          for (String o : a.getOptions())
          {
            attrName.append(";");
            attrName.append(o);
          }
          for (AttributeValue av : a)
          {
            String stringValue = av.toString();

            modTypeLine.append(attrName);
            if (needsBase64Encoding(stringValue))
            {
              modTypeLine.append(":: ");
              modTypeLine.append(Base64.encode(av.getValue()));
            }
            else
            {
              modTypeLine.append(": ");
              modTypeLine.append(stringValue);
            }
            modTypeLine.append("\n");
          }
        }
      }
      return modTypeLine.toString();
    }
    catch(Exception e)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, e);
    }
    return null;
  }

  /**
   * q.
   * @param rattributes q.
   * @return
   */
  private static String delMsgToLDIFString(ArrayList<RawAttribute> rattributes)
  {
    StringBuilder modTypeLine = new StringBuilder();
    try
    {

      Map<AttributeType,List<Attribute>> attributes =
        new HashMap<AttributeType,List<Attribute>>();

      for (RawAttribute a : rattributes)
      {
        Attribute attr = a.toAttribute();
        AttributeType attrType = attr.getAttributeType();
        List<Attribute> attrs = attributes.get(attrType);
        if (attrs == null)
        {
          attrs = new ArrayList<Attribute>(1);
          attrs.add(attr);
          attributes.put(attrType, attrs);
        }
        else
        {
          attrs.add(attr);
        }
      }

      for (List<Attribute> attrList : attributes.values())
      {
        for (Attribute a : attrList)
        {
          StringBuilder attrName = new StringBuilder(a.getName());
          for (String o : a.getOptions())
          {
            attrName.append(";");
            attrName.append(o);
          }
          for (AttributeValue av : a)
          {
            // ??
            String stringValue = av.toString();
            modTypeLine.append(attrName);
            if (needsBase64Encoding(stringValue))
            {
              modTypeLine.append(":: ");
              modTypeLine.append(Base64.encode(av.getValue()));
            }
            else
            {
              modTypeLine.append(": ");
              modTypeLine.append(stringValue);
            }
            modTypeLine.append("\n");
          }
        }
      }
      return modTypeLine.toString();
    }
    catch(Exception e)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, e);
    }
    return null;
  }

  /**
   * Dumps a list of modifications into an LDIF string.
   * @param mods The provided list of modifications.
   * @return The LDIF string.
   */
  public static String modToLDIF(List<Modification> mods)
  {
    StringBuilder modTypeLine = new StringBuilder();
    Iterator<Modification> iterator = mods.iterator();
    while (iterator.hasNext())
    {
      Modification m = iterator.next();
      Attribute a = m.getAttribute();
      String attrName = a.getName();
      modTypeLine.append(m.getModificationType().getLDIFName());
      modTypeLine.append(": ");
      modTypeLine.append(attrName);
      modTypeLine.append("\n");

      Iterator<AttributeValue> iteratorValues = a.iterator();
      while (iteratorValues.hasNext())
      {
        AttributeValue av = iteratorValues.next();

        String stringValue = av.toString();

        modTypeLine.append(attrName);
        if (needsBase64Encoding(stringValue))
        {
          modTypeLine.append(":: ");
          modTypeLine.append(Base64.encode(av.getValue()));
        }
        else
        {
          modTypeLine.append(": ");
          modTypeLine.append(stringValue);
        }
        modTypeLine.append("\n");
      }

      modTypeLine.append("-");
      if (iterator.hasNext())
      {
        modTypeLine.append("\n");
      }
    }
    return modTypeLine.toString();
  }

  /**
   * {@inheritDoc}
   */
  public CancelResult cancel(CancelRequest cancelRequest)
  {
    if (eclSession!=null)
    {
      try
      {
        eclSession.close();
      }
      catch(Exception e){}
    }
    return super.cancel(cancelRequest);
  }
}

