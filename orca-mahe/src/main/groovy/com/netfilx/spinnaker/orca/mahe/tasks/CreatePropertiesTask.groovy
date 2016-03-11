package com.netfilx.spinnaker.orca.mahe.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netfilx.spinnaker.orca.mahe.MaheService
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.client.Response

/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@Slf4j
@Component
class CreatePropertiesTask implements Task{

  @Autowired MaheService maheService
  @Autowired ObjectMapper mapper

  @Override
  TaskResult execute(Stage stage) {


    List properties = assemblePersistedPropertyListFromContext(stage.context)

    List propertyIdList = []

    properties.forEach { Map prop ->

      log.info("Upserting Property: ${prop}")
      Response response = maheService.upsertProperty(prop)
      if (response.status == 200 && response.body.mimeType().startsWith('application/')) {
        Map responseMap = mapper.readValue(response.body.in().text, Map)
        propertyIdList << responseMap.propertyId

      } else {
        throw new IllegalStateException("Unable to handle $response for property $prop")
      }
    }

    def outputs = [
      propertyIdList: propertyIdList
    ]

    return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, outputs)

  }

  List assemblePersistedPropertyListFromContext(Map<String, Object> context) {
    Map scope = context.scope
    scope.appId = scope.appIdList.first()
    String email = context.email
    String cmcTicket = context.cmcTicket

    List properties = context.persistedProperties

    return properties.collect { Map prop ->
      prop << scope
      prop.email = email
      prop.sourceOfUpdate = 'spinnaker'
      prop.cmcTicket = cmcTicket
      [property: prop]
    }

  }
}
