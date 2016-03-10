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
package org.opencastproject.live.api;

import org.opencastproject.workflow.api.WorkflowInstance;

/**
 * {@link LiveService} provides creation of live tracks.
 */
public interface LiveService {

  String CHANNEL_ID = "live";

  /**
   * Publish a live media package to the search index. The live publication channel is added to the original media
   * package.
   *
   * @param workflow
   *          The workflow instance
   * @param flavors
   *          List of flavors to generate live tracks for
   */
  void publishMediaPackage(WorkflowInstance workflow) throws LiveException;

  /**
   * Republish a live media package to the search index if already there. If not there or not LIVE, doesn't do anything.
   *
   * @param workflow
   *          The workflow instance
   */
  void updateMediaPackage(WorkflowInstance workflow) throws LiveException;

  /**
   * Retract a live media package from the search index. The live publication channel is removed from the original media
   * package.
   *
   * @param workflow
   *          The workflow instance
   */
  void retractMediaPackage(WorkflowInstance workflow) throws LiveException;
}
