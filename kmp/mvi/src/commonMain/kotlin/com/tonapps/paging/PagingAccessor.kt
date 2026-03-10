package com.tonapps.paging

import androidx.annotation.MainThread
import com.tonapps.mvi.thread.MviThread
import com.tonapps.paging.collection.MviPagingMutationProxy

data class PagingAccessor<Item>(
    val state: MviPagingMutationProxy<Item>,
    private val onScrollPosition: (Int) -> Unit
) {

    @MainThread
    fun size(): Int {
        MviThread.Main.require()
        return state.size()
    }

    @MainThread
    fun isEmpty(): Boolean {
        MviThread.Main.require()
        return state.size() == 0
    }

    @MainThread
    fun get(index: Int): Item? {
        MviThread.Main.require()
        return state.getByPosition(index)
    }

    @MainThread
    fun require(index: Int): Item {
        MviThread.Main.require()
        return get(index)!!
    }

    @MainThread
    fun scroll(index: Int): Item? {
        MviThread.Main.require()
        onScrollPosition.invoke(index)
        return state.getByPosition(index)
    }
}
