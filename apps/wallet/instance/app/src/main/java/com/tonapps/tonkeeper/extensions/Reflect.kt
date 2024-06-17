package com.tonapps.tonkeeper.extensions

import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaType

fun KProperty<*>.isFunction(): Boolean {
    return returnType.javaType.typeName.startsWith("kotlin.jvm.functions.Function")
}
