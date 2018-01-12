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

fun Struct.unpack(): Map<String, Any?> =
  fieldsMap.mapValues { (_, value) ->
    value.unpack()
  }

fun Value.unpack(): Any? =
  (@Suppress("IMPLICIT_CAST_TO_ANY", "WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  when (kindCase) {
    Value.KindCase.STRING_VALUE -> stringValue
    Value.KindCase.NUMBER_VALUE -> numberValue
    Value.KindCase.BOOL_VALUE -> boolValue
    Value.KindCase.NULL_VALUE -> null
    Value.KindCase.STRUCT_VALUE -> structValue.unpack()
    Value.KindCase.LIST_VALUE -> listValue.valuesList.map(Value::unpack)
    Value.KindCase.KIND_NOT_SET -> throw IllegalStateException("Value type not set")
  })
