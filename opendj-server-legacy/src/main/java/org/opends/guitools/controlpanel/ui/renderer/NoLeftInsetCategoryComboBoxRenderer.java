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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui.renderer;

import java.awt.Component;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;

import org.opends.guitools.controlpanel.datamodel.CategorizedComboBoxElement;
/**
 *  This class is used simply to avoid an inset on the left for the
 *  elements of the combo box.
 *  Since this item is a CategorizedComboBoxElement of type
 *  CategorizedComboBoxElement.Type.REGULAR, it has by default an inset on
 *  the left.
 */
public class NoLeftInsetCategoryComboBoxRenderer extends CustomListCellRenderer
{
  /**
   * The constructor.
   * @param combo the combo box to be rendered.
   */
  public NoLeftInsetCategoryComboBoxRenderer(JComboBox combo)
  {
    super(combo);
  }

  /** {@inheritDoc} */
  public Component getListCellRendererComponent(JList list, Object value,
      int index, boolean isSelected, boolean cellHasFocus)
  {
    Component comp = super.getListCellRendererComponent(list, value, index,
        isSelected, cellHasFocus);
    if (value instanceof CategorizedComboBoxElement)
    {
      CategorizedComboBoxElement element = (CategorizedComboBoxElement)value;
      String name = getStringValue(element);
      ((JLabel)comp).setText(name);
    }
    comp.setFont(defaultFont);
    return comp;
  }
}
