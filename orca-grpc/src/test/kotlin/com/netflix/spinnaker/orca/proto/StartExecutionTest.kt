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

package com.netflix.spinnaker.orca.proto

import com.google.protobuf.Duration
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasSize
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.should.shouldMatch
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.proto.execution.ExecutionRequest
import com.netflix.spinnaker.orca.proto.execution.ExecutionResponse
import com.netflix.spinnaker.orca.proto.execution.ManualTrigger
import com.netflix.spinnaker.orca.proto.execution.WaitStage
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import io.grpc.internal.testing.StreamRecorder
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import java.util.*

class StartExecutionTest : Spek({

  val launcher: ExecutionLauncher = mock()
  val service = ExecutionService(launcher)

  describe("starting an execution") {

    describe("with a single stage") {

      val request = ExecutionRequest.newBuilder()
        .setApplication("orca")
        .setName("Test Pipeline")
        .setId(UUID.randomUUID().toString())
        .addStages(WaitStage.newBuilder()
          .setName("wait")
          .setRef("1")
          .setWaitTime(Duration.newBuilder().setSeconds(30).build())
          .build()
          .pack()
        )
        .setTrigger(ManualTrigger.newBuilder()
          .setUser("fzlem@netflix.com")
//          .putParameters("foo", Any.pack("bar"))
          .build()
          .pack()
        )
        .build()

      val response = StreamRecorder.create<ExecutionResponse>()

      service.start(request, response)

      it("starts a pipeline") {
        argumentCaptor<Execution>().apply {
          verify(launcher).start(capture())
          firstValue.let { pipeline ->
            pipeline.application shouldMatch equalTo(request.application)
            pipeline.name shouldMatch equalTo(request.name)
            pipeline.pipelineConfigId shouldMatch equalTo(request.id)
            pipeline.stages shouldMatch hasSize(equalTo(1))
            pipeline.stages.first().let { stage ->
              stage.type shouldMatch equalTo("wait")
              stage.name shouldMatch equalTo("wait")
              stage.refId shouldMatch equalTo("1")
              stage.requisiteStageRefIds shouldMatch isEmpty
              stage.context["waitTime"].toString() shouldMatch equalTo("30")
            }
          }
        }
      }
    }
  }
})
