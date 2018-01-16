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
import com.netflix.spinnaker.orca.proto.execution.DeployStageSpec
import com.netflix.spinnaker.orca.proto.execution.DeployStageSpec.ClusterSpec
import com.netflix.spinnaker.orca.proto.execution.DeployStageSpec.ClusterSpec.Ec2ClusterSpec
import com.netflix.spinnaker.orca.proto.execution.FindImageStageSpec
import com.netflix.spinnaker.orca.proto.execution.StageSpec
import com.netflix.spinnaker.orca.proto.execution.WaitStageSpec
import com.netflix.spinnaker.orca.proto.isA
import com.netflix.spinnaker.orca.proto.unpack

fun StageSpec.unpack(): Stage =
  Stage().also { model ->
    model.name = name
    model.refId = ref
    model.requisiteStageRefIds = dependsOnList
    model.context["comments"] = comments
    when {
      spec.isA<WaitStageSpec>() -> spec.unpack<WaitStageSpec>().unpackInto(model)
      spec.isA<DeployStageSpec>() -> spec.unpack<DeployStageSpec>().unpackInto(model)
      spec.isA<FindImageStageSpec>() -> spec.unpack<FindImageStageSpec>().unpackInto(model)
      else ->
        TODO("Stage type ${spec.typeUrl} is not yet supported")
    }
  }

fun WaitStageSpec.unpackInto(model: Stage) {
  model.type = "wait"
  model.context["waitTime"] = waitTime.seconds
}

fun DeployStageSpec.unpackInto(model: Stage) {
  model.type = "deploy"
  model.context["clusters"] = clustersList.map(ClusterSpec::unpack)
}

fun FindImageStageSpec.unpackInto(model: Stage) {
  model.type = "findImage"
  model.context.putAll(this.unpack())
  model.context["cluster"] = moniker.cluster
  model.context["cloudProviderType"] = cloudProvider
}

fun ClusterSpec.unpack(): Map<String, Any> =
  mutableMapOf<String, Any>().also { model ->
    model["cloudProvider"] = cloudProvider
    model["provider"] = cloudProvider

    model["account"] = account
    model["strategy"] = strategy

    model["capacity"] = capacity.unpack()
    model["useSourceCapacity"] = useSourceCapacity
    model["loadBalancers"] = loadBalancersList
    model["securityGroups"] = securityGroupsList

    model["tags"] = tagsMap
    model["entityTags"] = entityTags.unpack()

    when {
      providerSpec.isA<Ec2ClusterSpec>() -> providerSpec.unpack<Ec2ClusterSpec>().unpackInto(model)
      else ->
        TODO("Provider specific cluster type ${providerSpec.typeUrl} is not yet supported")
    }

    model["moniker"] = moniker.unpack()
    model["application"] = moniker.app
    model["stack"] = moniker.stack
    model["freeFormDetails"] = moniker.detail
  }
