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
package org.opencastproject.mediapackage.lock;

import org.opencastproject.mediapackage.lock.api.MediaPackageLockException;
import org.opencastproject.mediapackage.lock.api.MediaPackageLockManager;
import org.opencastproject.mediapackage.lock.api.MediaPackageLockRequester;
import org.opencastproject.util.Log;

import org.osgi.service.component.ComponentContext;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * This class allows serialization of custom services when these services make changes to media packages, their files,
 * and the solr indices. Services should first get a lock on a media package before proceeding so that we don't have two
 * services updating media package concurrently, which may cause inconsistencies or exceptions. The matterhorn services
 * (workflow, etc) do not use this! Current limitation: only serializes services that run on the same server i.e. the
 * admin server.
 *
 * @author rsantos
 *
 */
public class MediaPackageLockManagerImpl implements MediaPackageLockManager {

  // Keeps track of all locks on media packages and which services are waiting for the
  // current lock to be released
  private static final Map<String, MediaPackageLockControl> locks = new HashMap<String, MediaPackageLockControl>();

  /**
   * Logger
   */
  private static final Log logger = new Log(LoggerFactory.getLogger(MediaPackageLockManagerImpl.class));

  /**
   * OSGi activate callback
   *
   * @param context
   */
  protected void activate(ComponentContext context) {
    logger.debug("Activating...");
  }

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
  @Override
  public synchronized int lock(String mediaPackageId, MediaPackageLockRequester requester)
          throws MediaPackageLockException {
    if (mediaPackageId == null) {
      throw new MediaPackageLockException("Media package id is null");
    }
    if (requester == null) {
      throw new MediaPackageLockException("Lock requester service is null");
    }

    MediaPackageLockControl lockControl = locks.get(mediaPackageId);
    if (lockControl == null) {
      lockControl = new MediaPackageLockControl(mediaPackageId);
      locks.put(mediaPackageId, lockControl);
    }

    // Return the lock number or -1 if not available
    return lockControl.getLock(requester);
  }

  /**
   * Allows requester to verify that manager still has the requester's request and keep it's place in the queue.
   * Otherwise the requester re-requests the lock. Used by persisted requester's after restart (i.e. Metasynch service).
   *
   * @param mediaPackageId
   * @param requester
   * @return
   * @throws MediaPackageLockException
   */
  @Override
  public Boolean isInQueue(String mediaPackageId, MediaPackageLockRequester requester) throws MediaPackageLockException {
    Boolean isInQueue = false;
    // Verify requester is in queue for the specified mpId
    MediaPackageLockControl lockControl = locks.get(mediaPackageId);
    if (lockControl != null) {
      isInQueue = lockControl.isInQueue(requester);
    }
    return isInQueue;
  }

  /**
   * Releases lock on the specified media package.
   *
   * @param mediaPackageId
   *          the media package id
   * @param lockNumber
   *          the lock number
   * @throws MediaPackageLockException
   *           if the lock number passed does not match the current lock or invalid arguments passed
   */
  @Override
  public synchronized void release(String mediaPackageId, int lockNumber) throws MediaPackageLockException {
    if (mediaPackageId == null) {
      throw new MediaPackageLockException("Media package id is null");
    }
    MediaPackageLockControl lockControl = locks.get(mediaPackageId);
    if (lockControl == null) {
      return; // Nothing to do
    }
    // Release lock
    lockControl.releaseLock(lockNumber);
    // Grant lock to next requester waiting in the queue
    grantLockToNextInQueue(lockControl);
  }

  private void grantLockToNextInQueue(MediaPackageLockControl lockControl) {
    boolean lockAccepted = false;
    while (!lockAccepted) {
      // Remove next from queue
      MediaPackageLockRequester nextInQueue = lockControl.removeNextFromQueue();
      if (nextInQueue != null) {
        lockControl.getLock(nextInQueue);
        lockAccepted = nextInQueue.lockGranted(lockControl.getMediaPackageId(), lockControl.getCurrentLockNumber());
      } else {
        // Nothing queued. remove this lock control from our map
        locks.remove(lockControl.getMediaPackageId());
        break;
      }
    }
  }

  @Override
  public synchronized void unsetMediaPackageLockRequester(MediaPackageLockRequester requester) {
    // Release granted locks and remove from queues
    for (String mpId : locks.keySet()) {
      MediaPackageLockControl lockControl = locks.get(mpId);
      try {
        boolean hadLockOnThisMp = lockControl.forceRemoveLockRequester(requester);
        if (hadLockOnThisMp) {
          grantLockToNextInQueue(lockControl);
        }
      } catch (MediaPackageLockException e) {
        // Ignore because we are removing the requester
        logger.debug("Exception " + e.getMessage());
      }
    }
  }
}
