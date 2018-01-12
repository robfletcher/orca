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

package com.netflix.spinnaker.assertj

import org.assertj.core.api.AbstractMapAssert
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions
import org.assertj.core.api.SoftAssertions

fun <SELF : AbstractMapAssert<SELF, ACTUAL, K, V>, ACTUAL : Map<K, V>, K, V> AbstractMapAssert<SELF, ACTUAL, K, V>.hasEntry(key: K, block: AbstractObjectAssert<*, V>.() -> Unit): AbstractMapAssert<SELF, ACTUAL, K, V> =
  hasEntrySatisfying(key) {
    @Suppress("UNCHECKED_CAST")
    (Assertions.assertThat(it)
      .`as`(key.toString()) as AbstractObjectAssert<*, V>)
      .apply(block)
  }

fun softly(block: SoftAssertions.() -> Unit) {
  SoftAssertions.assertSoftly(block)
}
