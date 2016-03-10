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

import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageBuilder;
import org.opencastproject.mediapackage.MediaPackageBuilderFactory;
import org.opencastproject.mediapackage.lock.api.MediaPackageLockManager;
import org.opencastproject.mediapackage.lock.api.MediaPackageLockRequester;
import org.opencastproject.security.api.Organization;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.workflow.api.WorkflowDefinitionImpl;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowInstanceImpl;
import org.opencastproject.workflow.api.WorkflowListener;
import org.opencastproject.workflow.api.WorkflowQuery;
import org.opencastproject.workflow.api.WorkflowService;
import org.opencastproject.workflow.api.WorkflowSet;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class LiveServiceAddWorkflowListenerTest {
  /** The service to test */
  private LiveServiceImpl service;

  /** The media package with the publication channel */
  private MediaPackage mediaPackageLiveChannel;
  /** A media package with 'engage' publication channel */
  private MediaPackage mediaPackageEngageChannel;
  /** A media package with no publication channel */
  private MediaPackage mediaPackageNoChannel;

  private ComponentContext cc;
  private MediaPackageBuilder builder;
  private Organization org;
  private WorkflowDefinitionImpl wfDef;
  private Map<String, String> properties = new HashMap<String, String>();
  private SecurityService securityService;
  private WorkflowService workflowService;
  private MediaPackageLockManager mediaPackageLockService;

  private static final String STREAMING_SERVER_URL = "rtmp://streaming.harvard.edu/live";

  @Before
  public void setUp() throws Exception {
    builder = MediaPackageBuilderFactory.newInstance().newMediaPackageBuilder();

    URI mpURI = LiveServiceImplTest.class.getResource("/mediapackage_with_live_pub_channel.xml").toURI();
    mediaPackageLiveChannel = builder.loadFromXml(mpURI.toURL().openStream());

    mpURI = LiveServiceImplTest.class.getResource("/mediapackage_with_engage_pub_channel.xml").toURI();
    mediaPackageEngageChannel = builder.loadFromXml(mpURI.toURL().openStream());

    mpURI = LiveServiceImplTest.class.getResource("/mediapackage_with_no_pub_channel.xml").toURI();
    mediaPackageNoChannel = builder.loadFromXml(mpURI.toURL().openStream());

    org = EasyMock.createNiceMock(Organization.class);
    properties.put("org.opencastproject.engage.ui.url", "http://engage.harvard.edu");
    EasyMock.expect(org.getProperties()).andReturn(properties).anyTimes();
    EasyMock.replay(org);

    securityService = EasyMock.createNiceMock(SecurityService.class);
    EasyMock.replay(securityService);

    properties = new HashMap<String, String>();
    properties.put("schedule.location", "demo-capture-agent");
    wfDef = new WorkflowDefinitionImpl();
    wfDef.setId("DCE-production");
    wfDef.setPublished(true);

    // Set up the service
    BundleContext bc = EasyMock.createNiceMock(BundleContext.class);
    EasyMock.expect(bc.getProperty(LiveServiceImpl.LIVE_STREAMING_URL_PROPERTY)).andReturn(STREAMING_SERVER_URL);
    EasyMock.expect(bc.getProperty("org.opencastproject.security.digest.user")).andReturn("matterhorn_system_account");
    String streamName = "stream-#{flavor}-#{id}-#{caName}-#{resolution}.suffix";
    Properties props = new Properties();
    props.put(LiveServiceImpl.LIVE_STREAM_MIME_TYPE, "video/x-flv");
    props.put(LiveServiceImpl.LIVE_STREAM_NAME, streamName);
    props.put(LiveServiceImpl.LIVE_STREAM_RESOLUTION, "3840x1080,1920x540");
    props.put(LiveServiceImpl.LIVE_TARGET_FLAVORS, "presenter/delivery,presentation/delivery");

    cc = EasyMock.createNiceMock(ComponentContext.class);
    EasyMock.expect(cc.getBundleContext()).andReturn(bc);
    EasyMock.expect(cc.getProperties()).andReturn(props);
    EasyMock.replay(bc, cc);

    mediaPackageLockService = EasyMock.createNiceMock(MediaPackageLockManager.class);
    EasyMock.expect(
            mediaPackageLockService.lock((String) EasyMock.anyObject(),
                    (MediaPackageLockRequester) EasyMock.anyObject())).andReturn(1);
    EasyMock.replay(mediaPackageLockService);

  }

  @Test
  public void testAddWorkflowListeners() throws Exception {

    WorkflowInstance wf1 = new WorkflowInstanceImpl(wfDef, mediaPackageLiveChannel, null, null, org, properties);
    wf1.setId(1);
    WorkflowInstance wf2 = new WorkflowInstanceImpl(wfDef, mediaPackageEngageChannel, null, null, org, properties);
    wf2.setId(2);
    WorkflowInstance wf3 = new WorkflowInstanceImpl(wfDef, mediaPackageNoChannel, null, null, org, properties);
    wf3.setId(3);

    WorkflowInstance[] wfs = new WorkflowInstance[3];
    wfs[0] = wf1;
    wfs[1] = wf2;
    wfs[2] = wf3;
    WorkflowSet set = EasyMock.createNiceMock(WorkflowSet.class);
    EasyMock.expect(set.getItems()).andReturn(wfs);
    EasyMock.expect(set.size()).andReturn(3L);
    EasyMock.expect(set.getTotalCount()).andReturn(3L).anyTimes();
    EasyMock.replay(set);

    workflowService = EasyMock.createStrictMock(WorkflowService.class);
    EasyMock.expect(workflowService.getWorkflowInstancesForAdministrativeRead((WorkflowQuery) EasyMock.anyObject()))
            .andReturn(set).once();
    workflowService.addWorkflowListener((WorkflowListener) EasyMock.anyObject());
    EasyMock.expectLastCall().once();
    EasyMock.replay(workflowService);

    service = new LiveServiceImpl();
    service.setWorkflowService(workflowService);
    service.setSecurityService(securityService);
    service.activate(cc);
  }

  @Test
  public void testWorkflowListenersWithPaging() throws Exception {

    // Generate 20 workflows for the first page (count = 20)
    WorkflowInstance[] wfs20 = new WorkflowInstance[20];
    for (int i = 0; i < 7; i++) {
      WorkflowInstance wf1 = new WorkflowInstanceImpl(wfDef, mediaPackageLiveChannel, null, null, org, properties);
      wf1.setId(i * 3 + 1);
      wfs20[i * 3] = wf1;
      WorkflowInstance wf2 = new WorkflowInstanceImpl(wfDef, mediaPackageEngageChannel, null, null, org, properties);
      wf2.setId(i * 3 + 2);
      wfs20[i * 3 + 1] = wf2;
      if (i == 6) {
        break; // We got 20!
      }
      WorkflowInstance wf3 = new WorkflowInstanceImpl(wfDef, mediaPackageNoChannel, null, null, org, properties);
      wf3.setId(i * 3 + 3);
      wfs20[i * 3 + 2] = wf3;
    }
    // Generate 10 workflows for the second page
    WorkflowInstance[] wfs10 = new WorkflowInstance[10];
    for (int i = 0; i < 4; i++) {
      WorkflowInstance wf1 = new WorkflowInstanceImpl(wfDef, mediaPackageLiveChannel, null, null, org, properties);
      wf1.setId(i * 3 + 1);
      wfs10[i * 3] = wf1;
      if (i == 3) {
        break; // We got 10!
      }
      WorkflowInstance wf2 = new WorkflowInstanceImpl(wfDef, mediaPackageEngageChannel, null, null, org, properties);
      wf2.setId(i * 3 + 2);
      wfs10[i * 3 + 1] = wf2;
      WorkflowInstance wf3 = new WorkflowInstanceImpl(wfDef, mediaPackageNoChannel, null, null, org, properties);
      wf3.setId(i * 3 + 3);
      wfs10[i * 3 + 2] = wf3;
    }

    WorkflowSet set20 = EasyMock.createNiceMock(WorkflowSet.class);
    EasyMock.expect(set20.getItems()).andReturn(wfs20).once();
    EasyMock.expect(set20.size()).andReturn(20L);
    EasyMock.expect(set20.getTotalCount()).andReturn(30L).anyTimes();
    EasyMock.replay(set20);
    WorkflowSet set10 = EasyMock.createNiceMock(WorkflowSet.class);
    EasyMock.expect(set10.getItems()).andReturn(wfs10).once();
    EasyMock.expect(set10.size()).andReturn(10L);
    EasyMock.expect(set10.getTotalCount()).andReturn(30L).anyTimes();
    EasyMock.replay(set10);

    // Strict mock to check the number of calls!
    workflowService = EasyMock.createStrictMock(WorkflowService.class);
    EasyMock.expect(workflowService.getWorkflowInstancesForAdministrativeRead((WorkflowQuery) EasyMock.anyObject()))
            .andReturn(set20).once();
    workflowService.addWorkflowListener((WorkflowListener) EasyMock.anyObject());
    EasyMock.expectLastCall().times(7);
    EasyMock.expect(workflowService.getWorkflowInstancesForAdministrativeRead((WorkflowQuery) EasyMock.anyObject()))
            .andReturn(set10).once();
    workflowService.addWorkflowListener((WorkflowListener) EasyMock.anyObject());
    EasyMock.expectLastCall().times(4);
    EasyMock.replay(workflowService);

    service = new LiveServiceImpl();
    service.setWorkflowService(workflowService);
    service.setSecurityService(securityService);

    service.activate(cc);
  }

}
