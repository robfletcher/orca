/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.proto.mapping

import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.proto.execution.ExecutionRequest

fun ExecutionRequest.unpack(): Execution =
  Execution(PIPELINE, application)
    .also { model ->
      model.pipelineConfigId = id
      model.name = name
      model.trigger.putAll(trigger.unpack())
      stagesList.forEach { stage ->
        model.stages.add(stage.unpack())
      }
    }
