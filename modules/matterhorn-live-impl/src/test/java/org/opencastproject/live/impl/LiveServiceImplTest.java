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

import org.opencastproject.distribution.api.DownloadDistributionService;
import org.opencastproject.job.api.Job;
import org.opencastproject.live.api.LiveException;
import org.opencastproject.live.api.LiveService;
import org.opencastproject.mediapackage.Attachment;
import org.opencastproject.mediapackage.Catalog;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageBuilder;
import org.opencastproject.mediapackage.MediaPackageBuilderFactory;
import org.opencastproject.mediapackage.MediaPackageElementFlavor;
import org.opencastproject.mediapackage.MediaPackageElementParser;
import org.opencastproject.mediapackage.Publication;
import org.opencastproject.mediapackage.Track;
import org.opencastproject.mediapackage.VideoStream;
import org.opencastproject.mediapackage.lock.api.MediaPackageLockManager;
import org.opencastproject.mediapackage.lock.api.MediaPackageLockRequester;
import org.opencastproject.mediapackage.track.TrackImpl;
import org.opencastproject.metadata.dublincore.DublinCoreCatalogService;
import org.opencastproject.search.api.SearchQuery;
import org.opencastproject.search.api.SearchResult;
import org.opencastproject.search.api.SearchResultImpl;
import org.opencastproject.search.api.SearchService;
import org.opencastproject.security.api.Organization;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.util.MimeType;
import org.opencastproject.util.MimeTypes;
import org.opencastproject.workflow.api.WorkflowDefinitionImpl;
import org.opencastproject.workflow.api.WorkflowInstanceImpl;
import org.opencastproject.workflow.api.WorkflowListener;
import org.opencastproject.workflow.api.WorkflowQuery;
import org.opencastproject.workflow.api.WorkflowService;
import org.opencastproject.workflow.api.WorkflowSet;
import org.opencastproject.workspace.api.Workspace;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class LiveServiceImplTest {

  /** The service to test */
  private LiveServiceImpl service;

  private WorkflowInstanceImpl workflowInstance;

  /** The original media package */
  private MediaPackage mediaPackage;

  /** The original media package with the live publication channel added */
  private MediaPackage mediaPackageWithLiveChannel;

  /** The published media package */
  private MediaPackage publishedLiveMediaPackage;
  /** The media package with the publication channel */
  // private MediaPackage pubChannelMediaPackage;
  /** A media package with 'engage' publication channel */
  // private MediaPackage mediaPackageEngageChannel;
  /** A media package with no publication channel */
  // private MediaPackage mediaPackageNoChannel;

  private Track[] publishedLiveTracks;
  // private MediaPackageElementFlavor[] liveFlavors;
  private MimeType mimeType;

  /** Distribution jobs */
  private List<Job> jobs;

  private ComponentContext cc;
  private MediaPackageBuilder builder;
  private Organization org;
  private WorkflowDefinitionImpl wfDef;
  private Map<String, String> properties = new HashMap<String, String>();
  private SecurityService securityService;
  private WorkflowService workflowService;
  private SearchService searchService;
  private DownloadDistributionService distributionService;
  private ServiceRegistry serviceRegistry;
  private Workspace ws;

  private Capture<MediaPackage> capturedMp;
  private MediaPackageLockManager mediaPackageLockService;

  private Set<String> downloadElementIds;

  private static final String STREAMING_SERVER_URL = "rtmp://streaming.harvard.edu/live";

  @Before
  public void setUp() throws Exception {
    builder = MediaPackageBuilderFactory.newInstance().newMediaPackageBuilder();

    // Media package only has catalogs, no tracks
    URI mediaPackageURI = LiveServiceImplTest.class.getResource("/mediapackage.xml").toURI();
    mediaPackage = builder.loadFromXml(mediaPackageURI.toURL().openStream());

    // Media package only has catalogs, no tracks
    mediaPackageURI = LiveServiceImplTest.class.getResource("/mediapackage_with_live_pub_channel.xml").toURI();
    mediaPackageWithLiveChannel = builder.loadFromXml(mediaPackageURI.toURL().openStream());

    //
    mediaPackageURI = LiveServiceImplTest.class.getResource("/published_live_mediapackage.xml").toURI();
    publishedLiveMediaPackage = builder.loadFromXml(mediaPackageURI.toURL().openStream());

    // Organization
    org = EasyMock.createNiceMock(Organization.class);
    properties.put("org.opencastproject.engage.ui.url", "http://engage.harvard.edu");
    EasyMock.expect(org.getProperties()).andReturn(properties).anyTimes();
    EasyMock.replay(org);

    // Workflow instance
    properties = new HashMap<String, String>();
    properties.put("schedule.location", "demo-capture-agent");
    wfDef = new WorkflowDefinitionImpl();
    wfDef.setId("DCE-production");
    wfDef.setPublished(true);

    workflowInstance = new WorkflowInstanceImpl(wfDef, mediaPackage, null, null, org, properties);
    workflowInstance.setId(1);
    workflowInstance.setOrganization(org);

    mimeType = MimeTypes.parseMimeType("video/x-flv");

    // Security service
    securityService = EasyMock.createNiceMock(SecurityService.class);
    EasyMock.replay(securityService);

    // The ServiceRegistry
    serviceRegistry = EasyMock.createNiceMock(ServiceRegistry.class);

    // The DublinCoreCatalogService
    DublinCoreCatalogService dublinCoreService = new DublinCoreCatalogService();

    // The SearchService
    searchService = EasyMock.createNiceMock(SearchService.class);

    // The DownloadDistributionService
    distributionService = EasyMock.createNiceMock(DownloadDistributionService.class);

    // The Workspace
    ws = EasyMock.createNiceMock(Workspace.class);

    // Media package lock service
    mediaPackageLockService = EasyMock.createNiceMock(MediaPackageLockManager.class);
    EasyMock.expect(
            mediaPackageLockService.lock((String) EasyMock.anyObject(),
                    (MediaPackageLockRequester) EasyMock.anyObject())).andReturn(1);
    EasyMock.replay(mediaPackageLockService);

    // WorkflowService
    WorkflowSet set = EasyMock.createNiceMock(WorkflowSet.class);
    EasyMock.expect(set.getItems()).andReturn(new WorkflowInstanceImpl[0]);
    EasyMock.replay(set);
    workflowService = EasyMock.createNiceMock(WorkflowService.class);
    workflowService.addWorkflowListener((WorkflowListener) EasyMock.anyObject());
    EasyMock.expect(workflowService.getWorkflowInstancesForAdministrativeRead((WorkflowQuery) EasyMock.anyObject()))
            .andReturn(set)
            .anyTimes();
    EasyMock.replay(workflowService);

    // Live service
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

    service = new LiveServiceImpl();
    service.setServiceRegistry(serviceRegistry);
    service.setDownloadDistributionService(distributionService);
    service.setDublinCoreService(dublinCoreService);
    service.setSearchService(searchService);
    service.setWorkspace(ws);
    service.setWorkflowService(workflowService);
    service.setSecurityService(securityService);
    service.setMediaPackageLockManager(mediaPackageLockService);

    service.activate(cc);
  }

  private void replayServices() {
    EasyMock.replay(searchService);
    EasyMock.replay(distributionService);
    EasyMock.replay(serviceRegistry);
    EasyMock.replay(ws);
  }

  private void assertExpectedLiveTracks(Track[] liveTracks, long duration) {
    Assert.assertEquals(4, liveTracks.length);
    Assert.assertEquals(STREAMING_SERVER_URL
            + "/stream-presenter-delivery-0ebf2138-3f8c-4454-8e01-4ff109a6681e-demo-capture-agent-3840x1080.suffix",
            liveTracks[0].getURI().toString());
    Assert.assertEquals(new Long(duration), liveTracks[0].getDuration());
    Assert.assertEquals(mimeType, liveTracks[0].getMimeType());
    Assert.assertEquals(true, liveTracks[0].isLive());
    Assert.assertEquals(1, liveTracks[0].getStreams().length);
    Assert.assertEquals(new Integer(1080), ((VideoStream) liveTracks[0].getStreams()[0]).getFrameHeight());
    Assert.assertEquals(new Integer(3840), ((VideoStream) liveTracks[0].getStreams()[0]).getFrameWidth());

    Assert.assertEquals(STREAMING_SERVER_URL
            + "/stream-presenter-delivery-0ebf2138-3f8c-4454-8e01-4ff109a6681e-demo-capture-agent-1920x540.suffix",
            liveTracks[1].getURI().toString());
    Assert.assertEquals(new Long(duration), liveTracks[0].getDuration());
    Assert.assertEquals(mimeType, liveTracks[1].getMimeType());
    Assert.assertEquals(true, liveTracks[1].isLive());
    Assert.assertEquals(1, liveTracks[1].getStreams().length);
    Assert.assertEquals(new Integer(540), ((VideoStream) liveTracks[1].getStreams()[0]).getFrameHeight());
    Assert.assertEquals(new Integer(1920), ((VideoStream) liveTracks[1].getStreams()[0]).getFrameWidth());

    Assert.assertEquals(STREAMING_SERVER_URL
            + "/stream-presentation-delivery-0ebf2138-3f8c-4454-8e01-4ff109a6681e-demo-capture-agent-3840x1080.suffix",
            liveTracks[2].getURI().toString());
    Assert.assertEquals(new Long(duration), liveTracks[2].getDuration());
    Assert.assertEquals(mimeType, liveTracks[2].getMimeType());
    Assert.assertEquals(true, liveTracks[2].isLive());
    Assert.assertEquals(1, liveTracks[2].getStreams().length);
    Assert.assertEquals(new Integer(1080), ((VideoStream) liveTracks[2].getStreams()[0]).getFrameHeight());
    Assert.assertEquals(new Integer(3840), ((VideoStream) liveTracks[2].getStreams()[0]).getFrameWidth());

    Assert.assertEquals(STREAMING_SERVER_URL
            + "/stream-presentation-delivery-0ebf2138-3f8c-4454-8e01-4ff109a6681e-demo-capture-agent-1920x540.suffix",
            liveTracks[3].getURI().toString());
    Assert.assertEquals(new Long(duration), liveTracks[3].getDuration());
    Assert.assertEquals(mimeType, liveTracks[3].getMimeType());
    Assert.assertEquals(true, liveTracks[3].isLive());
    Assert.assertEquals(1, liveTracks[3].getStreams().length);
    Assert.assertEquals(new Integer(540), ((VideoStream) liveTracks[3].getStreams()[0]).getFrameHeight());
    Assert.assertEquals(new Integer(1920), ((VideoStream) liveTracks[3].getStreams()[0]).getFrameWidth());
  }

  private Job createJob(long id, String elementId, String payload) {
    Job job = EasyMock.createNiceMock(Job.class);
    List<String> args = new ArrayList<String>();
    args.add("anything");
    args.add("anything");
    args.add(elementId);
    EasyMock.expect(job.getId()).andReturn(id).anyTimes();
    EasyMock.expect(job.getArguments()).andReturn(args).anyTimes();
    EasyMock.expect(job.getPayload()).andReturn(payload).anyTimes();
    EasyMock.expect(job.getStatus()).andReturn(Job.Status.FINISHED).anyTimes();
    EasyMock.expect(job.getDateCreated()).andReturn(new Date()).anyTimes();
    EasyMock.expect(job.getDateStarted()).andReturn(new Date()).anyTimes();
    EasyMock.expect(job.getQueueTime()).andReturn(new Long(0)).anyTimes();
    EasyMock.replay(job);
    return job;
  }

  @Test
  public void testReplaceVariables() throws Exception {
    replayServices();

    MediaPackageElementFlavor flavor = new MediaPackageElementFlavor("presenter", "delivery");

    String expectedStreamName = "stream-presenter-delivery-0ebf2138-3f8c-4454-8e01-4ff109a6681e-demo-capture-agent-3840x1080.suffix";
    String actualStreamName = service.replaceVariables(workflowInstance, flavor, "3840x1080");

    Assert.assertEquals(expectedStreamName, actualStreamName);
  }

  @Test
  public void testAddLivePublicationChannel() throws Exception {
    replayServices();

    service.addLivePublicationChannel(mediaPackage, workflowInstance);

    Publication[] publications = mediaPackage.getPublications();
    Assert.assertEquals(1, publications.length);
    Assert.assertEquals(LiveService.CHANNEL_ID, publications[0].getChannel());
    Assert.assertEquals("text/html", publications[0].getMimeType().toString());
    Assert.assertEquals("http://engage.harvard.edu/engage/player/watch.html?id=0ebf2138-3f8c-4454-8e01-4ff109a6681e",
            publications[0].getURI().toString());
  }

  @Test
  public void testRemoveLivePublicationChannel() throws Exception {
    replayServices();

    service.removeLivePublicationChannel(mediaPackageWithLiveChannel);

    Publication[] publications = mediaPackage.getPublications();
    Assert.assertEquals(0, publications.length);
  }

  @Test
  public void testCreateLiveTracks() throws Exception {
    replayServices();

    assertExpectedLiveTracks(service.createLiveTracks(workflowInstance, 60000), 60000);
  }

  @Test(expected = LiveException.class)
  public void testCreateLiveTracksNullStreamingUrl() throws Exception {
    replayServices();
    service.setStreamingUrl(null);

    service.createLiveTracks(workflowInstance, 60000);
  }

  private void setUpForBuildMediaPackageForSearchIndex() throws Exception {

    downloadElementIds = new HashSet<String>();
    downloadElementIds.add("series-dc");
    downloadElementIds.add("episode-dc");
    downloadElementIds.add("security-policy");

    TrackImpl publishedPresenter1 = (TrackImpl) publishedLiveMediaPackage.getElementById("live-presenter-1");
    TrackImpl publishedPresenter2 = (TrackImpl) publishedLiveMediaPackage.getElementById("live-presenter-2");

    TrackImpl publishedPresentation1 = (TrackImpl) publishedLiveMediaPackage.getElementById("live-presentation-1");
    TrackImpl publishedPresentation2 = (TrackImpl) publishedLiveMediaPackage.getElementById("live-presentation-2");

    publishedLiveTracks = new Track[4];
    publishedLiveTracks[0] = publishedPresenter1;
    publishedLiveTracks[1] = publishedPresenter2;
    publishedLiveTracks[2] = publishedPresentation1;
    publishedLiveTracks[3] = publishedPresentation2;

    // Distribution jobs
    Catalog episodeDC = publishedLiveMediaPackage.getCatalog("episode-dc-published");
    Catalog seriesDC = publishedLiveMediaPackage.getCatalog("series-dc-published");
    Attachment security = publishedLiveMediaPackage.getAttachment("security-policy-published");

    Job job1 = createJob(1L, "episode-dc", MediaPackageElementParser.getAsXml(episodeDC));
    Job job2 = createJob(2L, "series-dc", MediaPackageElementParser.getAsXml(seriesDC));
    Job job3 = createJob(3L, "security-policy", MediaPackageElementParser.getAsXml(security));

    jobs = new ArrayList<Job>();
    jobs.add(job1);
    jobs.add(job2);
    jobs.add(job3);

    EasyMock.expect(distributionService.distribute(LiveService.CHANNEL_ID, mediaPackage, "series-dc", true)).andReturn(
            job1);
    EasyMock.expect(distributionService.distribute(LiveService.CHANNEL_ID, mediaPackage, "episode-dc", true))
            .andReturn(job2);
    EasyMock.expect(distributionService.distribute(LiveService.CHANNEL_ID, mediaPackage, "security-policy", true))
            .andReturn(job3);

    // Publish job
    Job jobPub = createJob(4L, "anything", "anything");
    capturedMp = new Capture<MediaPackage>();
    EasyMock.expect(searchService.add(EasyMock.capture(capturedMp))).andReturn(jobPub);

    // Service registry returns jobs created above
    EasyMock.expect(serviceRegistry.getJob(1L)).andReturn(job1).anyTimes();
    EasyMock.expect(serviceRegistry.getJob(2L)).andReturn(job2).anyTimes();
    EasyMock.expect(serviceRegistry.getJob(3L)).andReturn(job3).anyTimes();
    EasyMock.expect(serviceRegistry.getJob(4L)).andReturn(jobPub).anyTimes();

  }

  @Test
  public void testBuildMediaPackageForSearchIndex() throws Exception {
    setUpForBuildMediaPackageForSearchIndex();
    replayServices();

    MediaPackage generated = service.buildMediaPackageForSearchIndex(mediaPackage, jobs, downloadElementIds,
            publishedLiveTracks);

    Assert.assertEquals(new Long(300000), generated.getDuration());

    Catalog[] catalogs = generated.getCatalogs(new MediaPackageElementFlavor("dublincore", "episode"));
    Assert.assertEquals(1, catalogs.length);
    Assert.assertEquals("static/episode_dublincore.xml", catalogs[0].getURI().toString());

    catalogs = generated.getCatalogs(new MediaPackageElementFlavor("dublincore", "series"));
    Assert.assertEquals(1, catalogs.length);
    Assert.assertEquals("static/series_dublincore.xml", catalogs[0].getURI().toString());

    Track presenterLive = generated.getTrack("live-presenter-1");
    Assert.assertNotNull(presenterLive);
    Assert.assertEquals("rtmp://streaming.harvard.edu/live/live-presenter-presenter-delivery-3840x1080", presenterLive
            .getURI().toString());
    Assert.assertTrue(presenterLive.isLive());
    Assert.assertEquals(new Long(300000), presenterLive.getDuration());
    Assert.assertEquals(1, presenterLive.getStreams().length);
    Assert.assertEquals(new Integer(1080), ((VideoStream) presenterLive.getStreams()[0]).getFrameHeight());
    Assert.assertEquals(new Integer(3840), ((VideoStream) presenterLive.getStreams()[0]).getFrameWidth());

    presenterLive = generated.getTrack("live-presenter-2");
    Assert.assertNotNull(presenterLive);
    Assert.assertEquals("rtmp://streaming.harvard.edu/live/live-presenter-presenter-delivery-1920x540", presenterLive
            .getURI().toString());
    Assert.assertTrue(presenterLive.isLive());
    Assert.assertEquals(new Long(300000), presenterLive.getDuration());
    Assert.assertEquals(1, presenterLive.getStreams().length);
    Assert.assertEquals(new Integer(540), ((VideoStream) presenterLive.getStreams()[0]).getFrameHeight());
    Assert.assertEquals(new Integer(1920), ((VideoStream) presenterLive.getStreams()[0]).getFrameWidth());

    Track presentationLive = generated.getTrack("live-presentation-1");
    Assert.assertNotNull(presentationLive);
    Assert.assertEquals("rtmp://streaming.harvard.edu/live/live-presentation-presentation-delivery-3840x1080",
            presentationLive.getURI().toString());
    Assert.assertTrue(presentationLive.isLive());
    Assert.assertEquals(new Long(300000), presentationLive.getDuration());
    Assert.assertEquals(1, presentationLive.getStreams().length);
    Assert.assertEquals(new Integer(1080), ((VideoStream) presentationLive.getStreams()[0]).getFrameHeight());
    Assert.assertEquals(new Integer(3840), ((VideoStream) presentationLive.getStreams()[0]).getFrameWidth());

    presentationLive = generated.getTrack("live-presentation-2");
    Assert.assertNotNull(presentationLive);
    Assert.assertEquals("rtmp://streaming.harvard.edu/live/live-presentation-presentation-delivery-1920x540",
            presentationLive.getURI().toString());
    Assert.assertTrue(presentationLive.isLive());
    Assert.assertEquals(new Long(300000), presentationLive.getDuration());
    Assert.assertEquals(1, presentationLive.getStreams().length);
    Assert.assertEquals(new Integer(540), ((VideoStream) presentationLive.getStreams()[0]).getFrameHeight());
    Assert.assertEquals(new Integer(1920), ((VideoStream) presentationLive.getStreams()[0]).getFrameWidth());
  }

  private void setUpForDistributeAndPublish() throws Exception {
    // EasyMock.expect(searchService.getByQuery((SearchQuery) EasyMock.anyObject())).andReturn(searchResult);

    URI catalogURI = LiveServiceImplTest.class.getResource("/episode_dublincore.xml").toURI();
    File catalogFile = new File(catalogURI);

    EasyMock.expect(ws.get((URI) EasyMock.anyObject())).andReturn(catalogFile);
    EasyMock.expect(
            ws.put((String) EasyMock.anyObject(), (String) EasyMock.anyObject(), (String) EasyMock.anyObject(),
                    (InputStream) EasyMock.anyObject())).andReturn(catalogURI);
    EasyMock.expect(
            ws.getURI((String) EasyMock.anyObject(), (String) EasyMock.anyObject(), (String) EasyMock.anyObject()))
            .andReturn(catalogURI);
  }

  @Test
  public void testDistributeAndPublish() throws Exception {
    setUpForBuildMediaPackageForSearchIndex();
    setUpForDistributeAndPublish();
    replayServices();

    service.distributeAndPublish(workflowInstance);

    // Check live tracks in published mp that was captured
    MediaPackage mp = capturedMp.getValue();
    assertExpectedLiveTracks(mp.getTracks(), 60000);

    // Has the service generated the expected distribution jobs?
    EasyMock.verify(distributionService);
  }

  @Test
  public void testPublishMediaPackage() throws Exception {
    setUpForBuildMediaPackageForSearchIndex();
    setUpForDistributeAndPublish();
    replayServices();

    service.publishMediaPackage(workflowInstance);

    Publication[] publications = mediaPackage.getPublications();
    Assert.assertEquals(1, publications.length);
    Assert.assertEquals(LiveService.CHANNEL_ID, publications[0].getChannel());
    Assert.assertEquals("text/html", publications[0].getMimeType().toString());
    Assert.assertEquals("http://engage.harvard.edu/engage/player/watch.html?id=0ebf2138-3f8c-4454-8e01-4ff109a6681e",
            publications[0].getURI().toString());
  }

  private void setUpForRetractMediaPackage() throws Exception {
    workflowInstance.setMediaPackage(mediaPackageWithLiveChannel);

    URI searchResultURI = LiveServiceImplTest.class.getResource("/search_results.xml").toURI();
    SearchResult searchResult = SearchResultImpl.valueOf(searchResultURI.toURL().openStream());

    EasyMock.expect(searchService.getByQuery((SearchQuery) EasyMock.anyObject())).andReturn(searchResult);

    // Retraction jobs (distribution)
    Catalog episodeDC = publishedLiveMediaPackage.getCatalog("episode-dc-published");
    Catalog seriesDC = publishedLiveMediaPackage.getCatalog("series-dc-published");
    Attachment security = publishedLiveMediaPackage.getAttachment("security-policy-published");

    Job job5 = createJob(5L, "episode-dc-published", MediaPackageElementParser.getAsXml(episodeDC));
    Job job6 = createJob(6L, "series-dc-published", MediaPackageElementParser.getAsXml(seriesDC));
    Job job7 = createJob(7L, "security-policy-published", MediaPackageElementParser.getAsXml(security));

    EasyMock.expect(distributionService.retract(LiveService.CHANNEL_ID, mediaPackage, "episode-dc-published"))
            .andReturn(job5);
    EasyMock.expect(distributionService.retract(LiveService.CHANNEL_ID, mediaPackage, "series-dc-published"))
            .andReturn(job6);
    EasyMock.expect(distributionService.retract(LiveService.CHANNEL_ID, mediaPackage, "security-policy-published"))
            .andReturn(job7);

    // Retraction job (search)
    Job jobDel = createJob(8L, "anything", "anything");
    EasyMock.expect(searchService.delete(mediaPackage.getIdentifier().toString())).andReturn(jobDel);

    // Service registry returns jobs created above
    EasyMock.expect(serviceRegistry.getJob(5L)).andReturn(job5).anyTimes();
    EasyMock.expect(serviceRegistry.getJob(6L)).andReturn(job6).anyTimes();
    EasyMock.expect(serviceRegistry.getJob(7L)).andReturn(job7).anyTimes();
    EasyMock.expect(serviceRegistry.getJob(8L)).andReturn(jobDel).anyTimes();
  }

  @Test
  public void testRetractMediaPackage() throws Exception {
    setUpForRetractMediaPackage();
    replayServices();

    service.retractMediaPackage(workflowInstance);

    Publication[] publications = mediaPackage.getPublications();
    Assert.assertEquals(0, publications.length);
  }

  @Test
  public void testUpdateMediaPackage() throws Exception {
    workflowInstance.setMediaPackage(mediaPackageWithLiveChannel);

    mediaPackageWithLiveChannel.setDuration(90000L);

    URI searchResultURI = LiveServiceImplTest.class.getResource("/search_results.xml").toURI();
    SearchResult searchResult = SearchResultImpl.valueOf(searchResultURI.toURL().openStream());

    EasyMock.expect(searchService.getByQuery((SearchQuery) EasyMock.anyObject())).andReturn(searchResult).anyTimes();

    setUpForBuildMediaPackageForSearchIndex();
    setUpForDistributeAndPublish();
    replayServices();

    service.updateMediaPackage(workflowInstance);

    // Check new duration in live tracks in published mp that was captured
    MediaPackage mp = capturedMp.getValue();
    assertExpectedLiveTracks(mp.getTracks(), 90000);
    Assert.assertEquals(90000L, mp.getDuration().longValue());

    Publication[] publications = mediaPackageWithLiveChannel.getPublications();
    Assert.assertEquals(1, publications.length);
    Assert.assertEquals(LiveService.CHANNEL_ID, publications[0].getChannel());
    Assert.assertEquals("text/html", publications[0].getMimeType().toString());
    Assert.assertEquals("http://engage.harvard.edu/engage/player/watch.html?id=0ebf2138-3f8c-4454-8e01-4ff109a6681e",
            publications[0].getURI().toString());
  }

}
