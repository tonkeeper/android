package com.tonapps.extensions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.reflect.Constructor
import kotlin.reflect.KClass
import kotlin.time.Duration

inline fun <reified T : Any> KClass<T>.constructor(vararg args: KClass<*>): Constructor<T> {
    return T::class.java.getDeclaredConstructor(*args.map { it.java }.toTypedArray())
}

suspend fun <T> whileTimeoutOrNull(
    timeout: Duration,
    block: suspend CoroutineScope.() -> T
): T? = withTimeoutOrNull(timeout) {
    while (isActive) {
        val result = block()
        if (result != null) {
            return@withTimeoutOrNull result
        }
        delay(100)
    }
    null
}