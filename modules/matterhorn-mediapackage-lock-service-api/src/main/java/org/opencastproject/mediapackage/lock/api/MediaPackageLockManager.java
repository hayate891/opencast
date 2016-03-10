/**
 *  Copyright 2009, 2010 The Regents of the University of California
 *  Licensed under the Educational Community License, Version 2.0
 *  (the "License"); you may not use this file except in compliance
 *  with the License. You may obtain a copy of the License at
 *lock
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
 * This interface describes the serialization of custom services when these services make changes to media packages, their files,
 * and the solr indices. Services should first get a lock on a media package before proceeding so that we don't have two
 * services updating media package concurrently, which may cause inconsistencies or exceptions. The matterhorn services
 * (workflow, etc) do not use this! Current limitation: only serializes services that run on the same server i.e. the
 * admin server.
 *
 */
public interface MediaPackageLockManager {

  /**
   * Requests a lock to the specified media package by the requester passed.
   *
   * @param mediaPackageId
   *          media package id
   * @param requester
   *          requester service
   * @return lock number if granted, -1 if queued
   * @throws MediaPackageLockException
   *           if invalid arguments are passed
   */
  public int lock(String mediaPackageId, MediaPackageLockRequester requester)
          throws MediaPackageLockException;

  /**
   * Allows requester to verify that manager still has the requester's request and keep it's place in the queue.
   * Otherwise the requester re-requests the lock. Used by persisted requester's after restart (i.e. Metasynch service).
   *
   * @param mediaPackageId
   * @param requester
   * @return
   * @throws MediaPackageLockException
   */
  public Boolean isInQueue(String mediaPackageId, MediaPackageLockRequester requester)
          throws MediaPackageLockException;

  /**
   * Releases lock on the specified media package.
   *
   * @param mediaPackageId
   *          the media package id
   * @param lockNumber
   *          the lock number
   * @throws MediaPackageLockException
   *           if the lock number passed does not match the current lock, invalid arguments passed
   */
  public void release(String mediaPackageId, int lockNumber)
          throws MediaPackageLockException;

  /**
   * Used to unregister MediaPackageLockRequester services from the manager.
   * This gives the manager the opportunity to free up locks held by unregistering requesters.
   *
   * @param requester that is unregistering
   */
  public void unsetMediaPackageLockRequester(MediaPackageLockRequester requester);


}
