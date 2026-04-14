package com.tonapps.async

import kotlinx.coroutines.CoroutineDispatcher

expect object AsyncPlatform {
    val Io: CoroutineDispatcher
    fun createDispatcherPool(name: String): CoroutineDispatcher
}

expect object ThreadChecker {
    fun isMainThread(): Boolean
    fun isStateThread(): Boolean
    fun isStateDiffThread(): Boolean
}