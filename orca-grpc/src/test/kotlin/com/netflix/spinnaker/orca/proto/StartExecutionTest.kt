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
import com.google.protobuf.Value
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasSize
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.should.shouldMatch
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.proto.execution.*
import com.netflix.spinnaker.orca.proto.execution.Notification.NotificationType
import com.netflix.spinnaker.orca.proto.execution.Notification.newBuilder
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

  val waitStage = StageSpec.newBuilder()
    .setName("wait")
    .setSpec(WaitStageSpec.newBuilder()
      .setWaitTime(Duration.newBuilder().setSeconds(30).build())
      .build()
      .pack()
    )
    .build()

  val manualTrigger = Trigger.newBuilder()
    .setUser("fzlem@netflix.com")
    .setSpec(ManualTrigger.newBuilder().build().pack())
    .build()

  val baseRequest = ExecutionRequest.newBuilder()
    .setApplication("orca")
    .setName("Test Pipeline")
    .setId(UUID.randomUUID().toString())
    .setTrigger(manualTrigger)
    .build()

  val response = StreamRecorder.create<ExecutionResponse>()

  describe("starting an execution") {
    describe("with a single stage") {
      val stage = StageSpec.newBuilder()
        .mergeFrom(waitStage)
        .setRef("1")
        .build()

      val request = ExecutionRequest.newBuilder()
        .mergeFrom(baseRequest)
        .addStages(stage)
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
              context["waitTime"] as Long shouldMatch equalTo(stage.spec.unpack<WaitStageSpec>().waitTime.seconds)
            }
          }
        }
      }

      it("responds with the new pipeline id") {
        val id = argumentCaptor<Execution>().run {
          verify(launcher).start(capture())
          firstValue.id
        }

        response.values.size shouldMatch equalTo(1)
        response.values.first().id shouldMatch equalTo(id)
      }
    }

    describe("with dependent stages") {
      val stage1 = StageSpec.newBuilder()
        .mergeFrom(waitStage)
        .setRef("1")
        .build()

      val stage2 = StageSpec.newBuilder()
        .mergeFrom(waitStage)
        .setRef("2")
        .addDependsOn("1")
        .build()

      val stage3 = StageSpec.newBuilder()
        .mergeFrom(waitStage)
        .setRef("3")
        .addDependsOn("1")
        .build()

      val stage4 = StageSpec.newBuilder()
        .mergeFrom(waitStage)
        .setRef("4")
        .addAllDependsOn(listOf("2", "3"))
        .build()

      val request = ExecutionRequest.newBuilder()
        .mergeFrom(baseRequest)
        .addStages(stage1)
        .addStages(stage2)
        .addStages(stage3)
        .addStages(stage4)
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
      val trigger = Trigger.newBuilder()
        .mergeFrom(manualTrigger)
        .apply {
          parametersBuilder
            .putFields("foo", Value.newBuilder().setStringValue("covfefe").build())
            .putFields("bar", Value.newBuilder().setNumberValue(1337.0).build())
            .putFields("baz", Value.newBuilder().setBoolValue(true).build())
            .build()
        }.build()

      val request = ExecutionRequest.newBuilder()
        .mergeFrom(baseRequest)
        .setTrigger(trigger)
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
            "bar" to 1337.0,
            "baz" to true
          ))
        }
      }
    }

    describe("with trigger notifications") {
      val emailNotification = newBuilder()
        .setType(NotificationType.email)
        .setAddress("fzlem@netflix.com")
        .setCc("reed@netflix.com")
        .build()

      val trigger = Trigger.newBuilder()
        .mergeFrom(manualTrigger)
        .addNotifications(emailNotification)
        .build()

      val request = ExecutionRequest.newBuilder()
        .mergeFrom(baseRequest)
        .setTrigger(trigger)
        .build()

      on("sending the request") {
        service.start(request, response)
      }

      afterGroup(::resetMocks)

      it("parses parameters correctly") {
        argumentCaptor<Execution>().apply {
          verify(launcher).start(capture())
          (firstValue.trigger["notifications"] as List<Map<String, String>>).apply {
            size shouldMatch equalTo(1)
            first()["type"] shouldMatch equalTo("email")
            first()["address"] shouldMatch equalTo(emailNotification.address)
            first()["cc"] shouldMatch equalTo(emailNotification.cc)
            first()["when"] as List<String> shouldMatch equalTo(listOf("pipeline.complete", "pipeline.failed"))
          }
        }
      }
    }
  }
})
