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

import org.assertj.core.api.*
import org.assertj.core.api.Assertions.assertThat
import java.lang.reflect.Field
import kotlin.reflect.KFunction1
import kotlin.reflect.KProperty1

private val actualField: Field = AbstractAssert::class.java
  .getDeclaredField("actual")
  .apply { isAccessible = true }

@Suppress("UNCHECKED_CAST")
private val <ACTUAL>
  Assert<*, ACTUAL>.actual: ACTUAL
  get() = actualField.get(this) as ACTUAL

/**
 * Alias for [Assert#isInstanceOf] using Kotlin's reified generics.
 */
inline fun <reified T> Assert<*, *>.isA(): Assert<*, *> =
  isInstanceOf(T::class.java)

fun Assert<*, *>.asInteger() = run {
  isA<Int>()
  IntegerAssert(actual as Int)
}

fun Assert<*, *>.asMap() = run {
  isA<Map<*, *>>()
  MapAssert(actual as Map<Any, Any>)
}

fun Assert<*, *>.asIterable() = run {
  isA<Iterable<*>>()
  IterableAssert(actual as Iterable<Any>)
}

fun <SELF : Assert<*, ACTUAL>, ACTUAL, PROP>
  SELF.get(
  getter: KFunction1<ACTUAL, PROP>
): Assert<*, PROP> =
  assertThat(getter.invoke(actual))
    .`as`(getter.name) as Assert<*, PROP>

fun <SELF : Assert<*, ACTUAL>, ACTUAL, PROP>
  SELF.get(
  getter: KProperty1<ACTUAL, PROP>
): Assert<*, PROP> =
  assertThat(getter.get(actual))
    .`as`(getter.name) as Assert<*, PROP>
