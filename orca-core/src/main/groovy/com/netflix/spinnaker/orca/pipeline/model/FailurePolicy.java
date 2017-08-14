/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline.model;

import com.netflix.spinnaker.orca.ExecutionStatus;

/**
 * The action that should be taken if a stage fails.
 */
public enum FailurePolicy {
  /**
   * Fail the execution immediately, marking this branch as
   * {@link ExecutionStatus#TERMINAL} and canceling any others.
   */
  fail,
  /**
   * Mark this branch as {@link ExecutionStatus#STOPPED} and allow others to
   * continue. The execution may complete successfully unless other branches
   * fail.
   */
  stop,
  /**
   * Mark this branch as {@link ExecutionStatus#STOPPED} and allow others to
   * continue but mark the execution as {@link ExecutionStatus#TERMINAL} at the
   * end.
   */
  failEventual,
  /**
   * Mark this stage as {@link ExecutionStatus#FAILED_CONTINUE} and proceed with
   * the next. Other branches are not affected.
   */
  ignore
}
