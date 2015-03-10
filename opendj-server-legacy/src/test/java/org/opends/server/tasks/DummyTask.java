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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.tasks;



import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.types.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;



/**
 * This class provides an implementation of a Directory Server task always
 * completes successfully.  It is intended only for testing purposes.
 */
public class DummyTask
       extends Task
{
  // The length of time that the task should sleep before completing.
  private long sleepTime;

  // The task state to use when interrupting the task.  This will be
  // null unless the task gets interrupted.
  private volatile TaskState interruptedState;

  /**
   * {@inheritDoc}
   */
  @Override
  public LocalizableMessage getDisplayName() {
    return LocalizableMessage.raw("Dummy");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void initializeTask()
         throws DirectoryException
  {
    sleepTime = 0;
    interruptedState = null;

    Entry taskEntry = getTaskEntry();
    if (taskEntry != null)
    {
      List<Attribute> attrList =
           taskEntry.getAttribute("ds-task-dummy-sleep-time");
      if (attrList != null)
      {
        for (Attribute a : attrList)
        {
          for (ByteString v : a)
          {
            sleepTime = Long.parseLong(v.toString());
          }
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  protected TaskState runTask()
  {
    long stopTime = System.currentTimeMillis() + sleepTime;
    while ((interruptedState == null) &&
           (System.currentTimeMillis() < stopTime))
    {
      try
      {
        Thread.sleep(10);
      } catch (Exception e) {}
    }

    if (interruptedState == null)
    {
      return TaskState.COMPLETED_SUCCESSFULLY;
    }
    else
    {
      return interruptedState;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isInterruptable()
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void interruptTask(TaskState taskState, LocalizableMessage interruptMessage)
  {
    interruptedState = taskState;
    setTaskInterruptState(taskState);
  }
}

