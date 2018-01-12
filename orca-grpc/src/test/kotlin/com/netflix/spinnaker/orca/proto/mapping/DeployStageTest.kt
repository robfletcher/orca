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

package com.netflix.spinnaker.orca.proto.mapping

import com.google.protobuf.ListValue
import com.google.protobuf.Value
import com.netflix.spinnaker.assertj.hasEntry
import com.netflix.spinnaker.assertj.softly
import com.netflix.spinnaker.orca.proto.execution.DeployStageSpec
import com.netflix.spinnaker.orca.proto.execution.DeployStageSpec.ClusterSpec.Ec2ClusterSpec
import com.netflix.spinnaker.orca.proto.execution.StageSpec
import com.netflix.spinnaker.orca.proto.pack
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

class DeployStageTest : Spek({

  val spec = StageSpec.newBuilder().apply {
    name = "deploy"
    ref = "3"
    addDependsOn("1")
    addDependsOn("2")
    spec = DeployStageSpec.newBuilder().apply {
      addClustersBuilder().apply {
        cloudProvider = "aws"
        account = "test"
        monikerBuilder.apply {
          app = "covfefe"
          stack = "stack"
          detail = "detail"
          cluster = "covfefe-stack-detail"
        }
        strategy = "highlander"
        capacityBuilder.apply {
          min = 20
          max = 100
          desired = 50
        }
        useSourceCapacity = false
        addLoadBalancers("covfefe-frontend")
        addSecurityGroups("covfefe-sg")
        addSecurityGroups("infra")
        providerSpec = Ec2ClusterSpec.newBuilder().apply {
          availabilityZonesBuilder.apply {
            putFields("us-west-2", Value.newBuilder().setListValue(
              ListValue.newBuilder().apply {
                addValuesBuilder().setStringValue("us-west-2a")
                addValuesBuilder().setStringValue("us-west-2b")
                addValuesBuilder().setStringValue("us-west-2c")
              }
            ).build())
          }
          cooldownBuilder.seconds = 30
          copySourceCustomBlockDeviceMappings = true
          ebsOptimized = true
          healthCheckGracePeriodBuilder.seconds = 30
          healthCheckType = "EC2"
          iamRole = "covfefe"
          instanceMonitoring = true
          instanceType = "r3.2xl"
          keyPair = "covfefe_keypair"
          subnetType = "vpc(internal)"
          targetHealthyDeployPercentage = 100
          useAmiBlockDeviceMappings = true
        }.build().pack()
      }
    }.build().pack()
  }.build()

  describe("unpacking a stage with a single cluster") {
    val stage = spec.unpack()

    it("should unpack common stage values") {
      stage.apply {
        softly {
          assertThat(name).isEqualTo("deploy")
          assertThat(type).isEqualTo("deploy")
          assertThat(refId).isEqualTo("3")
          assertThat(requisiteStageRefIds).isEqualTo(setOf("1", "2"))
        }
      }
    }

    describe("unpacking clusters") {
      val clusters = stage.context["clusters"] as List<Map<String, Any>>

      it("should have a single cluster") {
        assertThat(clusters).hasSize(1)
      }

      describe("unpacking cluster details") {
        val cluster = clusters.first()

        it("should unpack a moniker-compatible cluster name") {
          softly {
            assertThat(cluster).hasEntry("moniker") {
              isEqualTo(mapOf("app" to "covfefe", "stack" to "stack", "detail" to "detail", "cluster" to "covfefe-stack-detail"))
            }
            (cluster["moniker"] as Map<String, String>).let { moniker ->
              assertThat(cluster).hasEntry("application") { isEqualTo(moniker["app"]) }
              assertThat(cluster).hasEntry("stack") { isEqualTo(moniker["stack"]) }
              assertThat(cluster).hasEntry("freeFormDetails") { isEqualTo(moniker["detail"]) }
            }
          }
        }

        it("should unpack non provider-specific cluster details") {
          softly {
            assertThat(cluster).hasEntry("cloudProvider") { isEqualTo("aws") }
            assertThat(cluster).hasEntry("provider") { isEqualTo(cluster["cloudProvider"]) }
            assertThat(cluster).hasEntry("account") { isEqualTo("test") }
            assertThat(cluster).hasEntry("strategy") { isEqualTo("highlander") }
            assertThat(cluster).hasEntry("capacity") { isEqualTo(mapOf("min" to 20, "max" to 100, "desired" to 50)) }
            assertThat(cluster).hasEntry("useSourceCapacity") { isEqualTo(false) }
            assertThat(cluster).hasEntry("loadBalancers") { isEqualTo(listOf("covfefe-frontend")) }
            assertThat(cluster).hasEntry("securityGroups") { isEqualTo(listOf("covfefe-sg", "infra")) }
            assertThat(cluster).hasEntry("tags") { isEqualTo(emptyMap<String, String>()) }
            assertThat(cluster).hasEntry("entityTags") { isEqualTo(emptyMap<String, Any>()) }
          }
        }

        it("should unpack provider-specific cluster details") {
          softly {
            assertThat(cluster).hasEntry("availabilityZones") {
              isEqualTo(mapOf("us-west-2" to listOf("us-west-2a", "us-west-2b", "us-west-2c")))
            }
            assertThat(cluster).hasEntry("cooldown") { isEqualTo(30) }
            assertThat(cluster).hasEntry("copySourceCustomBlockDeviceMappings") { isEqualTo(true) }
            assertThat(cluster).hasEntry("ebsOptimized") { isEqualTo(true) }
//            assertThat(cluster).hasEntry("enabledMetrics") { isEqualTo(emptyList<String>()) }
            assertThat(cluster).hasEntry("healthCheckGracePeriod") { isEqualTo(30) }
            assertThat(cluster).hasEntry("healthCheckType") { isEqualTo("EC2") }
            assertThat(cluster).hasEntry("iamRole") { isEqualTo("covfefe") }
            assertThat(cluster).hasEntry("instanceMonitoring") { isEqualTo(true) }
            assertThat(cluster).hasEntry("instanceType") { isEqualTo("r3.2xl") }
            assertThat(cluster).hasEntry("keyPair") { isEqualTo("covfefe_keypair") }
            assertThat(cluster).hasEntry("subnetType") { isEqualTo("vpc(internal)") }
//            assertThat(cluster).hasEntry("suspendedProcesses") { isEqualTo(emptyList<String>()) }
//            assertThat(cluster).hasEntry("targetGroups") { isEqualTo(emptyList<String>()) }
            assertThat(cluster).hasEntry("targetHealthyDeployPercentage") { isEqualTo(100) }
//            assertThat(cluster).hasEntry("terminationPolicies") { isEqualTo(emptyList<String>()) }
            assertThat(cluster).hasEntry("useAmiBlockDeviceMappings") { isEqualTo(true) }
          }
        }
      }
    }
  }
})

