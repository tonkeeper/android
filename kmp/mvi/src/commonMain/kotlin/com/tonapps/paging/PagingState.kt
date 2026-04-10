package com.tonapps.paging

import kotlinx.coroutines.CoroutineScope

data class PagingConfig<Id, Value>(
    val initPage: Int,
    val pageSize: Int,
    val prefetchSize: Int,
    val stateScope: CoroutineScope
)

data class PagingState<Id>(
    val nextKey: Id?,
    val itemsSize: Int,
)

sealed interface PagingResult<Id, Value> {
    data class Error<Id, Value>(
        val error: Throwable
    ) : PagingResult<Id, Value>

    data class Page<Id, Value>(
        val data: List<Value>,
        val nextKey: Id?
    ) : PagingResult<Id, Value>
}

enum class PagingStage {
    None,
    Preload,
    Refresh,
    Loading,
    Pending,
    Error,
    End;
}

enum class PagingData {
    None,
    Cache,
    Synced;
}