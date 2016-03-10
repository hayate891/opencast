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
package org.opencastproject.live.workflowoperation;

import org.opencastproject.live.api.LiveException;
import org.opencastproject.live.api.LiveService;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageBuilder;
import org.opencastproject.mediapackage.MediaPackageBuilderFactory;
import org.opencastproject.workflow.api.WorkflowDefinitionImpl;
import org.opencastproject.workflow.api.WorkflowInstanceImpl;
import org.opencastproject.workflow.api.WorkflowOperationException;
import org.opencastproject.workflow.api.WorkflowOperationInstance;
import org.opencastproject.workflow.api.WorkflowOperationInstance.OperationState;
import org.opencastproject.workflow.api.WorkflowOperationInstanceImpl;
import org.opencastproject.workflow.api.WorkflowOperationResult;
import org.opencastproject.workflow.api.WorkflowOperationResult.Action;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class PublishLiveWorkflowOperationHandlerTest {

  /** The operation handler to test */
  private PublishLiveWorkflowOperationHandler operationHandler;

  private WorkflowInstanceImpl workflowInstance;

  /** The operation instance */
  private WorkflowOperationInstance operation;

  /** The original media package */
  private MediaPackage mediaPackage;

  /** The Live Service */
  private LiveService liveService;

  @Before
  public void setUp() throws Exception {
    MediaPackageBuilder builder = MediaPackageBuilderFactory.newInstance().newMediaPackageBuilder();

    // Load the test resources
    URI mediaPackageURI = PublishLiveWorkflowOperationHandler.class.getResource("/live_mediapackage.xml").toURI();
    mediaPackage = builder.loadFromXml(mediaPackageURI.toURL().openStream());

    // Set up the operation handler
    operationHandler = new PublishLiveWorkflowOperationHandler();

    WorkflowDefinitionImpl def = new WorkflowDefinitionImpl();
    def.setId("DCE-production");
    def.setPublished(true);
    workflowInstance = new WorkflowInstanceImpl(def, mediaPackage, null, null, null, null);
    workflowInstance.setId(1);

    // The Live Service
    liveService = EasyMock.createNiceMock(LiveService.class);

    operationHandler = new PublishLiveWorkflowOperationHandler();
    // operationHandler.setServiceRegistry(serviceRegistry);
    operationHandler.setLiveService(liveService);

    operation = new WorkflowOperationInstanceImpl("publish-live", OperationState.RUNNING);
    List<WorkflowOperationInstance> operationList = new ArrayList<WorkflowOperationInstance>();
    operationList.add(operation);

    workflowInstance.setOperations(operationList);
  }

  @Test
  public void testStart() throws Exception {
    liveService.publishMediaPackage(workflowInstance);
    EasyMock.replay(liveService);

    WorkflowOperationResult result = operationHandler.start(workflowInstance, null);
    Assert.assertEquals(Action.CONTINUE, result.getAction());
  }

  @Test(expected = WorkflowOperationException.class)
  public void testStartException() throws Exception {
    liveService.publishMediaPackage(workflowInstance);
    EasyMock.expectLastCall().andThrow(new LiveException("test"));
    EasyMock.replay(liveService);
    WorkflowOperationResult result = operationHandler.start(workflowInstance, null);
  }

}