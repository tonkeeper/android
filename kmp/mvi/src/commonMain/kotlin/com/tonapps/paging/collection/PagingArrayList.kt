package com.tonapps.paging.collection

import com.tonapps.paging.contract.MviPagingMutation

@Suppress("UNCHECKED_CAST")
internal class PagingArrayList<Item> : MviPagingMutation<Item> {

    internal object ArrayPool {

        private const val DEF_FACTOR = 2
        private const val DEF_START_SIZE = 50
        private const val DEF_MAX_CACHE = 200
        private const val DEF_START_PREFETCH = 5

        private val sizes = mutableMapOf<Int, ArrayDeque<Array<Any?>>>()

        init {
            val dequeue = ArrayDeque<Array<Any?>>()

            repeat(DEF_START_PREFETCH) {
                dequeue.add(array())
            }

            sizes.put(DEF_START_SIZE, dequeue)
        }

        fun recycle(old: Array<Any?>) {
            old.fill(null, 0, old.size)

            if (old.size < DEF_MAX_CACHE) {
                val recycle = sizes.get(old.size) ?: dequeue().apply { sizes.put(old.size, this) }
                recycle.add(old)
            }
        }

        fun obtain(old: Array<Any?>? = null, size: Int = -1): Array<Any?> {
            var final: Int

            if (old == null) {
                final = DEF_START_SIZE
            } else {
                final = old.size
                if (size > 0) {
                    while (final < size) {
                        final *= DEF_FACTOR
                    }
                } else {
                    final *= DEF_FACTOR
                }
            }

            if (old == null) {
                // create default
                return sizes[final]
                    ?.removeFirstOrNull()
                    ?: array()
            }

            // create new
            val oldSize = old.size
            val newSize = final

            val newArray = if (newSize < DEF_MAX_CACHE) {
                sizes[newSize]
                    ?.removeFirstOrNull()
                    ?: array(newSize)
            } else {
                array(newSize)
            }

            // move
            old.copyInto(newArray, destinationOffset = 0, startIndex = 0, endIndex = oldSize)

            // recycle
            recycle(old)

            return newArray
        }

        private fun dequeue(): ArrayDeque<Array<Any?>> {
            return ArrayDeque()
        }

        private fun array(size: Int = DEF_START_SIZE): Array<Any?> {
            return arrayOfNulls<Any?>(size)
        }
    }

    private class ListWrapper<Item>(
        private val arrayWrapper: MviArrayBuffer<Item>
    ) : AbstractList<Item>() {

        override val size: Int get() = arrayWrapper.size

        override fun get(index: Int): Item {
            return arrayWrapper.array[index] as? Item
                ?: throw IndexOutOfBoundsException("")
        }
    }

    internal interface MviListAccessor<Item> {
        val list: List<Item>
    }

    private class MviArrayBuffer<Item>(
        var array: Array<Any?> = ArrayPool.obtain(),
        var size: Int = 0
    ) : MviListAccessor<Item> {

        override val list: List<Item> by lazy(LazyThreadSafetyMode.NONE) { ListWrapper(this) }

        val capacity: Int get() = array.size

        fun arrayCopy(srcPos: Int, destPos: Int, length: Int) {
            array.copyInto(array, destinationOffset = destPos, startIndex = srcPos, endIndex = srcPos + length)
        }

        fun fillNull() {
            array.fill(null)
        }

        fun fillNull(from: Int, to: Int) {
            array.fill(null, from, to)
        }

        fun expandIfNeeded(size: Int) {
            if (size < capacity) {
                return
            }

            array = ArrayPool.obtain(array, size)
        }

        operator fun set(position: Int, value: Item) {
            array[position] = value
        }
    }

    private var buffer = MviArrayBuffer<Item>()

    fun getByPosition(position: Int): Item? {
        if (position < 0 || position >= buffer.size) {
            return null
        }
        return buffer.array[position] as? Item
    }

    fun indexOf(item: Item): Int {
        return buffer.array.indexIdOf(item, buffer.size)
    }

    fun size(): Int {
        return buffer.size
    }

    fun capacity(): Int {
        return buffer.capacity
    }

    fun array(): Array<Any?> {
        return buffer.array
    }

    internal fun unsafeList(): List<Item> {
        return buffer.list
    }

    fun safeList(): List<Item> {
        return buffer.array
            .copyOf(buffer.size)
            .toList() as List<Item>
    }

    override fun insert(item: Item) {
        expandIfNeeded(buffer.size)

        buffer[buffer.size] = item
        buffer.size++
    }

    override fun insertAll(items: List<Item>) {
        expandIfNeeded(buffer.size + items.size)

        items.forEach { item ->
            buffer[buffer.size] = item
            buffer.size++
        }
    }

    override fun insertHead(item: Item) {
        insert(0, 1, listOf(item))
    }

    override fun insertHeadAll(items: List<Item>) {
        insert(0, items.size, items)
    }

    override fun update(item: Item): Boolean {
        val index = indexOf(item)
        if (index < 0) {
            return false
        }

        buffer[index] = item

        return true
    }

    override fun updateAll(items: List<Item>) {
        items.forEach { item ->
            update(item)
        }
    }

    override fun delete(item: Item): Boolean {
        val index = indexOf(item)
        if (index < 0) {
            return false
        }

        remove(index, 1)
        return true
    }

    override fun submit(items: List<Item>) {
//        if (items is ArrayList) {
//            expandIfNeeded(items.size)
//
////            if (items.size < buffer.size) {
////                buffer.fillNull(0, buffer.size)
////            }
//
//            buffer.fillNull()
//            for (i in items.indices) {
//                buffer.array[i] = items[i]
//            }
//
//            buffer.size = items.size
//        } else {
            clear()
            insertAll(items)
//        }
    }

    override fun clear() {
        buffer.size = 0
        buffer.fillNull()
    }

    fun recycle() {
        ArrayPool.recycle(buffer.array)
    }

    override fun toString(): String {
        return buffer.array.joinToString(prefix = "[", postfix = "]")
    }

    private fun expandIfNeeded(size: Int) {
        buffer.expandIfNeeded(size)
    }

    private fun remove(position: Int, count: Int) {
        buffer.arrayCopy(position + count, position, buffer.size - (position + count))
        buffer.size -= count
        buffer.fillNull(buffer.size, buffer.size + count)
    }

    private fun insert(position: Int, count: Int, items: List<Item>) {
        expandIfNeeded(buffer.size + count)

        buffer.arrayCopy(position,position + count, buffer.size - position)

        for (i in 0 until count) {
            buffer[position + i] = items[i]
        }

        buffer.size += count
    }

    private fun change(position: Int, count: Int, items: List<Item>) {
        for (i in 0 until count) {
            buffer[position + i] = items[i]
        }
    }

    private fun move(position: Int, toPosition: Int) {
        val savedState = buffer.array[position] as Item

        if (position < toPosition) {
            buffer.arrayCopy(position + 1, position, toPosition - position)
        } else {
            buffer.arrayCopy(toPosition, toPosition + 1, position - toPosition)
        }

        buffer[toPosition] = savedState
    }

    // TODO N -> LogN/Const
    private fun Array<Any?>.indexIdOf(element: Item, searchSize: Int): Int {
        for (index in 0 until searchSize) {
            if (element == this[index]) {
                return index
            }
        }
        return -1
    }
}
