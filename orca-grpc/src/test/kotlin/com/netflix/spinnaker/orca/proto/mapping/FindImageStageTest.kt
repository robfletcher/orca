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

import com.netflix.spinnaker.assertj.asMap
import com.netflix.spinnaker.assertj.softly
import com.netflix.spinnaker.orca.proto.execution.FindImageStageSpec
import com.netflix.spinnaker.orca.proto.execution.FindImageStageSpec.SelectionStrategy
import com.netflix.spinnaker.orca.proto.execution.StageSpec
import com.netflix.spinnaker.orca.proto.pack
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

class FindImageStageTest : Spek({

  val findImageStageSpec = FindImageStageSpec.newBuilder().apply {
    cloudProvider = "aws"
    monikerBuilder.apply {
      app = "covfefe"
      stack = "stack"
      detail = "detail"
      cluster = "covfefe-stack-detail"
    }.build()
    credentials = "test"
    onlyEnabled = true
    selectionStrategy = SelectionStrategy.LARGEST
    addRegions("us-west-2")
    addRegions("us-east-1")
  }.build()

  val stageSpec = StageSpec.newBuilder().apply {
    name = "find dat ting"
    ref = "3"
    addDependsOn("1")
    addDependsOn("2")
    spec = findImageStageSpec.pack()
  }.build()

  describe("unpacking a find image stage") {
    val stage = stageSpec.unpack()

    it("should unpack common stage values") {
      stage.apply {
        softly {
          assertThat(name).isEqualTo(stageSpec.name)
          assertThat(type).isEqualTo("findImage")
          assertThat(refId).isEqualTo(stageSpec.ref)
          assertThat(requisiteStageRefIds).containsExactlyInAnyOrderElementsOf(stageSpec.dependsOnList)
        }
      }
    }

    it("should unpack a moniker-compatible cluster name") {
      softly {
        assertThat(stage.context)
          .hasEntrySatisfying("moniker") {
            assertThat(it)
              .asMap()
              .containsEntry("app", findImageStageSpec.moniker.app)
              .containsEntry("stack", findImageStageSpec.moniker.stack)
              .containsEntry("detail", findImageStageSpec.moniker.detail)
              .containsEntry("cluster", findImageStageSpec.moniker.cluster)
          }
          .containsEntry("cluster", findImageStageSpec.moniker.cluster)
      }
    }

    it("should unpack cloud provider") {
      softly {
        assertThat(stage.context)
          .containsEntry("cloudProvider", findImageStageSpec.cloudProvider)
          .containsEntry("cloudProviderType", findImageStageSpec.cloudProvider)
      }
    }

    it("should unpack remaining stage details") {
      softly {
        assertThat(stage.context)
          .containsEntry("onlyEnabled", findImageStageSpec.onlyEnabled)
          .containsEntry("selectionStrategy", findImageStageSpec.selectionStrategy.name)
          .containsEntry("regions", findImageStageSpec.regionsList)
      }
    }
  }

})
