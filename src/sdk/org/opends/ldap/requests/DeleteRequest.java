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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.ldap.requests;



import org.opends.ldap.controls.Control;
import org.opends.ldap.controls.SubtreeDeleteControl;



/**
 * The Delete operation allows a client to request the
 * removal of an entry from the Directory.
 * <p>
 * Only leaf entries (those with no subordinate entries) can be deleted
 * with this operation. However, addition of the
 * {@link SubtreeDeleteControl} permits whole sub-trees to be deleted
 * using a single Delete request.
 */
public interface DeleteRequest extends Request<DeleteRequest>
{

  /**
   * {@inheritDoc}
   */
  DeleteRequest addControl(Control control)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  DeleteRequest clearControls() throws UnsupportedOperationException;



  /**
   * {@inheritDoc}
   */
  Control getControl(String oid) throws NullPointerException;



  /**
   * {@inheritDoc}
   */
  Iterable<Control> getControls();



  /**
   * {@inheritDoc}
   */
  boolean hasControls();



  /**
   * {@inheritDoc}
   */
  Control removeControl(String oid)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  String toString();



  /**
   * {@inheritDoc}
   */
  StringBuilder toString(StringBuilder builder)
      throws NullPointerException;



  /**
   * Returns the name of the entry to be deleted. The server shall not
   * dereference any aliases in locating the entry to be deleted.
   *
   * @return The name of the entry to be deleted.
   */
  String getDN();



  /**
   * Sets the name of the entry to be deleted. The server shall not
   * dereference any aliases in locating the entry to be deleted.
   *
   * @param dn
   *          The name of the entry to be deleted.
   * @return This delete request.
   * @throws UnsupportedOperationException
   *           If this delete request does not permit the DN to be set.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  DeleteRequest setDN(String dn) throws UnsupportedOperationException,
      NullPointerException;

}