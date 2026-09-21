package com.tonapps.mvi

import androidx.lifecycle.ViewModel
import com.tonapps.async.Async
import com.tonapps.log.L
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

abstract class AsyncViewModel : ViewModel() {

    class Scopes(
        val main: CoroutineScope,
        val bg: CoroutineScope,
        val state: CoroutineScope
    )

    @Suppress("MemberVisibilityCanBePrivate")
    protected val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        L.e(throwable, "Coroutine Exception Handler")
    }

    private val job = SupervisorJob()

    @Suppress("MemberVisibilityCanBePrivate")
    protected val commonScope = job + exceptionHandler

    // Bg
    protected val bgDispatcher = Async.Io
    protected val bgContext = commonScope + bgDispatcher
    protected val bgScope = CoroutineScope(bgContext)

    // State
    protected val stateDispatcher = Async.stateDispatcher()
    protected val stateContext = commonScope + stateDispatcher
    protected val stateScope = CoroutineScope(commonScope + stateContext)

    // Main
    @Suppress("MemberVisibilityCanBePrivate")
    protected val mainContext = commonScope + Dispatchers.Main.immediate
    protected val mainScope = CoroutineScope(mainContext)

    // Utils
    internal val scopes = Scopes(mainScope, bgScope, stateScope)

    suspend fun verifyError(t: Throwable) {
        currentCoroutineContext().ensureActive()
        if (t is CancellationException) {
            throw t
        }
    }

    override fun onCleared() {
        super.onCleared()
        job.cancel()
    }

    fun <T> Flow<T>.cacheState(
        scope: CoroutineScope = mainScope,
        started: SharingStarted = SharingStarted.Eagerly,
    ) = stateIn(scope, started, null)

    fun <T> Flow<T>.cacheState(
        scope: CoroutineScope = mainScope,
        started: SharingStarted = SharingStarted.Eagerly,
        initialValue: T
    ) = stateIn(scope, started, initialValue)

    protected suspend inline fun <S : Any> MutableStateFlow<S>.postState(setter: S.() -> S): Boolean {
        return this.tryEmit(setter(value))
    }

    protected suspend inline fun <reified S : Any> MutableStateFlow<in S>.postOnExactState(
        failure: () -> Unit = { },
        setter: S.() -> S,
    ): Boolean {
        val currentValue = value
        if (currentValue is S) {
            val newState = setter(currentValue)
            return this.tryEmit(newState)
        } else {
            failure()
            return false
        }
    }
}
