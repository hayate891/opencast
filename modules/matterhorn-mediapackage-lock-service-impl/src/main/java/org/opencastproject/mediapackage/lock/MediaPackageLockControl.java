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
package org.opencastproject.mediapackage.lock;

import org.opencastproject.mediapackage.lock.api.MediaPackageLockException;
import org.opencastproject.mediapackage.lock.api.MediaPackageLockRequester;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MediaPackageLockControl {

  private static int lockCounter = 0;

  private final String mediaPackageId;
  private MediaPackageLockRequester currentLockOwner;
  private int currentLockNumber;
  // Services waiting for the media package lock
  private final List<MediaPackageLockRequester> queue;

  public MediaPackageLockControl(String mediaPackageId) {
    this.mediaPackageId = mediaPackageId;
    this.currentLockNumber = -1;
    this.queue = new ArrayList<MediaPackageLockRequester>();
  }

  public MediaPackageLockRequester getCurrentLockOwner() {
    return currentLockOwner;
  }

  public int getCurrentLockNumber() {
    return currentLockNumber;
  }

  public String getMediaPackageId() {
    return mediaPackageId;
  }

  /**
   * Gives the media package lock to the requester passed if available.
   *
   * @param requester
   *          service requesting the lock
   * @return the lock number if granted, -1 otherwise
   */
  public int getLock(MediaPackageLockRequester requester) {
    if (this.currentLockNumber == -1) {
      this.currentLockOwner = requester;
      this.currentLockNumber = ++lockCounter;
      return this.currentLockNumber;
    } else {
      // Add to queue
      this.queue.add(requester);
      return -1;
    }
  }

  /**
   * Releases the current lock. Make sure the lock number passed matches the current lock number.
   *
   * @param lockNumber
   * @throws MediaPackageLockException
   *           if the lock number passed does not match the current lock
   */
  public void releaseLock(int lockNumber) throws MediaPackageLockException {
    if (this.currentLockNumber == -1) {
      return; // Nothing to do
    }
    if (this.currentLockNumber != lockNumber) {
      // Can't release, not same requester
      throw new MediaPackageLockException(String.format(
              "Cannot release lock for media package {}. Invalid lock number: {}", mediaPackageId, lockNumber));
    }
    this.currentLockNumber = -1;
    this.currentLockOwner = null;
  }

  public MediaPackageLockRequester removeNextFromQueue() {
    if (queue.size() > 0) {
      return queue.remove(0);
    }
    return null;
  }

  /**
   * Removes the requester from the queue and releases the current lock if the requester currently has it.
   *
   * @param requester
   * @return true if requester had the current lock, false otherwise
   * @throws MediaPackageLockException
   */
  public boolean forceRemoveLockRequester(MediaPackageLockRequester requester) throws MediaPackageLockException {
    boolean hadCurrentLock = false;
    // Remove from queue
    for (Iterator<MediaPackageLockRequester> it = queue.iterator(); it.hasNext();) {
      MediaPackageLockRequester r = it.next();
      if (r == requester) {
        it.remove();
      }
    }
    // Remove lock if it has the current lock
    if (this.currentLockOwner == requester) {
      releaseLock(this.currentLockNumber);
      hadCurrentLock = true;
    }
    return hadCurrentLock;
  }

  /**
   * Confirms if a requester is already in queue for a lock on this MpId
   *
   * @param requester
   * @return true, if in queue, false if not in queue
   */
  public boolean isInQueue(MediaPackageLockRequester requester) {
   return this.queue.contains(requester);
  }

}
