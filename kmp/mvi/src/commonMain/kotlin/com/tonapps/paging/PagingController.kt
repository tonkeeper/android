package com.tonapps.paging

import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import com.tonapps.log.L
import com.tonapps.mvi.thread.MviThread
import com.tonapps.mvi.thread.StateThread
import com.tonapps.paging.contract.MviPagingMutation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PagingController<Id, Value>(
    private val config: PagingConfig<Id, Value>,
    private val loader: PagingLoader<Id, Value>,
    private val mutation: MviPagingMutation<Value>,
) {

    private var state = PagingState<Id>(null, 0)
    private var stage = PagingStage.None
    private var pagingData = PagingData.None
    private var version = 0
    private var isDestroyed = false

    private val mainDispatcher = Dispatchers.Main
    private val bgDispatcher = Dispatchers.Default

    val stageRelay = MutableSharedFlow<PagingStage>(
        1,
        1,
        BufferOverflow.DROP_OLDEST
    )

    @AnyThread
    fun onLoad() {
        config.stateScope.launch {
            if (stage != PagingStage.Pending) {
                return@launch
            }

            updateState(PagingStage.Loading)
            innerLoad()
        }
    }

    @AnyThread
    fun onPreload() {
        config.stateScope.launch {
            if (stage != PagingStage.None) {
                return@launch
            }

            updateState(PagingStage.Preload)
            innerLoad()
        }
    }

    @AnyThread
    fun onRecover() {
        config.stateScope.launch {
            if (stage != PagingStage.Error) {
                return@launch
            }

            if (pagingData == PagingData.Synced) {
                updateState(PagingStage.Loading)
            } else {
                updateState(PagingStage.Preload)
            }

            innerLoad()
        }
    }

    @AnyThread
    fun onRefresh() {
        config.stateScope.launch {
            if (stage == PagingStage.Refresh) {
                return@launch
            }

            L.d("Paging onRefresh")
            version++
            updateState(PagingStage.Refresh)
            state = PagingState(null, 0)

            innerLoad()
        }
    }

    @MainThread
    fun onDestroy() {
        MviThread.Main.require()
        isDestroyed = true
    }

    @StateThread
    private suspend fun innerLoad() {
        MviThread.State.require()

        val lastVersion = version
        val lastStage = stage
        val lastState = state
        L.d("Paging [$lastVersion] | innerLoad | lastStage: $lastStage - lastState: $lastState")

        if (isDestroyed) {
            return
        }

        if (lastStage == PagingStage.Preload) {
            val cached = loader.loadCache(config)
            L.d("Paging [$lastVersion] | cached | lastStage: $lastStage - lastState: $lastState")
            if (cached != null) {
                updateDataState(newStage = PagingData.Cache)
                withContext(mainDispatcher) {
                    mutation.submit(cached)
                }
            }
        }

        val result = withContext(bgDispatcher) {
            loader.loadPage(config, lastStage, lastState)
        }

        L.d("Paging [$lastVersion] | innerLoaded | lastStage: $lastStage - lastState: $lastState")

        if (lastVersion != version || isDestroyed) {
            L.d("Paging return because of version")
            return
        }

        when (result) {
            is PagingResult.Page -> {
                updateDataState(newStage = PagingData.Synced)

                // TODO Update state with the same UI loop
                if (result.data.isNotEmpty()) {
                    val size = state.itemsSize + result.data.size
                    state = PagingState(result.nextKey, size)
                    updateState(if (result.nextKey != null) PagingStage.Pending else PagingStage.End)
                } else {
                    updateState(PagingStage.End)
                }

                L.d("Paging [$lastVersion] | newPage | lastStage: $lastStage - lastState: $lastState")

                // TODO To BG
                withContext(mainDispatcher) {
                    if (!isDestroyed) {
                        L.d("Paging [$lastVersion] | updateState | lastStage: $lastStage - lastState: $lastState")
                        when (lastStage) {
                            PagingStage.Preload,
                            PagingStage.Refresh -> mutation.submit(result.data)
                            else -> mutation.insertAll(result.data)
                        }
                    }
                }
            }

            is PagingResult.Error -> {
                // TODO notify when error but has cache
                updateState(PagingStage.Error)
            }
        }
    }

    @StateThread
    // TODO check race condition
    private fun updateDataState(newStage: PagingData) {
        pagingData = newStage
    }

    @StateThread
    private fun updateState(newStage: PagingStage) {
        MviThread.State.require()
        stage = newStage
        stageRelay.tryEmit(newStage)
    }
}
