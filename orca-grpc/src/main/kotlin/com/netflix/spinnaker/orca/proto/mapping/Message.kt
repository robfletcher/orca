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

import com.google.protobuf.Descriptors
import com.google.protobuf.Duration
import com.google.protobuf.Message
import com.google.protobuf.Struct
import org.slf4j.LoggerFactory

/**
 * Generic unpack that will turn any [Message] into a [Map].
 */
fun Message.unpack(): Map<String, Any> =
  mutableMapOf<String, Any>().also(this::unpackInto)

private val log = LoggerFactory.getLogger("com.netflix.spinnaker.orca.proto.mapping")

/**
 * Generic unpack that will turn any [Message] into a [Map].
 */
fun Message.unpackInto(model: MutableMap<String, Any>) {
  allFields.forEach { (descriptor, value) ->
    model[descriptor.jsonName] = when (value) {
      is Duration -> value.seconds.toInt()
      is Struct -> value.unpack()
      is Descriptors.EnumValueDescriptor -> value.name
      else -> value
    }
  }
}
