/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.model

import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class StageSpec extends Specification {

  def "sets timeoutMs using #key key"() {
    expect:
    with(new Stage<>(new Pipeline(), "covfefe", "covfefe", json)) {
      timeoutMs == value
      !context.containsKey(key)
    }

    where:
    key              | value
    "timeoutMs"      | 30_000L
    "stageTimeoutMs" | 30_000L

    json = [(key): value]
  }

  def "sets onFailure using #key value of #value"() {
    expect:
    with(new Stage<>(new Pipeline(), "covfefe", "covfefe", json)) {
      onFailure == FailurePolicy.valueOf(value ?: "fail")
      !context.containsKey(key)
    }

    where:
    key         | value
    "onFailure" | "ignore"

    json = [(key): value]
  }

  def "defaults onFailure to #expected"() {
    expect:
    with(new Stage<>(new Pipeline(), "covfefe", "covfefe", [:])) {
      onFailure == expected
    }

    where:
    expected = FailurePolicy.fail
  }

}
