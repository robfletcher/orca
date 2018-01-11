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
            "parameters" to trigger.parametersMap,
            "correlationId" to trigger.correlationId,
            "notifications" to trigger.notificationsMap
          )
        }
      }
      else ->
        TODO("Trigger type ${request.trigger.typeUrl} is not yet supported")
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
