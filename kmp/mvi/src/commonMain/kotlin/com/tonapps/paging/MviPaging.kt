package com.tonapps.paging

import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.neverEqualPolicy
import com.tonapps.mvi.thread.MviThread
import com.tonapps.paging.collection.MviPagingMutationProxy
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Stable
class MviPaging<Id, Item>(
    val config: PagingConfig<Id, Item>,
    val loader: PagingLoader<Id, Item>,
) {
    private val state = MviPagingMutationProxy<Item>(
        onChangeListener = ::update
    )

    private val controller = PagingController(
        config = config,
        loader = loader,
        mutation = state,
    )

    internal val accessor = PagingAccessor(
        state = state,
        onScrollPosition = { position ->
            if (state.size() - position <= config.prefetchSize) {
                controller.onLoad()
            }
        },
    )

    val observer = MutableSharedFlow<PagingAccessor<Item>>(replay = 1, extraBufferCapacity = 1, BufferOverflow.DROP_OLDEST)
    val uiObserver = observer.asSharedFlow()

    val stage: SharedFlow<PagingStage> = controller.stageRelay

    // TODO improve
    @MainThread
    fun preload() {
        MviThread.Main.require()
        controller.onPreload()
    }

    @MainThread
    fun recover() {
        MviThread.Main.require()
        controller.onRecover()
    }

    @MainThread
    fun refresh() {
        MviThread.Main.require()
        controller.onRefresh()
    }

    @MainThread
    fun destroy() {
        MviThread.Main.require()
        controller.onDestroy()
    }

    private fun update() {
        observer.tryEmit(accessor.copy()) // TODO
    }
}

@Composable
fun <Id, Item> MviPaging<Id, Item>.rememberPaging(): PagingAccessor<Item> {
    val accessor by uiObserver.collectAsStateWorkaround(initial = accessor, policy = neverEqualPolicy())
    return accessor
}

@Composable
fun <Id, Item> MviPaging<Id, Item>.rememberStage(): PagingStage? {
    val stage by stage.collectAsState(null)
    return stage
}
