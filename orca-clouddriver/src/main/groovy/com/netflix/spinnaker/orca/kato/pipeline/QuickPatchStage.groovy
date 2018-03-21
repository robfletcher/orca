/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.kato.pipeline

import java.util.concurrent.ConcurrentHashMap
import javax.annotation.Nonnull
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.kato.tasks.quip.ResolveQuipVersionTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.client.Client

/**
 * Wrapper stage over BuilkQuickPatchStage.  We do this so we can reuse the same steps whether or not we are doing
 * a rolling quick patch.  The difference is that the rolling version will only update one instance at a time while
 * the non-rolling version will act on all instances at once.  This is done by controlling the instances we
 * send to BuilkQuickPatchStage.
 */
@Slf4j
@Component
@CompileStatic
class QuickPatchStage implements StageDefinitionBuilder {

  @Autowired
  BulkQuickPatchStage bulkQuickPatchStage

  @Autowired
  OortHelper oortHelper

  @Autowired
  Client retrofitClient

  public static final String PIPELINE_CONFIG_TYPE = "quickPatch"

  private static INSTANCE_VERSION_SLEEP = 10000

  @Override
  void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder.withTask("resolveQuipVersion", ResolveQuipVersionTask)
  }

  @Override
  void afterStages(@Nonnull Stage parent, @Nonnull StageGraphBuilder graph) {
    def instances = getInstancesForCluster(parent)
    if (instances.size() == 0) {
      // skip since nothing to do
    } else if (parent.context.rollingPatch) {
      // rolling means instances in the asg will be updated sequentially
      instances.inject(null as Stage) { Stage previous, key, value ->
        def instance = [:]
        instance.put(key, value)
        def nextStageContext = [:]
        nextStageContext.putAll(parent.context)
        nextStageContext << [instances: instance]
        nextStageContext.put("instanceIds", [key]) // for WaitForDown/UpInstancesTask

        if (previous == null) {
        graph.add {
          it.type = bulkQuickPatchStage.type
          it.name = "bulkQuickPatchStage"
          it.context = nextStageContext
        }
        } else {
          graph.connect(previous) {
            it.type = bulkQuickPatchStage.type
            it.name = "bulkQuickPatchStage"
            it.context = nextStageContext
          }
        }
      }
    } else { // quickpatch all instances in the asg at once
      def nextStageContext = [:]
      nextStageContext.putAll(parent.context)
      nextStageContext << [instances: instances]
      nextStageContext.put("instanceIds", instances.collect { key, value -> key })
      // for WaitForDown/UpInstancesTask

      graph.add {
        it.type = bulkQuickPatchStage.type
        it.name = "bulkQuickPatchStage"
        it.context = nextStageContext
      }
    }
  }

  Map getInstancesForCluster(Stage stage) {
    ConcurrentHashMap instances = new ConcurrentHashMap(oortHelper.getInstancesForCluster(stage.context, null, true, false))
    return instances
  }

}
