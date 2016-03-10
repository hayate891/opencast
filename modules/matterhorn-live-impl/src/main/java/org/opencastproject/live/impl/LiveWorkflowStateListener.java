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

import org.opencastproject.live.api.LiveException;
import org.opencastproject.live.api.LiveService;
import org.opencastproject.util.Log;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowInstance.WorkflowState;
import org.opencastproject.workflow.api.WorkflowListener;
import org.opencastproject.workflow.api.WorkflowService;

import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author rsantos
 *
 *         This class will listen to workflows that have had live media packages published to the search index. When the
 *         workflow state changes to STOPPED or FAILED, it will retract the live media package from the search index.
 */
public class LiveWorkflowStateListener implements WorkflowListener {

  /** Logging utility */
  private static final Log logger = new Log(LoggerFactory.getLogger(LiveWorkflowStateListener.class));

  private final WorkflowService workflowService;
  private final LiveService liveService;

  /* Workflow instance we are listening to */
  private WorkflowInstance workflow = null;

  /* States we are interested in */
  private static final Set<WorkflowInstance.WorkflowState> wfStates = new HashSet<WorkflowInstance.WorkflowState>(
          Arrays.asList(new WorkflowInstance.WorkflowState[] { WorkflowState.FAILED, WorkflowState.STOPPED }));

  public LiveWorkflowStateListener(WorkflowService workflowService, LiveService liveService, WorkflowInstance workflow) {
    super();
    this.workflowService = workflowService;
    this.liveService = liveService;
    this.workflow = workflow;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.workflow.api.WorkflowListener#operationChanged(org.opencastproject.workflow.api.WorkflowInstance)
   */
  @Override
  public void operationChanged(WorkflowInstance workflow) {
    // #DCE MATT-1530 OC decorated logger doesn't implement TRACE, but DEBUG is too high for following log
    //logger.debug("No-op");
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.workflow.api.WorkflowListener#stateChanged(org.opencastproject.workflow.api.WorkflowInstance)
   */
  @Override
  public void stateChanged(WorkflowInstance workflow) {
    // Same workflow instance?
    if (!workflow.equals(this.workflow))
      return;

    logger.debug(String.format("State change on workflow %d to %s.", workflow.getId(), workflow.getState().name()));

    // State we are interested in?
    if (wfStates.contains(workflow.getState())) {
      // Remove listener
      workflowService.removeWorkflowListener(this);
      logger.debug(String.format("Removed live workflow listener on wf %d in state %s.", this.workflow.getId(),
              this.workflow.getState().name()));

      try {
        // Live channel is removed from the media package
        liveService.retractMediaPackage(workflow);
      } catch (LiveException e) {
        logger.warn("Exception occurred when trying to retract live workflow in wf listener: %s", e.toString());
      }
    }
  }
}
