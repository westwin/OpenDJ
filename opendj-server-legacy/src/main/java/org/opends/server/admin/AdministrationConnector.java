/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.admin;

import static org.opends.messages.AdminMessages.*;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.naming.ldap.Rdn;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.AddressMask;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.meta.LDAPConnectionHandlerCfgDefn.SSLClientAuthPolicy;
import org.opends.server.admin.std.server.AdministrationConnectorCfg;
import org.opends.server.admin.std.server.ConnectionHandlerCfg;
import org.opends.server.admin.std.server.FileBasedKeyManagerProviderCfg;
import org.opends.server.admin.std.server.FileBasedTrustManagerProviderCfg;
import org.opends.server.admin.std.server.KeyManagerProviderCfg;
import org.opends.server.admin.std.server.LDAPConnectionHandlerCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.std.server.TrustManagerProviderCfg;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.core.SynchronousStrategy;
import org.opends.server.protocols.ldap.LDAPConnectionHandler;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.FilePermission;
import org.opends.server.types.InitializationException;
import org.opends.server.util.CertificateManager;
import org.opends.server.util.Platform.KeyType;
import org.opends.server.util.SetupUtils;

/**
 * This class is a wrapper on top of LDAPConnectionHandler to manage
 * the administration connector, which is an LDAPConnectionHandler
 * with specific (limited) configuration properties.
 */
public final class AdministrationConnector implements
    ConfigurationChangeListener<AdministrationConnectorCfg>
{

  /** Default Administration Connector port. */
  public static final int DEFAULT_ADMINISTRATION_CONNECTOR_PORT = 4444;
  /** Validity (in days) of the generated certificate. */
  public static final int ADMIN_CERT_VALIDITY = 20 * 365;

  /** Friendly name of the administration connector. */
  private static final String FRIENDLY_NAME = "Administration Connector";
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private LDAPConnectionHandler adminConnectionHandler;
  private AdministrationConnectorCfg config;

  /** Predefined values for Administration Connector configuration. */
  private static final String ADMIN_CLASS_NAME =
    "org.opends.server.protocols.ldap.LDAPConnectionHandler";

  private static final boolean ADMIN_ALLOW_LDAP_V2 = false;
  private static final boolean ADMIN_ALLOW_START_TLS = false;

  private static final SortedSet<AddressMask> ADMIN_ALLOWED_CLIENT = new TreeSet<>();
  private static final SortedSet<AddressMask> ADMIN_DENIED_CLIENT = new TreeSet<>();

  private static final boolean ADMIN_ENABLED = true;
  private static final boolean ADMIN_KEEP_STATS = true;
  private static final boolean ADMIN_USE_SSL = true;

  private static final int ADMIN_ACCEPT_BACKLOG = 128;
  private static final boolean ADMIN_ALLOW_TCP_REUSE_ADDRESS = true;

  /** 2mn. */
  private static final long ADMIN_MAX_BLOCKED_WRITE_TIME_LIMIT = 120000;
  /** 5 Mb. */
  private static final int ADMIN_MAX_REQUEST_SIZE = 5000000;
  private static final int ADMIN_WRITE_BUFFER_SIZE = 4096;
  private static final int ADMIN_NUM_REQUEST_HANDLERS = 1;
  private static final boolean ADMIN_SEND_REJECTION_NOTICE = true;
  private static final boolean ADMIN_USE_TCP_KEEP_ALIVE = true;
  private static final boolean ADMIN_USE_TCP_NO_DELAY = true;
  private static final SSLClientAuthPolicy ADMIN_SSL_CLIENT_AUTH_POLICY =
    SSLClientAuthPolicy.DISABLED;

  private final ServerContext serverContext;

  /**
   * Initializes this administration connector provider based on the
   * information in the provided administration connector
   * configuration.
   *
   * @param configuration
   *          The connection handler configuration that contains the
   *          information to use to initialize this connection
   *          handler.
   * @throws ConfigException
   *           If an unrecoverable problem arises in the process of
   *           performing the initialization as a result of the server
   *           configuration.
   * @throws InitializationException
   *           If a problem occurs during initialization that is not
   *           related to the server configuration.
   */
  public void initializeAdministrationConnector(
      AdministrationConnectorCfg configuration) throws ConfigException,
      InitializationException
  {
    this.config = configuration;

    // Administration Connector uses the LDAP connection handler implementation
    adminConnectionHandler = new LDAPConnectionHandler(
        new SynchronousStrategy(), FRIENDLY_NAME);
    adminConnectionHandler.initializeConnectionHandler(serverContext, new LDAPConnectionCfgAdapter(config));
    adminConnectionHandler.setAdminConnectionHandler();

    // Register this as a change listener.
    config.addChangeListener(this);
  }


  /**
   * Creates an instance of the administration connector.
   *
   * @param serverContext
   *            The server context.
   **/
  public AdministrationConnector(ServerContext serverContext)
  {
    this.serverContext = serverContext;
  }

  /**
   * Retrieves the connection handler linked to this administration connector.
   *
   * @return The connection handler linked to this administration connector.
   */
  public LDAPConnectionHandler getConnectionHandler()
  {
    return adminConnectionHandler;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
      AdministrationConnectorCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    return adminConnectionHandler.isConfigurationAcceptable(new LDAPConnectionCfgAdapter(configuration),
        unacceptableReasons);
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
      AdministrationConnectorCfg configuration)
  {
    return adminConnectionHandler.applyConfigurationChange(new LDAPConnectionCfgAdapter(configuration));
  }



  /**
   * This private class implements a fake LDAP connection Handler configuration.
   * This allows to re-use the LDAPConnectionHandler as it is.
   */
  private static class LDAPConnectionCfgAdapter implements
      LDAPConnectionHandlerCfg
  {
    private final AdministrationConnectorCfg config;

    public LDAPConnectionCfgAdapter(AdministrationConnectorCfg config)
    {
      this.config = config;
    }

    /** {@inheritDoc} */
    @Override
    public Class<? extends LDAPConnectionHandlerCfg> configurationClass()
    {
      return LDAPConnectionHandlerCfg.class;
    }

    /** {@inheritDoc} */
    @Override
    public void addLDAPChangeListener(
        ConfigurationChangeListener<LDAPConnectionHandlerCfg> listener)
    {
      // do nothing. change listener already added.
    }

    /** {@inheritDoc} */
    @Override
    public void removeLDAPChangeListener(
        ConfigurationChangeListener<LDAPConnectionHandlerCfg> listener)
    {
      // do nothing. change listener already added.
    }

    /** {@inheritDoc} */
    @Override
    public int getAcceptBacklog()
    {
      return ADMIN_ACCEPT_BACKLOG;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAllowLDAPV2()
    {
      return ADMIN_ALLOW_LDAP_V2;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAllowStartTLS()
    {
      return ADMIN_ALLOW_START_TLS;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAllowTCPReuseAddress()
    {
      return ADMIN_ALLOW_TCP_REUSE_ADDRESS;
    }

    /** {@inheritDoc} */
    @Override
    public String getJavaClass()
    {
      return ADMIN_CLASS_NAME;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isKeepStats()
    {
      return ADMIN_KEEP_STATS;
    }

    /** {@inheritDoc} */
    @Override
    public String getKeyManagerProvider()
    {
      return config.getKeyManagerProvider();
    }

    /** {@inheritDoc} */
    @Override
    public DN getKeyManagerProviderDN()
    {
      return config.getKeyManagerProviderDN();
    }

    /** {@inheritDoc} */
    @Override
    public SortedSet<InetAddress> getListenAddress()
    {
      return config.getListenAddress();
    }

    /** {@inheritDoc} */
    @Override
    public int getListenPort()
    {
      return config.getListenPort();
    }

    /** {@inheritDoc} */
    @Override
    public long getMaxBlockedWriteTimeLimit()
    {
      return ADMIN_MAX_BLOCKED_WRITE_TIME_LIMIT;
    }

    /** {@inheritDoc} */
    @Override
    public long getMaxRequestSize()
    {
      return ADMIN_MAX_REQUEST_SIZE;
    }

    /** {@inheritDoc} */
    @Override
    public long getBufferSize()
    {
      return ADMIN_WRITE_BUFFER_SIZE;
    }

    /** {@inheritDoc} */
    @Override
    public Integer getNumRequestHandlers()
    {
      return ADMIN_NUM_REQUEST_HANDLERS;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isSendRejectionNotice()
    {
      return ADMIN_SEND_REJECTION_NOTICE;
    }

    /** {@inheritDoc} */
    @Override
    public SortedSet<String> getSSLCertNickname()
    {
      return config.getSSLCertNickname();
    }

    /** {@inheritDoc} */
    @Override
    public SortedSet<String> getSSLCipherSuite()
    {
      return config.getSSLCipherSuite();
    }

    /** {@inheritDoc} */
    @Override
    public SSLClientAuthPolicy getSSLClientAuthPolicy()
    {
      return ADMIN_SSL_CLIENT_AUTH_POLICY;
    }

    /** {@inheritDoc} */
    @Override
    public SortedSet<String> getSSLProtocol()
    {
      return config.getSSLProtocol();
    }

    /** {@inheritDoc} */
    @Override
    public String getTrustManagerProvider()
    {
      return config.getTrustManagerProvider();
    }

    /** {@inheritDoc} */
    @Override
    public DN getTrustManagerProviderDN()
    {
      return config.getTrustManagerProviderDN();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isUseSSL()
    {
      return ADMIN_USE_SSL;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isUseTCPKeepAlive()
    {
      return ADMIN_USE_TCP_KEEP_ALIVE;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isUseTCPNoDelay()
    {
      return ADMIN_USE_TCP_NO_DELAY;
    }

    /** {@inheritDoc} */
    @Override
    public void addChangeListener(
        ConfigurationChangeListener<ConnectionHandlerCfg> listener)
    {
      // do nothing. change listener already added.
    }

    /** {@inheritDoc} */
    @Override
    public void removeChangeListener(
        ConfigurationChangeListener<ConnectionHandlerCfg> listener)
    {
      // do nothing. change listener already added.
    }

    /** {@inheritDoc} */
    @Override
    public SortedSet<AddressMask> getAllowedClient()
    {
      return ADMIN_ALLOWED_CLIENT;
    }

    /** {@inheritDoc} */
    @Override
    public SortedSet<AddressMask> getDeniedClient()
    {
      return ADMIN_DENIED_CLIENT;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEnabled()
    {
      return ADMIN_ENABLED;
    }

    /** {@inheritDoc} */
    @Override
    public DN dn()
    {
      return config.dn();
    }
  }



  /**
   * Creates a self-signed JKS certificate if needed.
   *
   * @param serverContext
   *          The server context.
   * @throws InitializationException
   *           If an unexpected error occurred whilst trying to create the
   *           certificate.
   */
  public static void createSelfSignedCertificateIfNeeded(ServerContext serverContext)
      throws InitializationException
  {
    try
    {
      RootCfg root = ServerManagementContext.getInstance()
          .getRootConfiguration();
      AdministrationConnectorCfg config = root.getAdministrationConnector();

      // Check if certificate generation is needed
      final SortedSet<String> certAliases = config.getSSLCertNickname();
      KeyManagerProviderCfg keyMgrConfig = root.getKeyManagerProvider(config
          .getKeyManagerProvider());
      TrustManagerProviderCfg trustMgrConfig = root
          .getTrustManagerProvider(config.getTrustManagerProvider());

      if (hasDefaultConfigChanged(keyMgrConfig, trustMgrConfig))
      {
        // nothing to do
        return;
      }

      FileBasedKeyManagerProviderCfg fbKeyManagerConfig =
        (FileBasedKeyManagerProviderCfg) keyMgrConfig;
      String keystorePath = getFullPath(fbKeyManagerConfig.getKeyStoreFile());
      FileBasedTrustManagerProviderCfg fbTrustManagerConfig =
        (FileBasedTrustManagerProviderCfg) trustMgrConfig;
      String truststorePath = getFullPath(fbTrustManagerConfig
          .getTrustStoreFile());
      String pinFilePath = getFullPath(fbKeyManagerConfig.getKeyStorePinFile());

      // Check that either we do not have any file,
      // or we have the 3 required files (keystore, truststore, pin
      // file)
      boolean keystore = false;
      boolean truststore = false;
      boolean pinFile = false;
      int nbFiles = 0;
      if (new File(keystorePath).exists())
      {
        keystore = true;
        nbFiles++;
      }
      if (new File(truststorePath).exists())
      {
        truststore = true;
        nbFiles++;
      }
      if (new File(pinFilePath).exists())
      {
        pinFile = true;
        nbFiles++;
      }
      if (nbFiles == 3)
      {
        // nothing to do
        return;
      }
      if (nbFiles != 0)
      {
        // 1 or 2 files are missing : error
        String err = "";
        if (!keystore)
        {
          err += keystorePath + " ";
        }
        if (!truststore)
        {
          err += truststorePath + " ";
        }
        if (!pinFile)
        {
          err += pinFilePath + " ";
        }
        LocalizableMessage message = ERR_ADMIN_CERTIFICATE_GENERATION_MISSING_FILES
            .get(err);
        logger.error(message);
        throw new InitializationException(message);
      }

      // Generate a password
      String pwd = new String(SetupUtils.createSelfSignedCertificatePwd());

      // Generate a self-signed certificate
      CertificateManager certManager = new CertificateManager(
          getFullPath(fbKeyManagerConfig.getKeyStoreFile()), fbKeyManagerConfig
              .getKeyStoreType(), pwd);
      String hostName =
        SetupUtils.getHostNameForCertificate(DirectoryServer.getServerRoot());

      // Temporary exported certificate's file
      String tempCertPath = getFullPath("config" + File.separator
          + "admin-cert.txt");

      // Create a new trust store and import the server certificate
      // into it
      CertificateManager trustManager = new CertificateManager(truststorePath,
          CertificateManager.KEY_STORE_TYPE_JKS, pwd);
      for (String certAlias : certAliases)
      {
        final KeyType keyType = KeyType.getTypeOrDefault(certAlias);
        final String subjectDN =
            "cn=" + Rdn.escapeValue(hostName) + ",O=" + FRIENDLY_NAME + " " + keyType + " Self-Signed Certificate";
        certManager.generateSelfSignedCertificate(keyType, certAlias, subjectDN, ADMIN_CERT_VALIDITY);

        SetupUtils.exportCertificate(certManager, certAlias, tempCertPath);

        // import the server certificate into it
        final File tempCertFile = new File(tempCertPath);
        trustManager.addCertificate(certAlias, tempCertFile);
        tempCertFile.delete();
      }

      // Generate a password file
      if (!new File(pinFilePath).exists())
      {
        try (final FileWriter file = new FileWriter(pinFilePath);
             final PrintWriter out = new PrintWriter(file))
        {
          out.println(pwd);
          out.flush();
        }
      }

      // Change the password file permission if possible
      try
      {
        if (!FilePermission.setPermissions(new File(pinFilePath),
            new FilePermission(0600)))
        {
          // Log a warning that the permissions were not set.
          logger.warn(WARN_ADMIN_SET_PERMISSIONS_FAILED, pinFilePath);
        }
      }
      catch (DirectoryException e)
      {
        // Log a warning that the permissions were not set.
        logger.warn(WARN_ADMIN_SET_PERMISSIONS_FAILED, pinFilePath);
      }
    }
    catch (InitializationException e)
    {
      throw e;
    }
    catch (Exception e)
    {
      throw new InitializationException(ERR_ADMIN_CERTIFICATE_GENERATION.get(e.getMessage()), e);
    }
  }

  /**
   * Check if default configuration for administrator's key manager and trust
   * manager provider has changed.
   *
   * @param keyConfig
   *          key manager provider configuration
   * @param trustConfig
   *          trust manager provider configuration
   * @return true if default configuration has changed, false otherwise
   */
  private static boolean hasDefaultConfigChanged(
      KeyManagerProviderCfg keyConfig, TrustManagerProviderCfg trustConfig)
  {
    if (keyConfig.isEnabled()
        && keyConfig instanceof FileBasedKeyManagerProviderCfg
        && trustConfig.isEnabled()
        && trustConfig instanceof FileBasedTrustManagerProviderCfg)
    {
      FileBasedKeyManagerProviderCfg fileKeyConfig =
          (FileBasedKeyManagerProviderCfg) keyConfig;
      boolean pinIsProvidedByFileOnly =
          fileKeyConfig.getKeyStorePinFile() != null
              && fileKeyConfig.getKeyStorePin() == null
              && fileKeyConfig.getKeyStorePinEnvironmentVariable() == null
              && fileKeyConfig.getKeyStorePinProperty() == null;
      return !pinIsProvidedByFileOnly;
    }
    return true;
  }

  private static String getFullPath(String path)
  {
    File file = new File(path);
    if (!file.isAbsolute())
    {
      path = DirectoryServer.getInstanceRoot() + File.separator + path;
    }

    return path;
  }
}
