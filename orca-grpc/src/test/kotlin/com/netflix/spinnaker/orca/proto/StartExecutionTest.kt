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

import com.google.protobuf.BoolValue
import com.google.protobuf.Duration
import com.google.protobuf.Int32Value
import com.google.protobuf.StringValue
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
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import io.grpc.internal.testing.StreamRecorder
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.util.*

class StartExecutionTest : Spek({

  val launcher: ExecutionLauncher = mock()
  val service = ExecutionService(launcher)
  fun resetMocks() = reset(launcher)

  val waitStage = WaitStage.newBuilder()
    .setName("wait")
    .setWaitTime(Duration.newBuilder().setSeconds(30).build())
    .build()

  val manualTrigger = ManualTrigger.newBuilder()
    .setUser("fzlem@netflix.com")
    .build()

  val baseRequest = ExecutionRequest.newBuilder()
    .setApplication("orca")
    .setName("Test Pipeline")
    .setId(UUID.randomUUID().toString())
    .setTrigger(manualTrigger.pack())
    .build()

  val response = StreamRecorder.create<ExecutionResponse>()

  describe("starting an execution") {
    describe("with a single stage") {
      val stage = WaitStage.newBuilder()
        .mergeFrom(waitStage)
        .setRef("1")
        .build()

      val request = ExecutionRequest.newBuilder()
        .mergeFrom(baseRequest)
        .addStages(stage.pack())
        .build()

      on("sending the request") {
        service.start(request, response)
      }

      afterGroup(::resetMocks)

      it("starts a pipeline") {
        argumentCaptor<Execution>().apply {
          verify(launcher).start(capture())
          firstValue.apply {
            application shouldMatch equalTo(request.application)
            name shouldMatch equalTo(request.name)
            pipelineConfigId shouldMatch equalTo(request.id)
          }
        }
      }

      it("configures the trigger correctly") {
        argumentCaptor<Execution>().apply {
          verify(launcher).start(capture())
          firstValue.apply {
            trigger["type"] as String shouldMatch equalTo("manual")
            trigger["user"] as String shouldMatch equalTo(manualTrigger.user)
          }
        }
      }

      it("configures the stage correctly") {
        argumentCaptor<Execution>().apply {
          verify(launcher).start(capture())
          firstValue.apply {
            stages shouldMatch hasSize(equalTo(1))
            stages.first().apply {
              type shouldMatch equalTo("wait")
              name shouldMatch equalTo(stage.name)
              refId shouldMatch equalTo(stage.ref)
              requisiteStageRefIds shouldMatch isEmpty
              context["waitTime"] as Long shouldMatch equalTo(stage.waitTime.seconds)
            }
          }
        }
      }
    }

    describe("with dependent stages") {
      val stage1 = WaitStage.newBuilder()
        .mergeFrom(waitStage)
        .setRef("1")
        .build()

      val stage2 = WaitStage.newBuilder()
        .mergeFrom(waitStage)
        .setRef("2")
        .addDependsOn("1")
        .build()

      val stage3 = WaitStage.newBuilder()
        .mergeFrom(waitStage)
        .setRef("3")
        .addDependsOn("1")
        .build()

      val stage4 = WaitStage.newBuilder()
        .mergeFrom(waitStage)
        .setRef("4")
        .addAllDependsOn(listOf("2", "3"))
        .build()

      val request = ExecutionRequest.newBuilder()
        .mergeFrom(baseRequest)
        .addStages(stage1.pack())
        .addStages(stage2.pack())
        .addStages(stage3.pack())
        .addStages(stage4.pack())
        .build()

      on("sending the request") {
        service.start(request, response)
      }

      afterGroup(::resetMocks)

      it("configures the stage dependencies correctly") {
        argumentCaptor<Execution>().apply {
          verify(launcher).start(capture())
          firstValue.apply {
            stages shouldMatch hasSize(equalTo(4))
            listOf(stage1, stage2, stage3, stage4).forEach {
              stageByRef(it.ref).apply {
                refId shouldMatch equalTo(it.ref)
                requisiteStageRefIds.toSet() shouldMatch equalTo(it.dependsOnList.toSet())
              }
            }
          }
        }
      }
    }

    describe("with trigger parameters") {
      val trigger = ManualTrigger.newBuilder()
        .mergeFrom(manualTrigger)
        .putParameters("foo", StringValue.newBuilder().setValue("covfefe").build().pack())
        .putParameters("bar", Int32Value.newBuilder().setValue(1337).build().pack())
        .putParameters("baz", BoolValue.newBuilder().setValue(true).build().pack())
        .build()

      val request = ExecutionRequest.newBuilder()
        .mergeFrom(baseRequest)
        .setTrigger(trigger.pack())
        .build()

      on("sending the request") {
        service.start(request, response)
      }

      afterGroup(::resetMocks)

      it("parses parameters correctly") {
        argumentCaptor<Execution>().apply {
          verify(launcher).start(capture())
          firstValue.trigger["parameters"] as Map<String, Any> shouldMatch equalTo(mapOf(
            "foo" to "covfefe",
            "bar" to 1337,
            "baz" to true
          ))
        }
      }
    }
  }
})
