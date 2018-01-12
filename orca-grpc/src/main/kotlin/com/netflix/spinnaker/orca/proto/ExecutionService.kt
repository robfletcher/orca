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

import com.google.protobuf.Value
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
    execution.trigger.putAll(unpackTrigger(request))
    request.stagesList.forEach { stage ->
      unpackStage(stage).let { it ->
        execution.stages.add(it)
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

  private fun unpackTrigger(request: ExecutionRequest): Map<String, Any> {
    val trigger = mutableMapOf<String, Any>()
    trigger["user"] = request.trigger.user
    trigger["parameters"] = request.trigger.parameters.fieldsMap.mapValues { (_, value) -> value.unpackValue() }
    trigger["notifications"] = request.trigger.notificationsList.map {
      mapOf(
        "type" to it.type.name,
        "address" to it.address,
        "cc" to it.cc,
        "when" to listOf("pipeline.complete", "pipeline.failed")
      )
    }
    when {
      request.trigger.spec.isA<ManualTrigger>() ->
        request.trigger.spec.unpack<ManualTrigger>().run {
          trigger["type"] = "manual"
          trigger["correlationId"] = correlationId
        }
      else ->
        TODO("Trigger type ${request.trigger.spec.typeUrl} is not yet supported")
    }
    return trigger
  }

  private fun Value.unpackValue(): Any? =
    @Suppress("IMPLICIT_CAST_TO_ANY")
    when (kindCase) {
      Value.KindCase.STRING_VALUE -> stringValue
      Value.KindCase.NUMBER_VALUE -> numberValue
      Value.KindCase.BOOL_VALUE -> boolValue
      Value.KindCase.NULL_VALUE -> null
      Value.KindCase.STRUCT_VALUE -> structValue.fieldsMap.mapValues { (_, value) -> value.unpackValue() }
      Value.KindCase.LIST_VALUE -> listValue.valuesList.map { it.unpackValue() }
      Value.KindCase.KIND_NOT_SET -> throw IllegalStateException("Value type not set")
    }

  private fun unpackStage(stageSpec: StageSpec): Stage =
    Stage().also { stage ->
      stage.name = stageSpec.name
      stage.refId = stageSpec.ref
      stage.requisiteStageRefIds = stageSpec.dependsOnList
      stage.context["comments"] = stageSpec.comments
      when {
        stageSpec.spec.isA<WaitStageSpec>() -> stageSpec.spec.unpack<WaitStageSpec>().run {
          stage.type = "wait"
          stage.context["waitTime"] = waitTime.seconds
        }
        else ->
          TODO("Stage type ${stageSpec.spec.typeUrl} is not yet supported")
      }
    }
}
