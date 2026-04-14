package com.tonapps.mvi

interface ChangeStrategy<T> {
    fun isChanged(oldValue: T, newValue: T): Boolean

    @Suppress("FunctionName")
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T> Ref(): ChangeStrategy<T> = RefChangeStrategy as ChangeStrategy<T>

        @Suppress("UNCHECKED_CAST")
        fun <T> Value(): ChangeStrategy<T> = ValueChangeStrategy as ChangeStrategy<T>

        @Suppress("UNCHECKED_CAST")
        fun <T> Hash(): ChangeStrategy<T> = HashCodeChangeStrategy as ChangeStrategy<T>
    }
}

private object RefChangeStrategy : ChangeStrategy<Any?> {
    override fun isChanged(oldValue: Any?, newValue: Any?): Boolean {
        return oldValue !== newValue
    }
}

private object ValueChangeStrategy : ChangeStrategy<Any?> {
    override fun isChanged(oldValue: Any?, newValue: Any?): Boolean {
        return oldValue != newValue
    }
}

private object HashCodeChangeStrategy : ChangeStrategy<Any?> {
    override fun isChanged(oldValue: Any?, newValue: Any?): Boolean {
        return oldValue.hashCode() != newValue.hashCode()
    }
}