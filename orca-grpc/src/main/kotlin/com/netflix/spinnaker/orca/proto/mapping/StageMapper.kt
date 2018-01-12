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

import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.proto.execution.StageSpec
import com.netflix.spinnaker.orca.proto.execution.WaitStageSpec
import com.netflix.spinnaker.orca.proto.isA
import com.netflix.spinnaker.orca.proto.unpack

fun unpack(proto: StageSpec) =
  Stage().also { stage ->
    stage.name = proto.name
    stage.refId = proto.ref
    stage.requisiteStageRefIds = proto.dependsOnList
    stage.context["comments"] = proto.comments
    when {
      proto.spec.isA<WaitStageSpec>() -> proto.spec.unpack<WaitStageSpec>().run {
        stage.type = "wait"
        stage.context["waitTime"] = waitTime.seconds
      }
      else ->
        TODO("Stage type ${proto.spec.typeUrl} is not yet supported")
    }
  }
