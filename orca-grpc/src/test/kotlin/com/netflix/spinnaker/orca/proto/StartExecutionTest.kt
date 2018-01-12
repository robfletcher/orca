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
import com.netflix.spinnaker.assertj.hasEntry
import com.netflix.spinnaker.assertj.softly
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
import org.assertj.core.api.Assertions.assertThat
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
          softly {
            firstValue.apply {
              assertThat(application).isEqualTo(request.application)
              assertThat(name).isEqualTo(request.name)
              assertThat(pipelineConfigId).isEqualTo(request.id)
            }
          }
        }
      }

      it("configures the trigger correctly") {
        argumentCaptor<Execution>().apply {
          verify(launcher).start(capture())
          softly {
            firstValue.apply {
              assertThat(trigger["type"]).isEqualTo("manual")
              assertThat(trigger["user"]).isEqualTo(manualTrigger.user)
            }
          }
        }
      }

      it("configures the stage correctly") {
        argumentCaptor<Execution>().apply {
          verify(launcher).start(capture())
          softly {
            firstValue.apply {
              assertThat(stages).hasSize(1)
              stages.first().apply {
                assertThat(type).isEqualTo("wait")
                assertThat(name).isEqualTo(stage.name)
                assertThat(refId).isEqualTo(stage.ref)
                assertThat(requisiteStageRefIds).isEmpty()
                assertThat(context["waitTime"]).isEqualTo(stage.spec.unpack<WaitStageSpec>().waitTime.seconds)
              }
            }
          }
        }
      }

      it("responds with the new pipeline id") {
        val id = argumentCaptor<Execution>().run {
          verify(launcher).start(capture())
          firstValue.id
        }

        assertThat(response.values).extracting("id").isEqualTo(listOf(id))
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
          firstValue.let { execution ->
            softly {
              assertThat(execution.stages).hasSize(4)
              listOf(stage1, stage2, stage3, stage4).forEach {
                execution.stageByRef(it.ref).apply {
                  assertThat(refId).isEqualTo(it.ref)
                  assertThat(requisiteStageRefIds).isEqualTo(it.dependsOnList.toSet())
                }
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
          assertThat(firstValue.trigger["parameters"]).isEqualTo(mapOf(
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
          (firstValue.trigger["notifications"] as List<Map<String, String>>).let { notifications ->
            softly {
              assertThat(notifications).hasSize(1)
              assertThat(notifications.first())
                .hasEntry("type") { isEqualTo("email") }
                .hasEntry("address") { isEqualTo(emailNotification.address) }
                .hasEntry("cc") { isEqualTo(emailNotification.cc) }
                .hasEntry("when") { isEqualTo(listOf("pipeline.complete", "pipeline.failed")) }
            }
          }
        }
      }
    }
  }
})
