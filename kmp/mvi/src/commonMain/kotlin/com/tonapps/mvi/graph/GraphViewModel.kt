package com.tonapps.mvi.graph

import com.tonapps.async.ThreadChecker
import com.tonapps.log.L
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.mvi.Mvi
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.flow.transformFirst
import com.tonapps.mvi.thread.MviThread
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningReduce
import kotlinx.coroutines.flow.transformLatest

open class GraphViewModel<State : MviViewState, Action : MviAction> : AsyncViewModel() {

    protected val actions = MutableSharedFlow<Action>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun sendAction(action: Action) {
        checkMainThread("sendAction")
        actions.tryEmit(action)
    }

    // Action operators
    protected inline fun <reified A : Action> Flow<Action>.on(): Flow<A> {
        return filterIsInstance<A>()
    }

    // State producers
    fun <R> loadState(
        loader: suspend () -> R,
    ): Flow<KState<R>> {
        return flow {
            checkMainThread("loadState")

            val result = runCatching { loader() }
            currentCoroutineContext().ensureActive()

            result.fold(
                onSuccess = { emit(KState.Data(it)) },
                onFailure = { uncachedException(it) }
            )
        }
    }

    fun <T, R> Flow<T>.transformStateLatest(
        loader: suspend (T) -> R,
    ): Flow<KState<R>> {
        return transformLatest { item ->
            checkMainThread("transformStateLatest")
            emit(KState.Loading())

            val result = runCatching { loader(item) }
            currentCoroutineContext().ensureActive()

            result.fold(
                onSuccess = { emit(KState.Data(it)) },
                onFailure = { uncachedException(it) }
            )
        }.runningReduce { prev, next ->
            when (next) {
                is KState.Loading -> KState.Loading(prev.state)
                is KState.Data -> next
            }
        }
    }

    fun <T, R> Flow<T>.transformStateFirst(
        loader: suspend (T) -> R,
    ): Flow<KState<R>> {
        return transformFirst { item ->
            checkMainThread("transformStateFirst")
            emit(KState.Loading())

            val result = runCatching { loader(item) }
            currentCoroutineContext().ensureActive()

            result.fold(
                onSuccess = { emit(KState.Data(it)) },
                onFailure = { uncachedException(it) }
            )
        }.runningReduce { prev, next ->
            when (next) {
                is KState.Loading -> KState.Loading(prev.state)
                is KState.Data -> next
            }
        }
    }

    // State operators
    fun <T> Flow<KState<T>>.isLoading(): Flow<Boolean> {
        return map { it is KState.Loading }
    }

    fun <T> Flow<KState<T>>.filterStateLoading(): Flow<KState.Loading<T>> {
        return filterIsInstance<KState.Loading<T>>()
    }

    fun <T> Flow<KState<T>>.filterStateData(): Flow<KState.Data<T>> {
        return filterIsInstance<KState.Data<T>>()
    }

    fun <T> Flow<KState<T>>.mapStateLoadingOrNull(): Flow<KState.Loading<T>?> {
        return map { it as? KState.Loading<T> }
    }

    fun <T> Flow<KState<T>>.mapStateDataOrNull(): Flow<KState.Data<T>?> {
        return map { it as? KState.Data<T> }
    }

    fun <T> Flow<KState<T>>.filterAndMapStateData(
        transform: suspend (value: KState.Data<T>) -> T = { it.state }
    ): Flow<T> {
        return filterIsInstance<KState.Data<T>>()
            .map { transform(it) }
    }

    fun <T> Flow<KState<T>>.mapStateDataValueOrNull(
        transform: suspend (value: KState.Data<T>) -> T = { it.state }
    ): Flow<T?> {
        return map {
            (it as? KState.Data<T>)
                ?.let { data -> transform(data) }
        }
    }

    fun <T> Flow<KState<T>>.cacheStateWithLoading(): StateFlow<KState<T>> {
        return cacheState(initialValue = KState.Loading())
    }

    fun <T> Flow<KState<T?>>.cacheStateWithEmpty(): StateFlow<KState<T?>> {
        return cacheState(initialValue = KState.empty())
    }


    // Result operators
    fun <T, E> Flow<KResult<T, E>?>.isError(): Flow<Boolean> {
        return map { it is KResult.Err }
    }

    fun <T, E> Flow<KResult<T, E>?>.filterResultError(): Flow<KResult.Err<T, E>> {
        return filterIsInstance<KResult.Err<T, E>>()
    }

    fun <T, E> Flow<KResult<T, E>?>.filterResultValue(): Flow<KResult.Ok<T, E>> {
        return filterIsInstance<KResult.Ok<T, E>>()
    }

    fun <T, E> Flow<KResult<T, E>?>.mapResultErrorOrNull(): Flow<KResult.Err<T, E>?> {
        return map { it as? KResult.Err<T, E> }
    }

    fun <T, E> Flow<KResult<T, E>?>.mapResultValueOrNull(): Flow<KResult.Ok<T, E>?> {
        return map { it as? KResult.Ok<T, E> }
    }


    // Utils
    private fun uncachedException(e: Throwable) {
        L.e(e)

        if (Mvi.config().isFastFail) {
            ThreadChecker.finishProcess()
        }
    }

    private fun checkMainThread(where: String) {
        if (ThreadChecker.isMainThread()) {
            return
        }

        val error = IllegalStateException(
            "$where must run on the main thread, was '${Thread.currentThread().name}'"
        )

        if (Mvi.config().isFastFail) {
            throw error
        }

        L.e(error)
    }
}
