/**
 *  Copyright 2009, 2010 The Regents of the University of California
 *  Licensed under the Educational Community License, Version 2.0
 *  (the "License"); you may not use this file except in compliance
 *  with the License. You may obtain a copy of the License at
 *
 *  http://www.osedu.org/licenses/ECL-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an "AS IS"
 *  BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 *  or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 *
 */
package org.opencastproject.mediapackage.lock.api;

/**
 * Classes that modify metadata outside a workflow, implement this interface and ingest a MediaPackageLockManager.
 * The requester registers interest in a lock to the manager, the manager calls this method to grant the lock.
 */
public interface MediaPackageLockRequester {
  /**
   * Notifies listener that lock to the media package has been granted.
   *
   * @param mediaPackageId
   *          the media package id
   * @param lockNumber
   *          the lock id
   * @return true, if lock was taken; false, if lock not needed anymore
   */
  public boolean lockGranted(String mediaPackageId, int lockNumber);
}
