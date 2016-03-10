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

import org.easymock.classextension.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class MediaPackageLockImplTest {

  private MediaPackageLockManagerImpl lockManager;
  private MediaPackageLockRequester requester1;
  private MediaPackageLockRequester requester2;
  private final String mediaPackageId = "123mp";


  /**
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() throws Exception {
    lockManager = new MediaPackageLockManagerImpl();
    requester1 = EasyMock.createNiceMock(MediaPackageLockRequester.class);
    requester2 = EasyMock.createNiceMock(MediaPackageLockRequester.class);
  }

  /**
   * @throws java.lang.Exception
   */
  @After
  public void tearDown() throws Exception {
    lockManager.unsetMediaPackageLockRequester(requester1);
    lockManager.unsetMediaPackageLockRequester(requester2);
  }

  @Test
  public void testMakeWaitLock() throws MediaPackageLockException {
    int lock1 = lockManager.lock(mediaPackageId, requester1);
    Assert.assertTrue("Received a positive lock ", lock1 > -1 );
    int lock2 = lockManager.lock(mediaPackageId, requester2);
    Assert.assertEquals("Received a wait lock ",  -1, lock2 );
  }

  @Test
  public void testReleaseLock() throws MediaPackageLockException {
    int lock1 = lockManager.lock(mediaPackageId, requester1);
    Assert.assertTrue("Received a positive lock ", lock1 > -1 );
    lockManager.release(mediaPackageId, lock1);
    int lock2 = lockManager.lock(mediaPackageId, requester2);
    Assert.assertTrue("Received a positive lock ",  lock2 > -1);
  }

  @Test
  public void testReleaseLockHolder() throws MediaPackageLockException {
    int lock1 = lockManager.lock(mediaPackageId, requester1);
    Assert.assertTrue("Received a positive lock ", lock1 > -1 );
    lockManager.unsetMediaPackageLockRequester(requester1);
    int lock2 = lockManager.lock(mediaPackageId, requester2);
    Assert.assertTrue("Received a positive lock ",  lock2 > -1);
  }

  @Test
  public void testQueuedLockRequester() throws MediaPackageLockException {
    MediaPackageLockRequesterMock mockRequester = new MediaPackageLockRequesterMock();
    int lock1 = lockManager.lock(mediaPackageId, requester1);
    Assert.assertTrue("Received a positive lock ", lock1 > -1 );
    int lock2 = lockManager.lock(mediaPackageId, mockRequester);
    Assert.assertEquals("Received a wait lock ",  -1, lock2 );
    lockManager.release(mediaPackageId, lock1);
    Assert.assertTrue("Updated with a positive lock ", mockRequester.getLock() > -1 );
  }

  /**
   * Request Mock class
   */
  public class MediaPackageLockRequesterMock implements MediaPackageLockRequester {
    private int lock;
    private String mpId;
    public MediaPackageLockRequesterMock() {
    }
    @Override
    public boolean lockGranted(String mediaPackageId, int lockNumber) {
      this.mpId = mediaPackageId;
      this.lock = lockNumber;
      return true;
    }
    protected int getLock() {
      return lock;
    }
    protected void setLock(int lock) {
      this.lock = lock;
    }
    protected String getMpId() {
      return mpId;
    }
    protected void setMpId(String mpId) {
      this.mpId = mpId;
    }
  }

}
