package com.netflix.spinnaker.orca.mahe.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netfilx.spinnaker.orca.mahe.MaheService
import com.netfilx.spinnaker.orca.mahe.pipeline.MonitorCreatePropertyStage
import com.netfilx.spinnaker.orca.mahe.tasks.MonitorPropertiesTask
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import groovy.json.JsonOutput
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import retrofit.mime.TypedString
import spock.lang.IgnoreRest
import spock.lang.Specification

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

class MonitorPropertiesTaskSpec extends Specification {

  MaheService maheService = Mock(MaheService)
  ObjectMapper mapper = new ObjectMapper()
  MonitorPropertiesTask task = new MonitorPropertiesTask(maheService: maheService, mapper: mapper)

  def "monitor newly created persisted property still running"() {
    given:
    def pipeline = new Pipeline(application: 'foo')
    List propertyIds = ["foo|bar"]
    List persistedProperty = [[key: 'foo', value:'bar']]
    Map context = [propertyIdList: propertyIds, persistedProperties: persistedProperty]
    def stage = new PipelineStage( pipeline, MonitorCreatePropertyStage.PIPELINE_CONFIG_TYPE, context)

    when:
    TaskResult result = task.execute(stage)

    then:

    1 * maheService.getPropertyById(propertyIds.first()) >> { String id ->
      def body = JsonOutput.toJson([property:[propertyId: id, key:"foo", value: "bar"]])
      new Response("http://mahe", 200, "OK", [], new TypedByteArray('application/json', body.bytes))
    }

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.stageOutputs.persistedProperties.size() == 1

    with(result.stageOutputs.persistedProperties.first()) {
      propertyId == propertyIds.first()
      key == "foo"
      value == "bar"
    }
  }

}
