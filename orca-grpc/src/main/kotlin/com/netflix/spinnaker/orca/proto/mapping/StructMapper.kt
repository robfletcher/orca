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

import com.google.protobuf.Struct
import com.google.protobuf.Value

fun unpack(proto: Struct) =
  proto.fieldsMap.mapValues { (_, value) ->
    unpack(value)
  }

fun unpack(value: Value): Any? =
  (@Suppress("IMPLICIT_CAST_TO_ANY", "WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  when (value.kindCase) {
    Value.KindCase.STRING_VALUE -> value.stringValue
    Value.KindCase.NUMBER_VALUE -> value.numberValue
    Value.KindCase.BOOL_VALUE -> value.boolValue
    Value.KindCase.NULL_VALUE -> null
    Value.KindCase.STRUCT_VALUE -> unpack(value.structValue)
    Value.KindCase.LIST_VALUE -> value.listValue.valuesList.map(::unpack)
    Value.KindCase.KIND_NOT_SET -> throw IllegalStateException("Value type not set")
  })
