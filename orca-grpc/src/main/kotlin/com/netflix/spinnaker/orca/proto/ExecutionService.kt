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
import com.netflix.spinnaker.orca.proto.execution.ExecutionRequest
import com.netflix.spinnaker.orca.proto.execution.ExecutionResponse
import com.netflix.spinnaker.orca.proto.execution.ExecutionServiceGrpc
import com.netflix.spinnaker.orca.proto.mapping.ExecutionMapper
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory

class ExecutionService(
  private val launcher: ExecutionLauncher
) : ExecutionServiceGrpc.ExecutionServiceImplBase() {

  private val log = LoggerFactory.getLogger(javaClass)
  private val executionMapper = ExecutionMapper()

  override fun start(
    request: ExecutionRequest,
    responseObserver: StreamObserver<ExecutionResponse>
  ) {
    log.info("Received execution request… $request")

    val execution = executionMapper.unpack(request)

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
}
