package com.tonapps.paging.collection

import androidx.annotation.MainThread
import com.tonapps.mvi.thread.MviThread
import com.tonapps.paging.contract.MviPagingMutation

// TODO Remove
class MviPagingMutationProxy<Item>(
    private val onChangeListener: () -> Unit
) : MviPagingMutation<Item> {

    private val array = PagingArrayList<Item>()

    @MainThread
    fun getByPosition(index: Int): Item? {
        MviThread.Main.require()
        return array.getByPosition(index)
    }

    @MainThread
    fun size(): Int {
        MviThread.Main.require()
        return array.size()
    }

    @MainThread
    fun recycle() {
        MviThread.Main.require()
        array.recycle()
    }

    @MainThread
    override fun insert(item: Item) {
        MviThread.Main.require()
        array.insert(item)
        onChanged()
    }

    @MainThread
    override fun insertAll(items: List<Item>) {
        MviThread.Main.require()
        array.insertAll(items)
        onChanged()
    }

    @MainThread
    override fun insertHead(item: Item) {
        MviThread.Main.require()
        array.insertHead(item)
        onChanged()
    }

    @MainThread
    override fun insertHeadAll(items: List<Item>) {
        MviThread.Main.require()
        array.insertHeadAll(items)
        onChanged()
    }

    @MainThread
    override fun update(item: Item): Boolean {
        MviThread.Main.require()
        val result = array.update(item)
        if (result) {
            onChanged()
        }
        return result
    }

    @MainThread
    override fun updateAll(items: List<Item>) {
        MviThread.Main.require()
        array.updateAll(items)
        onChanged()
    }

    @MainThread
    override fun delete(item: Item): Boolean {
        MviThread.Main.require()
        val result = array.delete(item)
        if (result) {
            onChanged()
        }
        return result
    }

    @MainThread
    override fun submit(items: List<Item>) {
        MviThread.Main.require()
        array.submit(items)
        onChanged()
    }

    @MainThread
    private fun onChanged() {
        MviThread.Main.require()
        onChangeListener()
    }

    @MainThread
    override fun clear() {
        MviThread.Main.require()
        array.clear()
    }
}
