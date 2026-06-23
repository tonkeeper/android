package com.tonapps.paging.contract

import androidx.annotation.MainThread

interface MviPagingMutation<Item> {
    @MainThread
    fun insert(item: Item)
    @MainThread
    fun insertAll(items: List<Item>)

    @MainThread
    fun insertHead(item: Item)
    @MainThread
    fun insertHeadAll(items: List<Item>)

    @MainThread
    fun update(item: Item): Boolean
    @MainThread
    fun updateAll(items: List<Item>)

    @MainThread
    fun delete(item: Item): Boolean
    @MainThread
    fun submit(items: List<Item>)

    @MainThread
    fun clear()
}
