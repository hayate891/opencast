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
import org.opencastproject.job.api.JobBarrier;
import org.opencastproject.live.api.LiveException;
import org.opencastproject.live.api.LiveService;
import org.opencastproject.mediapackage.Attachment;
import org.opencastproject.mediapackage.Catalog;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElement;
import org.opencastproject.mediapackage.MediaPackageElementBuilder;
import org.opencastproject.mediapackage.MediaPackageElementBuilderFactory;
import org.opencastproject.mediapackage.MediaPackageElementFlavor;
import org.opencastproject.mediapackage.MediaPackageElementParser;
import org.opencastproject.mediapackage.MediaPackageElements;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.mediapackage.MediaPackageReference;
import org.opencastproject.mediapackage.MediaPackageReferenceImpl;
import org.opencastproject.mediapackage.Publication;
import org.opencastproject.mediapackage.PublicationImpl;
import org.opencastproject.mediapackage.Track;
import org.opencastproject.mediapackage.lock.api.MediaPackageLockException;
import org.opencastproject.mediapackage.lock.api.MediaPackageLockManager;
import org.opencastproject.mediapackage.lock.api.MediaPackageLockRequester;
import org.opencastproject.mediapackage.selector.SimpleElementSelector;
import org.opencastproject.mediapackage.track.TrackImpl;
import org.opencastproject.mediapackage.track.VideoStreamImpl;
import org.opencastproject.metadata.dublincore.DublinCore;
import org.opencastproject.metadata.dublincore.DublinCoreCatalog;
import org.opencastproject.metadata.dublincore.DublinCoreCatalogService;
import org.opencastproject.metadata.dublincore.DublinCoreValue;
import org.opencastproject.metadata.dublincore.EncodingSchemeUtils;
import org.opencastproject.metadata.dublincore.Precision;
import org.opencastproject.search.api.SearchQuery;
import org.opencastproject.search.api.SearchResult;
import org.opencastproject.search.api.SearchService;
import org.opencastproject.security.api.DefaultOrganization;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.util.SecurityUtil;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.serviceregistry.api.ServiceRegistryException;
import org.opencastproject.util.Log;
import org.opencastproject.util.MimeTypes;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.UrlSupport;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowInstance.WorkflowState;
import org.opencastproject.workflow.api.WorkflowOperationException;
import org.opencastproject.workflow.api.WorkflowQuery;
import org.opencastproject.workflow.api.WorkflowService;
import org.opencastproject.workflow.api.WorkflowSet;
import org.opencastproject.workspace.api.Workspace;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.utils.URIUtils;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implement {@link SmilService} interface.
 */
public class LiveServiceImpl implements LiveService, MediaPackageLockRequester {

  /** Default values for configuration options */
  private static final String DEFAULT_STREAM_MIME_TYPE = "video/mp4";
  private static final String DEFAULT_STREAM_RESOLUTION = "1920x1080";
  private static final String DEFAULT_STREAM_NAME = "live-stream";
  private static final String DEFAULT_DOWNLOAD_SOURCE_FLAVORS = "dublincore/*";
  private static final String DEFAULT_LIVE_TARGET_FLAVORS = "presenter/delivery";

  /** Variables that can be replaced in stream name */
  public static final String REPLACE_ID = "id";
  public static final String REPLACE_FLAVOR = "flavor";
  public static final String REPLACE_CA_NAME = "caName";
  public static final String REPLACE_RESOLUTION = "resolution";

  public static final String LIVE_STREAM_NAME = "live.stream-name";
  public static final String LIVE_STREAM_MIME_TYPE = "live.mime-type";
  public static final String LIVE_STREAM_RESOLUTION = "live.resolution";
  public static final String LIVE_DOWNLOAD_SOURCE_FLAVORS = "live.download-source-flavors";
  public static final String LIVE_TARGET_FLAVORS = "live.target-flavors";

  /** Configuration properties id */
  public static final String LIVE_STREAMING_URL_PROPERTY = "org.opencastproject.live.streaming.url";
  private static final String SERVER_URL_PROPERTY = "org.opencastproject.server.url";
  private static final String ENGAGE_URL_PROPERTY = "org.opencastproject.engage.ui.url";

  /** How long to wait in between trying to get the media package lock (in milliseconds) */
  private static final int WAIT_FOR_MP_LOCK = 60000; // 1 minute

  /**
   * Logger
   */
  private static final Log logger = new Log(LoggerFactory.getLogger(LiveServiceImpl.class));

  /** The admin system account */
  private String systemAccount;

  /** The live streaming url */
  private String liveStreamingUrl;
  /** The server url */
  private URL serverUrl;

  private String streamName;
  private String streamMimeType;
  private String[] streamResolution;
  // Download element selector
  private SimpleElementSelector downloadElementSelector;
  private MediaPackageElementFlavor[] liveFlavors;

  /**
   * Lock management queue. Useful when there are more than one update request for the same media package.
   */
  private final CopyOnWriteArrayList<LockControl> mpLockQueue = new CopyOnWriteArrayList<LockControl>();

  /** The search service */
  private SearchService searchService;
  /** The download distribution service */
  private DownloadDistributionService downloadDistributionService;
  /** The service registry */
  private ServiceRegistry serviceRegistry;
  /** The workflow service */
  private WorkflowService workflowService;
  /** The workspace */
  private Workspace workspace;
  /** The dublin core service */
  private DublinCoreCatalogService dublinCoreService;
  /** The security service */
  private SecurityService securityService;
  /** The media package lock service */
  private MediaPackageLockManager lockManager;

  /**
   * OSGi callback on component activation.
   *
   * @param context
   *          the component context
   */
  protected void activate(ComponentContext context) {
    BundleContext bundleContext = context.getBundleContext();

    systemAccount = bundleContext.getProperty("org.opencastproject.security.digest.user");

    liveStreamingUrl = StringUtils.trimToNull(bundleContext.getProperty(LIVE_STREAMING_URL_PROPERTY));
    if (liveStreamingUrl == null)
      logger.warn("Live streaming url was not set (%s)", LIVE_STREAMING_URL_PROPERTY);
    else
      logger.debug("Live streaming server url is %s", liveStreamingUrl);

    if (!StringUtils.isBlank(bundleContext.getProperty(SERVER_URL_PROPERTY))) {
      serverUrl = UrlSupport.url(bundleContext.getProperty(SERVER_URL_PROPERTY));
      logger.debug("Server url is %s", serverUrl.toString());
    } else
      logger.warn("Server url was not set (%s)", SERVER_URL_PROPERTY);

    @SuppressWarnings("rawtypes")
    Dictionary properties = context.getProperties();
    if (!StringUtils.isBlank((String) properties.get(LIVE_STREAM_NAME))) {
      streamName = StringUtils.trimToEmpty((String) properties.get(LIVE_STREAM_NAME));
    } else {
      streamName = DEFAULT_STREAM_NAME;
    }

    if (!StringUtils.isBlank((String) properties.get(LIVE_STREAM_MIME_TYPE))) {
      streamMimeType = StringUtils.trimToEmpty((String) properties.get(LIVE_STREAM_MIME_TYPE));
    } else {
      streamMimeType = DEFAULT_STREAM_MIME_TYPE;
    }

    String resolution = null;
    if (!StringUtils.isBlank((String) properties.get(LIVE_STREAM_RESOLUTION))) {
      resolution = StringUtils.trimToEmpty((String) properties.get(LIVE_STREAM_RESOLUTION));
    } else {
      resolution = DEFAULT_STREAM_RESOLUTION;
    }
    streamResolution = resolution.split(",");

    String downloadSourceFlavors = null;
    if (!StringUtils.isBlank((String) properties.get(LIVE_DOWNLOAD_SOURCE_FLAVORS))) {
      downloadSourceFlavors = StringUtils.trimToEmpty((String) properties.get(LIVE_DOWNLOAD_SOURCE_FLAVORS));
    } else {
      downloadSourceFlavors = DEFAULT_DOWNLOAD_SOURCE_FLAVORS;
    }
    String[] sourceDownloadFlavors = StringUtils.split(downloadSourceFlavors, ",");
    // Configure the download element selector
    downloadElementSelector = new SimpleElementSelector();
    for (String flavor : sourceDownloadFlavors) {
      downloadElementSelector.addFlavor(MediaPackageElementFlavor.parseFlavor(flavor));
    }

    String flavors = null;
    if (!StringUtils.isBlank((String) properties.get(LIVE_TARGET_FLAVORS))) {
      flavors = StringUtils.trimToEmpty((String) properties.get(LIVE_TARGET_FLAVORS));
    } else {
      flavors = DEFAULT_LIVE_TARGET_FLAVORS;
    }
    String[] flavorArray = StringUtils.split(flavors, ",");
    liveFlavors = new MediaPackageElementFlavor[flavorArray.length];
    int i = 0;
    for (String f : flavorArray)
      liveFlavors[i++] = MediaPackageElementFlavor.parseFlavor(f);

    logger.debug(
            "Configured live stream name: %s, mime type: %s, resolution: %s, source download flavors: %s, target flavors: %s",
            streamName, streamMimeType, streamResolution, downloadSourceFlavors, flavors);

    // Re-attach live workflow state listeners
    addLiveWorkflowStateListeners();
  }

  /**
   * Add live workflow state listeners to workflows that have captures in the future.
   */
  private void addLiveWorkflowStateListeners() {
    // Look for workflows that have start date in the future (i.e. capture date/time) and
    // that are in progress (running the scheduler wf or paused i.e. upcoming).
    WorkflowQuery q = new WorkflowQuery();
    q.withState(WorkflowState.FAILING);
    q.withState(WorkflowState.RUNNING);
    q.withState(WorkflowState.PAUSED);
    q.withDateAfter(new Date());
    int page = 0; // Use count default 20 per page
    int total = 0;
    WorkflowSet set = null;

    try {
      DefaultOrganization defaultOrg = new DefaultOrganization();
      securityService.setOrganization(defaultOrg);
      securityService.setUser(SecurityUtil.createSystemUser(systemAccount, defaultOrg));

      // For each workflow returned, check if they have published a LIVE media
      // package to the search index
      do { // Query results come in pages
        q.withStartPage(page);
        set = workflowService.getWorkflowInstancesForAdministrativeRead(q);

        for (WorkflowInstance wf : set.getItems()) {
          MediaPackage mp = wf.getMediaPackage();
          Publication[] pubs = mp.getPublications();
          for (int i = 0; pubs != null && i < pubs.length; i++) {
            if (LiveService.CHANNEL_ID.equals(pubs[i].getChannel())) {
              // Add listener to workflow so that media package can be retracted if
              // workflow stops/fails
              workflowService.addWorkflowListener(new LiveWorkflowStateListener(workflowService, this, wf));
              logger.info("Added live workflow listener on wf %d.", wf.getId());
            }
          }
        }

        total += set.size();
        logger.debug("Processed {} workflows in page {}", set.size(), page);
        page++;
      } while (total < set.getTotalCount());
    } catch (Exception ex) {
      logger.warn(ex, "Could not add live workflow listeners.");
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void publishMediaPackage(WorkflowInstance workflow) throws LiveException {
    String mediaPackageId = null;
    try {
      MediaPackage mediaPackage = workflow.getMediaPackage();

      mediaPackageId = mediaPackage.getIdentifier().toString();
      getLock(mediaPackageId);

      // Schedule jobs for distribution and publishing
      distributeAndPublish(workflow);

      // Add live channel to original media package
      addLivePublicationChannel(mediaPackage, workflow);
      logger.debug("Publishing of LIVE mediapackage %s completed", mediaPackage);

      // Add listener to workflow
      workflowService.addWorkflowListener(new LiveWorkflowStateListener(workflowService, this, workflow));
      logger.debug("Added live workflow listener on wf %d.", workflow.getId());
    } catch (Exception e) {
      if (e instanceof LiveException)
        throw (LiveException) e;
      else
        throw new LiveException(e);
    } finally {
      releaseLock(mediaPackageId);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void updateMediaPackage(WorkflowInstance workflow) throws LiveException {
    String mediaPackageId = null;
    try {
      MediaPackage mediaPackage = workflow.getMediaPackage();

      mediaPackageId = mediaPackage.getIdentifier().toString();
      getLock(mediaPackageId);

      logger.debug("Updating LIVE mediapackage %s", mediaPackage);

      // Get the media package from the search index if there
      MediaPackage previousSearchMediaPackage = getMediaPackageFromSearch(mediaPackageId);

      if (previousSearchMediaPackage != null && !isLive(previousSearchMediaPackage)) {
        throw new LiveException("Media package in search index is NOT live: "
                + previousSearchMediaPackage.getIdentifier());
      }

      // Redistribute and republish
      distributeAndPublish(workflow);

      // If no media package in search index previously, there are no files to retract
      if (previousSearchMediaPackage == null) {
        return;
      }

      // Get the newly distributed media package from the search index
      MediaPackage currentSearchMediaPackage = getMediaPackageFromSearch(mediaPackageId);

      // Now can retract elements/catalogs from previous publish. Before creating a retraction
      // job, check if the element url is still used by the newly published media package.
      List<Job> jobs = new ArrayList<Job>();
      for (MediaPackageElement element : previousSearchMediaPackage.getElements()) {
        // We don't retract tracks because they are just live links
        if (!Track.TYPE.equals(element.getElementType())) {
          boolean canBeDeleted = true;
          for (MediaPackageElement newElement : currentSearchMediaPackage.getElements()) {
            if (element.getURI().equals(newElement.getURI())) {
              logger.debug(
                      "Not retracting element %s with URI %s from download distribution because it is still used by updated live media package",
                      element.getIdentifier(), element.getURI());
              canBeDeleted = false;
              break;
            }
          }
          if (canBeDeleted) {
            Job retractDownloadJob = downloadDistributionService.retract(CHANNEL_ID, previousSearchMediaPackage,
                    element.getIdentifier());
            jobs.add(retractDownloadJob);
          }
        }
      }

      if (jobs.size() > 0) {
        // Wait for retraction to finish
        if (!waitForStatus(jobs.toArray(new Job[jobs.size()])).isSuccess())
          logger.warn("One of the download retract jobs did not complete successfully");
        else
          logger.debug("Retraction of previously published elements complete");
      }

      // Current workflow is unchanged, publication channel unchanged
    } catch (Exception e) {
      if (e instanceof LiveException)
        throw (LiveException) e;
      else
        throw new LiveException(e);
    } finally {
      releaseLock(mediaPackageId);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void retractMediaPackage(WorkflowInstance workflow) throws LiveException {
    String mediaPackageId = null;
    try {
      MediaPackage mediaPackage = workflow.getMediaPackage();

      mediaPackageId = mediaPackage.getIdentifier().toString();
      getLock(mediaPackageId);

      // Retract the media package from the search index if there and if it is a LIVE media package
      MediaPackage searchMediaPackage = getMediaPackageFromSearch(mediaPackageId);

      if (searchMediaPackage == null || !isLive(searchMediaPackage)) {
        // Remove publication element if there
        removeLivePublicationChannel(mediaPackage);
        return;
      }

      logger.info("Retracting live media package %s from live distribution channel", searchMediaPackage);
      List<Job> jobs = new ArrayList<Job>();
      for (MediaPackageElement element : searchMediaPackage.getElements()) {
        // We don't retract tracks because they are just live links
        if (!Track.TYPE.equals(element.getElementType())) {
          Job retractDownloadJob = downloadDistributionService.retract(CHANNEL_ID, searchMediaPackage,
                  element.getIdentifier());
          jobs.add(retractDownloadJob);
        }
      }
      if (jobs.size() > 0) {
        // Wait for retraction to finish
        if (!waitForStatus(jobs.toArray(new Job[jobs.size()])).isSuccess())
          logger.warn("One of the download retract job did not complete successfully");
        else
          logger.debug("Retraction download complete");
      }

      logger.info("Removing live media package %s from the search index", mediaPackage);
      Job deleteFromSearch = searchService.delete(mediaPackage.getIdentifier().toString());
      if (!waitForStatus(deleteFromSearch).isSuccess())
        throw new LiveException("Removing live media package from search did not complete successfully");

      logger.debug("Remove live media package from search index complete");

      // Remove publication element
      removeLivePublicationChannel(mediaPackage);
    } catch (Throwable t) {
      throw new LiveException(t);
    } finally {
      releaseLock(mediaPackageId);
    }
  }

  protected void distributeAndPublish(WorkflowInstance workflow) throws LiveException {
    try {
      MediaPackage mediaPackage = workflow.getMediaPackage();

      // First make sure the episode dublin core has dcCreated set so that the pub listing page can
      // display it (DCE-specific)
      updateDublinCore(mediaPackage);

      // Select the appropriate elements for download
      Collection<MediaPackageElement> downloadElements = downloadElementSelector.select(mediaPackage, false);
      // Look for elements matching the tag
      Set<String> downloadElementIds = new HashSet<String>();
      for (MediaPackageElement elem : downloadElements) {
        downloadElementIds.add(elem.getIdentifier());
      }
      // Also distribute the security configuration
      // -----
      // This was removed in the meantime by a fix for MH-8515, but could
      // now be used again.
      // -----
      Attachment[] securityAttachments = mediaPackage.getAttachments(MediaPackageElements.XACML_POLICY);
      if (securityAttachments != null && securityAttachments.length > 0) {
        for (Attachment a : securityAttachments) {
          downloadElementIds.add(a.getIdentifier());
        }
      }
      // Create jobs for download distribution
      List<Job> jobs = new ArrayList<Job>();
      for (String elementId : downloadElementIds) {
        Job job = downloadDistributionService.distribute(CHANNEL_ID, mediaPackage, elementId, true);
        if (job != null)
          jobs.add(job);
      }
      // At least the episode catalog must be distributed!
      if (jobs.size() < 1) {
        logger.info("No mediapackage element was found for live distribution to engage");
        throw new LiveException("Cannot distribute the live media package without the episode dublincore catalog");
      }
      // Wait until all distribution jobs have returned
      if (!waitForStatus(jobs.toArray(new Job[jobs.size()])).isSuccess())
        throw new LiveException("One of the distribution jobs did not complete successfully");
      logger.debug("Download distribute of live mediapackage %s completed", mediaPackage);

      // Build live tracks
      Track[] liveTracks = createLiveTracks(workflow, mediaPackage.getDuration());
      // Create media package to be published to search index: catalogs + live tracks
      MediaPackage mediaPackageForSearch = buildMediaPackageForSearchIndex(mediaPackage, jobs, downloadElementIds,
              liveTracks);
      // Adding media package to the search index
      logger.info("Publishing LIVE media package %s to search index", mediaPackageForSearch);
      Job publishJob = searchService.add(mediaPackageForSearch);
      if (!waitForStatus(publishJob).isSuccess()) {
        throw new WorkflowOperationException("Mediapackage " + mediaPackageForSearch.getIdentifier()
                + " could not be published");
      }
    } catch (Exception e) {
      if (e instanceof LiveException)
        throw (LiveException) e;
      else
        throw new LiveException(e);
    }
  }

  /**
   * Retrieves the media package from the search index.
   *
   * @param mediaPackageId
   *          the media package id
   * @return the media package in the search index or null if not there
   * @throws LiveException
   *           if found many media packages with the same id
   */
  private MediaPackage getMediaPackageFromSearch(String mediaPackageId) throws LiveException {
    // Look for the media package in the search index
    SearchQuery query = new SearchQuery().withId(mediaPackageId);
    SearchResult result = searchService.getByQuery(query);
    if (result.size() == 0) {
      logger.info("The search service doesn't know live mediapackage %s", mediaPackageId);
      return null;
    } else if (result.size() > 1) {
      logger.warn("More than one live mediapackage with id %s returned from search service", mediaPackageId);
      throw new LiveException("More than one live mediapackage with id " + mediaPackageId + " found");
    }
    return result.getItems()[0].getMediaPackage();
  }

  /**
   * Populates the dcCreated field in the episode catalog. This field is used by the DCE pub listing page.
   *
   * @param mediaPackage
   */
  private void updateDublinCore(MediaPackage mediaPackage) {
    // #DCE-specific: we use the dcCreated in the pub listing page to sort recordings
    // This logic was based on the inspect WOH

    // Complete episode dublin core catalog (if available)
    Catalog[] dcCatalogs = mediaPackage.getCatalogs(MediaPackageElements.EPISODE,
            MediaPackageReferenceImpl.ANY_MEDIAPACKAGE);
    if (dcCatalogs.length > 0) {
      DublinCoreCatalog dublinCore = loadDublinCoreCatalog(dcCatalogs[0]);
      if (dublinCore == null) {
        logger.warn("Could not update episode dublin core");
        return;
      }

      // Date created
      if (mediaPackage.getDate() != null && !dublinCore.hasValue(DublinCore.PROPERTY_CREATED)) {
        DublinCoreValue date = EncodingSchemeUtils.encodeDate(mediaPackage.getDate(), Precision.Minute);
        dublinCore.set(DublinCore.PROPERTY_CREATED, date);
        logger.debug("Setting dc:created to '{}'", date.getValue());

        // Serialize changed dublin core
        InputStream in = null;
        try {
          in = dublinCoreService.serialize(dublinCore);
          String mpId = mediaPackage.getIdentifier().toString();
          String elementId = dcCatalogs[0].getIdentifier();
          String fileName = FilenameUtils.getName(dcCatalogs[0].getURI().getPath());
          workspace.put(mpId, elementId, fileName, in);
          dcCatalogs[0].setURI(workspace.getURI(mpId, elementId, fileName));
        } catch (IOException iox) {
          logger.warn("Could not update episode dublin core file");
        } finally {
          IOUtils.closeQuietly(in);
        }
      }
    }
  }

  /**
   * Loads a dublin core catalog from a mediapackage's catalog reference
   *
   * @param catalog
   *          the mediapackage's reference to this catalog
   * @return he dublin core
   * @throws IOException
   *           if there is a problem loading or parsing the dublin core object
   */
  private DublinCoreCatalog loadDublinCoreCatalog(Catalog catalog) {
    InputStream in = null;
    try {
      File f = workspace.get(catalog.getURI());
      in = new FileInputStream(f);
      return dublinCoreService.load(in);
    } catch (NotFoundException e) {
      logger.warn(e, "Could not load episode dublin core");
    } catch (IOException e) {
      logger.warn(e, "Could not load episode dublin core");
    } finally {
      IOUtils.closeQuietly(in);
    }
    return null;
  }

  Track[] createLiveTracks(WorkflowInstance wf, long duration) throws LiveException {

    try {
      Track[] tracks = new Track[liveFlavors.length * streamResolution.length];
      int i = 0;
      for (MediaPackageElementFlavor flavor : liveFlavors) {
        for (int j = 0; j < streamResolution.length; j++) {
          logger.info("Creating live track element of flavor %s and resolution %s", flavor, streamResolution[j]);
          tracks[i++] = buildStreamingTrack(wf, streamName, flavor, streamMimeType, streamResolution[j], duration);
        }
      }
      return tracks;
    } catch (URISyntaxException e) {
      throw new LiveException(e);
    }
  }

  private Track buildStreamingTrack(WorkflowInstance wf, String fileName, MediaPackageElementFlavor flavor,
          String mimeType, String resolution, long duration) throws URISyntaxException, LiveException {

    String replaced = replaceVariables(wf, flavor, resolution);
    if (liveStreamingUrl == null)
      throw new LiveException(String.format("%s must be set in configuration", LIVE_STREAMING_URL_PROPERTY));
    URI uri = new URI(UrlSupport.concat(liveStreamingUrl.toString(), replaced));

    MediaPackageElementBuilder elementBuilder = MediaPackageElementBuilderFactory.newInstance().newElementBuilder();
    MediaPackageElement element = elementBuilder.elementFromURI(uri, MediaPackageElement.Type.Track, flavor);
    TrackImpl track = (TrackImpl) element;

    // Set duration and mime type
    track.setDuration(duration);
    track.setLive(true);
    track.setMimeType(MimeTypes.parseMimeType(mimeType));

    VideoStreamImpl video = new VideoStreamImpl("video-" + flavor.getType() + "-" + flavor.getSubtype());
    // Set video resolution
    String[] dimensions = resolution.split("x");
    video.setFrameWidth(Integer.parseInt(dimensions[0]));
    video.setFrameHeight(Integer.parseInt(dimensions[1]));

    track.addStream(video);

    return track;
  }

  /**
   * Replaces variables in the live stream name. Currently, this is only prepared to handle the following: #{id} = media
   * package id #{flavor} = type-subtype of flavor #{caName} = capture agent name
   *
   * @param wf
   *          The workflow instance
   * @param flavor
   *          The media package flavor for that live track
   *
   * @return The live stream name with variables replaced
   */
  String replaceVariables(WorkflowInstance wf, MediaPackageElementFlavor flavor, String resolution) {

    MediaPackage mp = wf.getMediaPackage();

    // Substitution pattern: any string in the form #{name}, where 'name' has only word characters: [a-zA-Z_0-9].
    final Pattern pat = Pattern.compile("#\\{(\\w+)\\}");

    Matcher matcher = pat.matcher(streamName);
    StringBuffer sb = new StringBuffer();
    while (matcher.find()) {
      if (matcher.group(1).equals(REPLACE_ID)) {
        matcher.appendReplacement(sb, mp.getIdentifier().compact());
      } else if (matcher.group(1).equals(REPLACE_FLAVOR)) {
        matcher.appendReplacement(sb, flavor.getType() + "-" + flavor.getSubtype());
      } else if (matcher.group(1).equals(REPLACE_CA_NAME)) {
        // Taking the easy route to find the capture agent name...
        matcher.appendReplacement(sb, wf.getConfiguration("schedule.location"));
      } else if (matcher.group(1).equals(REPLACE_RESOLUTION)) {
        // Taking the easy route to find the capture agent name...
        matcher.appendReplacement(sb, resolution);
      } // else will not replace
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  /**
   * Returns a mediapackage that only contains elements that are marked for live distribution.
   *
   * @param current
   *          the current media package
   * @param jobs
   *          the distribution jobs
   * @param downloadElementIds
   *          identifiers for elements that have been distributed to downloads
   * @param liveTracks
   *          the live tracks to be added to the media package
   *
   * @return the new mediapackage
   */
  MediaPackage buildMediaPackageForSearchIndex(MediaPackage current, List<Job> jobs,
          Set<String> downloadElementIds, Track[] liveTracks) throws MediaPackageException, NotFoundException,
          ServiceRegistryException, WorkflowOperationException {
    // Work on a copy of the media package
    MediaPackage mp = (MediaPackage) current.clone();

    // All the jobs have passed, let's update the media package with references to the distributed elements
    List<String> elementsToPublish = new ArrayList<String>();
    Map<String, String> distributedElementIds = new HashMap<String, String>();

    for (Job entry : jobs) {
      Job job = serviceRegistry.getJob(entry.getId());
      String sourceElementId = job.getArguments().get(2);
      MediaPackageElement sourceElement = mp.getElementById(sourceElementId);

      // If there is no payload, then the item has not been distributed.
      if (job.getPayload() == null)
        continue;

      MediaPackageElement distributedElement = null;
      try {
        distributedElement = MediaPackageElementParser.getFromXml(job.getPayload());
      } catch (MediaPackageException e) {
        throw new WorkflowOperationException(e);
      }

      // If the job finished successfully, but returned no new element, the channel simply doesn't support this
      // kind of element. So we just keep on looping.
      if (distributedElement == null)
        continue;

      // Make sure the media package is prompted to create a new identifier for this element
      distributedElement.setIdentifier(null);

      // Copy references from the source elements to the distributed elements
      MediaPackageReference ref = sourceElement.getReference();
      if (ref != null && mp.getElementByReference(ref) != null) {
        MediaPackageReference newReference = (MediaPackageReference) ref.clone();
        distributedElement.setReference(newReference);
      }

      // Add the new element to the mediapackage
      mp.add(distributedElement);
      elementsToPublish.add(distributedElement.getIdentifier());
      distributedElementIds.put(sourceElementId, distributedElement.getIdentifier());
    }

    // Add a live track for each live flavor passed to the media package
    for (Track track : liveTracks) {
      mp.add(track);
      logger.info("Added live track element %s to mediapackage %s", track.getIdentifier(), mp);
      elementsToPublish.add(track.getIdentifier());
    }

    // Mark everything that is set for removal
    List<MediaPackageElement> removals = new ArrayList<MediaPackageElement>();
    for (MediaPackageElement element : mp.getElements()) {
      if (!elementsToPublish.contains(element.getIdentifier())) {
        removals.add(element);
      }
    }

    // Translate references to the distributed artifacts
    for (MediaPackageElement element : mp.getElements()) {

      if (removals.contains(element))
        continue;

      // Is the element referencing anything?
      MediaPackageReference reference = element.getReference();
      if (reference == null)
        continue;

      // See if the element has been distributed
      String distributedElementId = distributedElementIds.get(reference.getIdentifier());
      if (distributedElementId == null)
        continue;

      MediaPackageReference translatedReference = new MediaPackageReferenceImpl(mp.getElementById(distributedElementId));
      if (reference.getProperties() != null) {
        translatedReference.getProperties().putAll(reference.getProperties());
      }

      // Set the new reference
      element.setReference(translatedReference);

    }

    // Remove everything we don't want to add to publish
    for (MediaPackageElement element : removals) {
      mp.remove(element);
    }
    return mp;
  }

  void addLivePublicationChannel(MediaPackage mediaPackage, WorkflowInstance workflow) throws LiveException {
    // Add publication element
    logger.info("Adding live channel publication element to media package %s", mediaPackage);
    URL engageBaseUrl = null;
    String engageUrlString = StringUtils
            .trimToNull(workflow.getOrganization().getProperties().get(ENGAGE_URL_PROPERTY));
    try {
      if (engageUrlString != null) {
        engageBaseUrl = new URL(engageUrlString);
      } else {
        engageBaseUrl = serverUrl;
        logger.info(
                "Using 'server.url' as a fallback for the non-existing organization level key '%s' for the publication url",
                ENGAGE_URL_PROPERTY);
      }
      // Create new distribution element
      // #DCE Karen: TODO: Retrieve local path to watch.html from config file
      // String defaultMhPathToWatch = "/engage/ui/";
      String localPathToWatch = "/engage/player/";
      URI engageUri = URIUtils.resolve(engageBaseUrl.toURI(), localPathToWatch + "watch.html?id="
              + mediaPackage.getIdentifier().compact());
      // #DCE end

      Publication publicationElement = PublicationImpl.publication(UUID.randomUUID().toString(), CHANNEL_ID, engageUri,
              MimeTypes.parseMimeType("text/html"));
      mediaPackage.add(publicationElement);
    } catch (MalformedURLException e) {
      logger.error("%s is malformed: %s", ENGAGE_URL_PROPERTY, engageUrlString);
      throw new LiveException(e);
    } catch (URISyntaxException e) {
      logger.error("URI is malformed: %s", engageUrlString);
      throw new LiveException(e);
    }
  }

  void removeLivePublicationChannel(MediaPackage mediaPackage) {
    // Remove publication element
    logger.info("Removing live channel publication element from media package %s", mediaPackage);
    Publication[] publications = mediaPackage.getPublications();
    if (publications != null) {
      for (Publication publication : publications) {
        if (CHANNEL_ID.equals(publication.getChannel())) {
          mediaPackage.remove(publication);
          logger.debug("Remove live channel publication element '%s' complete", publication.getIdentifier());
        }
      }
    }
  }

  private boolean isLive(MediaPackage mp) {
    Track[] tracks = mp.getTracks();
    if (tracks != null)
      for (Track track : tracks)
        if (track.isLive())
          return true;

    return false;
  }

  private void getLock(String mediaPackageId) throws MediaPackageLockException {
    // Before requesting lock, puts request in queue
    LockControl lockControl = new LockControl(mediaPackageId, Thread.currentThread().getId(), -1);
    mpLockQueue.add(lockControl);
    // Request lock
    int lockId = lockManager.lock(mediaPackageId, this);
    if (lockId > -1) {
      // Got it right away!
      lockControl.setLockId(lockId);
    } else {
      // Will have to wait for the lock
      while (lockControl.getLockId() == -1) {
        try {
          logger.info("Waiting for the lock on media package %s for %d", mediaPackageId, Thread.currentThread().getId());
          Thread.sleep(WAIT_FOR_MP_LOCK);
        } catch (InterruptedException e) {
        }
      }
      lockId = lockControl.getLockId();
    }
    logger.info("Got lock %d for media package %s from thread %d", lockId, mediaPackageId, Thread.currentThread()
            .getId());
  }

  private void releaseLock(String mediaPackageId) {
    int lockId = -1;
    LockControl lockControl = null;

    for (LockControl element : mpLockQueue) {
      if (element.getMediaPackageId().equals(mediaPackageId)
              && element.getThreadId().equals(Thread.currentThread().getId())) {
        lockId = element.getLockId();
        lockControl = element;
        break;
      }
    }

    try {
      mpLockQueue.remove(lockControl);
      lockManager.release(mediaPackageId, lockId);
      logger.info("Released lock %d for media package %s from thread %d", lockId, mediaPackageId, Thread.currentThread()
              .getId());
    } catch (MediaPackageLockException e) {
      logger.warn(e, String.format("Exception when releasing lock %d on media package %s", lockControl.getLockId(),
              mediaPackageId));
    }
  }

  @Override
  public boolean lockGranted(String mediaPackageId, int lockId) {
    LockControl firstWaitingMp = null;

    for (LockControl element : mpLockQueue) {
      if (mediaPackageId.equals(element.getMediaPackageId())) {
        firstWaitingMp = element;
        break;
      }
    }

    if (firstWaitingMp != null) {
      firstWaitingMp.setLockId(lockId);
      logger.info("Lock for media package %s was granted to %d", mediaPackageId, firstWaitingMp.getThreadId());
      return true; // Lock accepted
    } else {
      // This should never happen...
      logger.warn("Lock for media package %s was granted, but there are no threads waiting for it", mediaPackageId);
      return false; // Nobody waiting for this lock
    }
  }

  public void setSearchService(SearchService service) {
    this.searchService = service;
  }

  public void setDownloadDistributionService(DownloadDistributionService service) {
    this.downloadDistributionService = service;
  }

  public void setServiceRegistry(ServiceRegistry service) {
    this.serviceRegistry = service;
  }

  public void setWorkflowService(WorkflowService service) {
    this.workflowService = service;
  }

  public void setSecurityService(SecurityService service) {
    this.securityService = service;
  }

  public void setDublinCoreService(DublinCoreCatalogService service) {
    this.dublinCoreService = service;
  }

  public void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
  }

  public void setMediaPackageLockManager(MediaPackageLockManager lockManager) {
    this.lockManager = lockManager;
  }

  private JobBarrier.Result waitForStatus(Job... jobs) throws IllegalStateException, IllegalArgumentException {
    if (serviceRegistry == null)
      throw new IllegalStateException("Can't wait for job status without providing a service registry first");
    JobBarrier barrier = new JobBarrier(serviceRegistry, jobs);
    return barrier.waitForJobs();
  }

  // Used by tests
  void setStreamingUrl(String streamingUrl) {
    this.liveStreamingUrl = streamingUrl;
  }

  class LockControl {
    private final String mediaPackageId;
    private final Long threadId;
    private int lockId;

    public LockControl(String mediaPackageId, Long threadId, int lockId) {
      this.mediaPackageId = mediaPackageId;
      this.threadId = threadId;
      this.lockId = lockId;
    }

    public void setLockId(int lockId) {
      this.lockId = lockId;
    }

    public String getMediaPackageId() {
      return mediaPackageId;
    }

    public Long getThreadId() {
      return threadId;
    }

    public int getLockId() {
      return lockId;
    }
  }
}
