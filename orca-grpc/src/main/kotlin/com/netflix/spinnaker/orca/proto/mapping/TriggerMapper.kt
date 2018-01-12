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

import com.netflix.spinnaker.orca.proto.execution.ManualTrigger
import com.netflix.spinnaker.orca.proto.execution.Trigger
import com.netflix.spinnaker.orca.proto.isA
import com.netflix.spinnaker.orca.proto.unpack

fun Trigger.unpack() =
  mutableMapOf<String, Any>().also { model ->
    model["user"] = user
    model["parameters"] = parameters.unpack()
    model["notifications"] = notificationsList.map { it.unpack() }
    when {
      spec.isA<ManualTrigger>() ->
        spec.unpack<ManualTrigger>().run {
          model["type"] = "manual"
          model["correlationId"] = correlationId
        }
      else ->
        TODO("Trigger type ${spec.typeUrl} is not yet supported")
    }
    return model
  }
