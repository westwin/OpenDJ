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
import org.opends.server.types.ByteString;



/**
 * The Compare operation allows a client to compare
 * an assertion value with the values of a particular attribute in a
 * particular entry in the Directory.
 * <p>
 * Note that some directory systems may establish access controls that
 * permit the values of certain attributes (such as {@code userPassword}
 * ) to be compared but not interrogated by other means.
 */
public interface CompareRequest extends Request<CompareRequest>
{

  /**
   * {@inheritDoc}
   */
  CompareRequest addControl(Control control)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  CompareRequest clearControls() throws UnsupportedOperationException;



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
   * Returns the attribute value assertion to be compared.
   *
   * @return The attribute value assertion to be compared.
   */
  ByteString getAssertionValue();



  /**
   * Returns the attribute value assertion to be compared decoded as a
   * UTF-8 string.
   *
   * @return The attribute value assertion to be compared decoded as a
   *         UTF-8 string.
   */
  String getAssertionValueAsString();



  /**
   * Returns the name of the attribute to be compared.
   *
   * @return The name of the attribute to be compared.
   */
  String getAttributeDescription();



  /**
   * Returns the name of the entry to be compared. The server shall not
   * dereference any aliases in locating the entry to be compared.
   *
   * @return The name of the entry to be compared.
   */
  String getDN();



  /**
   * Sets the attribute value assertion to be compared.
   *
   * @param ava
   *          The attribute value assertion to be compared.
   * @return This compare request.
   * @throws UnsupportedOperationException
   *           If this compare request does not permit the attribute
   *           value assertion to be set.
   * @throws NullPointerException
   *           If {@code ava} was {@code null}.
   */
  CompareRequest setAssertionValue(ByteString ava)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Sets the attribute value assertion to be compared.
   *
   * @param ava
   *          The attribute value assertion to be compared.
   * @return This compare request.
   * @throws UnsupportedOperationException
   *           If this compare request does not permit the attribute
   *           value assertion to be set.
   * @throws NullPointerException
   *           If {@code ava} was {@code null}.
   */
  CompareRequest setAssertionValue(String ava)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Sets the name of the attribute to be compared.
   *
   * @param attributeDescription
   *          The name of the attribute to be compared.
   * @return This compare request.
   * @throws UnsupportedOperationException
   *           If this compare request does not permit the attribute
   *           description to be set.
   * @throws NullPointerException
   *           If {@code attributeDescription} was {@code null}.
   */
  CompareRequest setAttributeDescription(String attributeDescription)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Sets the name of the entry to be compared. The server shall not
   * dereference any aliases in locating the entry to be compared.
   *
   * @param dn
   *          The name of the entry to be compared.
   * @return This compare request.
   * @throws UnsupportedOperationException
   *           If this compare request does not permit the DN to be set.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  CompareRequest setDN(String dn) throws UnsupportedOperationException,
      NullPointerException;

}