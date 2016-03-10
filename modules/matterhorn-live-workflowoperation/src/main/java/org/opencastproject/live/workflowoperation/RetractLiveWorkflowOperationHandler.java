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

import org.opencastproject.job.api.JobContext;
import org.opencastproject.live.api.LiveException;
import org.opencastproject.live.api.LiveService;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.util.Log;
import org.opencastproject.workflow.api.AbstractWorkflowOperationHandler;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowOperationException;
import org.opencastproject.workflow.api.WorkflowOperationInstance;
import org.opencastproject.workflow.api.WorkflowOperationResult;
import org.opencastproject.workflow.api.WorkflowOperationResult.Action;

import org.osgi.service.component.ComponentContext;
import org.slf4j.LoggerFactory;

/**
 * The workflow operation for retracting a LIVE media package
 */
public class RetractLiveWorkflowOperationHandler extends AbstractWorkflowOperationHandler {

  /** The logging facility */
  private static final Log logger = new Log(LoggerFactory.getLogger(RetractLiveWorkflowOperationHandler.class));

  /** The live service */
  private LiveService liveService = null;

  @Override
  protected void activate(ComponentContext cc) {
    super.activate(cc);
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.workflow.api.WorkflowOperationHandler#start(org.opencastproject.workflow.api.WorkflowInstance,
   *      JobContext)
   */
  @Override
  public WorkflowOperationResult start(final WorkflowInstance workflowInstance, JobContext context)
          throws WorkflowOperationException {
    logger.debug("Running retract live publication workflow operation");

    MediaPackage mediaPackage = workflowInstance.getMediaPackage();
    WorkflowOperationInstance op = workflowInstance.getCurrentOperation();

    try {
      // Live channel is removed from the media package
      liveService.retractMediaPackage(workflowInstance);
    } catch (LiveException e) {
      logger.warn("Exception occurred when trying to retract live workflow: %s", e.toString());
      throw new WorkflowOperationException("Live media package " + mediaPackage + " could not be retracted", e);
    }

    logger.debug("Retracting of LIVE mediapackage %s completed", mediaPackage);
    return createResult(mediaPackage, Action.CONTINUE);
  }

  /**
   * Callback for declarative services configuration that will introduce us to the search service. Implementation
   * assumes that the reference is configured as being static.
   *
   * @param liveService
   *          an instance of the live service
   */
  protected void setLiveService(LiveService service) {
    this.liveService = service;
  }

}