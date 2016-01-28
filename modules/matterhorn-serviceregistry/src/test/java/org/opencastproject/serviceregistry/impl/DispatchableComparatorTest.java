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
package org.opencastproject.serviceregistry.impl;

import static org.junit.Assert.assertEquals;

import org.opencastproject.job.api.Job;
import org.opencastproject.job.api.Job.Status;
import org.opencastproject.job.api.JobContext;
import org.opencastproject.serviceregistry.impl.ServiceRegistryJpaImpl.DispatchableComparator;

import org.junit.Test;

import java.net.URI;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

public class DispatchableComparatorTest {

  @Test
  public void testDispatchableComparator() {
    Comparator<Job> dispatchableComparator = new DispatchableComparator();

    // A date and time
    Calendar dt = new GregorianCalendar(2016, 1, 1, 1, 0, 0);
    // One hour later
    Calendar dtPlusOneHour = new GregorianCalendar();
    dtPlusOneHour.setTimeInMillis(dt.getTimeInMillis());
    dtPlusOneHour.add(Calendar.HOUR, 1);

    // Test equal: same job type, same status, same date
    Job j1 = new TestJob(1, "non-wf", Status.RESTART, dt.getTime());
    Job j2 = new TestJob(2, "non-wf", Status.RESTART, dt.getTime());
    assertEquals(0, dispatchableComparator.compare(j1, j2));

    // Test first less than second
    // Another status
    Job j3 = new TestJob(3, "non-wf", Status.QUEUED, dt.getTime());
    assertEquals(-1, dispatchableComparator.compare(j1, j3));
    // Another job type
    Job j4 = new TestJob(4, ServiceRegistryJpaImpl.TYPE_WORKFLOW, Status.RESTART, dt.getTime());
    assertEquals(-1, dispatchableComparator.compare(j1, j4));
    // Another date
    Job j5 = new TestJob(5, "non-wf", Status.RESTART, dtPlusOneHour.getTime());
    assertEquals(-1, dispatchableComparator.compare(j1, j5));

    // Test first greater than second
    assertEquals(1, dispatchableComparator.compare(j3, j1));
    assertEquals(1, dispatchableComparator.compare(j4, j1));
    assertEquals(1, dispatchableComparator.compare(j5, j1));
  }

  private class TestJob implements Job {
    /** The job ID */
    protected long id;

    /** The job type */
    protected String jobType;

    /** The job status */
    protected Status status;

    /** The date this job was created */
    protected Date dateCreated;

    TestJob(long id, String jobType, Status status, Date dateCreated) {
      this.id = id;
      this.jobType = jobType;
      this.status = status;
      this.dateCreated = dateCreated;
    }

    @Override
    public long getId() {
      return id;
    }

    @Override
    public String getCreator() {
      return null;
    }

    @Override
    public String getOrganization() {
      return null;
    }

    @Override
    public long getVersion() {
      return 0;
    }

    @Override
    public void setId(long id) {
      this.id = id;
    }

    @Override
    public String getJobType() {
      return jobType;
    }

    @Override
    public String getOperation() {
      return null;
    }

    @Override
    public void setOperation(String operation) {
    }

    @Override
    public List<String> getArguments() {
      return null;
    }

    @Override
    public void setArguments(List<String> arguments) {
    }

    @Override
    public Status getStatus() {
      return status;
    }

    @Override
    public FailureReason getFailureReason() {
      return null;
    }

    @Override
    public void setStatus(Status status) {
      this.status = status;
    }

    @Override
    public void setStatus(Status status, FailureReason reason) {
      this.status = status;
    }

    @Override
    public String getCreatedHost() {
      return null;
    }

    @Override
    public String getProcessingHost() {
      return null;
    }

    @Override
    public Date getDateCreated() {
      return dateCreated;
    }

    @Override
    public Date getDateStarted() {
      return null;
    }

    @Override
    public Long getQueueTime() {
      return null;
    }

    @Override
    public Long getRunTime() {
      return null;
    }

    @Override
    public Date getDateCompleted() {
      return null;
    }

    @Override
    public String getPayload() {
      return null;
    }

    @Override
    public void setPayload(String payload) {
    }

    @Override
    public JobContext getContext() {
      return null;
    }

    @Override
    public Long getParentJobId() {
      return null;
    }

    @Override
    public Long getRootJobId() {
      return null;
    }

    @Override
    public boolean isDispatchable() {
      return false;
    }

    @Override
    public void setDispatchable(boolean dispatchable) {
    }

    @Override
    public URI getUri() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public int getSignature() {
      return 0;
    }

  }

}
