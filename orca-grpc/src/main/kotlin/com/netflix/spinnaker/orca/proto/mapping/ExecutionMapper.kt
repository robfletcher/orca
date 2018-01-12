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

class ExecutionMapper : Mapper<ExecutionRequest, Execution> {

  private val triggerMapper = TriggerMapper()
  private val stageMapper = StageMapper()

  override fun unpack(proto: ExecutionRequest): Execution =
    Execution(PIPELINE, proto.application)
      .also { model ->
        model.pipelineConfigId = proto.id
        model.name = proto.name
        model.trigger.putAll(triggerMapper.unpack(proto.trigger))
        proto.stagesList.forEach { stage ->
          model.stages.add(stageMapper.unpack(stage))
        }
      }
}

