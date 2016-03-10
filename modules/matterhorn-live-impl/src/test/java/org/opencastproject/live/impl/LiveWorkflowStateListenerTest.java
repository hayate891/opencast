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
package org.opencastproject.live.impl;

import org.opencastproject.live.api.LiveService;
import org.opencastproject.security.api.Organization;
import org.opencastproject.workflow.api.WorkflowDefinitionImpl;
import org.opencastproject.workflow.api.WorkflowInstance.WorkflowState;
import org.opencastproject.workflow.api.WorkflowInstanceImpl;
import org.opencastproject.workflow.api.WorkflowService;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class LiveWorkflowStateListenerTest {

  private WorkflowService workflowService;
  private LiveService liveService;

  private LiveWorkflowStateListener wfListener;
  private WorkflowInstanceImpl workflowInstance;

  @Before
  public void setUp() throws Exception {
    workflowService = EasyMock.createStrictMock(WorkflowService.class);

    liveService = EasyMock.createStrictMock(LiveService.class);

    // Organization
    Organization org = EasyMock.createNiceMock(Organization.class);
    Map<String, String> properties = new HashMap<String, String>();
    properties.put("org.opencastproject.engage.ui.url", "http://engage.harvard.edu");
    EasyMock.expect(org.getProperties()).andReturn(properties);
    EasyMock.replay(org);

    // Workflow instance
    properties = new HashMap<String, String>();
    properties.put("schedule.location", "demo-capture-agent");
    WorkflowDefinitionImpl wfDef = new WorkflowDefinitionImpl();
    wfDef.setId("DCE-production");
    wfDef.setPublished(true);

    workflowInstance = new WorkflowInstanceImpl(wfDef, null, null, null, org, properties);
    workflowInstance.setId(1);
    workflowInstance.setOrganization(org);
    workflowInstance.setState(WorkflowState.INSTANTIATED);

    wfListener = new LiveWorkflowStateListener(workflowService, liveService, workflowInstance);
  }

  private void verifyStateChanged(WorkflowState state) throws Exception {
    workflowService.removeWorkflowListener(wfListener);
    EasyMock.expectLastCall().once();
    EasyMock.replay(workflowService);

    liveService.retractMediaPackage(workflowInstance);
    EasyMock.expectLastCall().once();
    EasyMock.replay(liveService);

    workflowInstance.setState(state);
    wfListener.stateChanged(workflowInstance);

    // Check if both
    EasyMock.verify(workflowService, liveService);
  }

  @Test
  public void testWorkflowStateChangedToFailed() throws Exception {
    verifyStateChanged(WorkflowState.FAILED);
  }

  @Test
  public void testWorkflowStateChangedToStopped() throws Exception {
    verifyStateChanged(WorkflowState.STOPPED);
  }

}
