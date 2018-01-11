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
import com.google.protobuf.Int32Value
import com.google.protobuf.StringValue
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.proto.execution.*
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory

class ExecutionService(
  private val launcher: ExecutionLauncher
) : ExecutionServiceGrpc.ExecutionServiceImplBase() {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun start(
    request: ExecutionRequest,
    responseObserver: StreamObserver<ExecutionResponse>
  ) {
    log.info("Received execution request… $request")

    val execution = Execution(PIPELINE, request.application)
    execution.pipelineConfigId = request.id
    execution.name = request.name
    execution.trigger.putAll(convertTrigger(request))
    request.stagesList.forEach { stage ->
      convertStage(stage).let { (type, name, context) ->
        execution.stages.add(Stage(execution, type, name, context))
      }
    }

    launcher.start(execution)

    ExecutionResponse
      .newBuilder()
      .setId(execution.id)
      .build()
      .let { response ->
        responseObserver.onNext(response)
        responseObserver.onCompleted()
      }
  }

  private fun convertTrigger(request: ExecutionRequest): Map<String, Any> =
    when {
      request.trigger.isA<ManualTrigger>() -> {
        request.trigger.unpack<ManualTrigger>().let { trigger ->
          mapOf(
            "type" to "manual",
            "user" to trigger.user,
            "parameters" to trigger.parametersMap.unpackValues(),
            "correlationId" to trigger.correlationId,
            "notifications" to trigger.notificationsList.map {
              mapOf(
                "type" to it.type.name,
                "address" to it.address,
                "cc" to it.cc,
                "when" to listOf("pipeline.complete", "pipeline.failed")
              )
            }
          )
        }
      }
      else ->
        TODO("Trigger type ${request.trigger.typeUrl} is not yet supported")
    }

  private fun Map<String, com.google.protobuf.Any>.unpackValues(): Map<String, Any> =
    mapValues { (_, value) ->
      @Suppress("IMPLICIT_CAST_TO_ANY")
      when {
        value.isA<StringValue>() -> value.unpack<StringValue>().value
        value.isA<Int32Value>() -> value.unpack<Int32Value>().value
        value.isA<BoolValue>() -> value.unpack<BoolValue>().value
        else -> TODO("Parameter value type ${value.typeUrl} is not yet supported")
      }
    }

  private fun convertStage(stage: com.google.protobuf.Any): Triple<String, String, Map<String, Any>> =
    when {
      stage.isA<WaitStage>() -> stage.unpack<WaitStage>().run {
        Triple("wait", name, mapOf(
          "waitTime" to waitTime.seconds,
          "refId" to ref,
          "requisiteStageRefIds" to dependsOnList
        ))
      }
      else ->
        TODO("Stage type ${stage.typeUrl} is not yet supported")
    }
}
