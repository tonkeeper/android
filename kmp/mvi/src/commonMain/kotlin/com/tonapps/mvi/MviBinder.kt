package com.tonapps.mvi

import androidx.compose.ui.util.fastForEachIndexed
import com.tonapps.mvi.props.MviMutableProperty
import com.tonapps.mvi.props.MviProperty
import com.tonapps.mvi.props.MviPropertyLiveData
import com.tonapps.mvi.thread.ComputationThread
import com.tonapps.mvi.thread.MainThread
import com.tonapps.mvi.thread.MviThread
import kotlinx.coroutines.CoroutineScope

class MviBinder<S>(
    private val bindings: List<ViewBinding<S, Any?>>,
    private val initStates: Array<Any?>,
    private val emitterScope: CoroutineScope,
) {

    private val pool = ArrayPool(bindings.size)

    @ComputationThread
    internal fun update(state: S) {
        bindings.fastForEachIndexed { index, bind ->
            val newState = bind.mapper.map(state)
            bind.property.setValue(newState)
        }
    }

    fun interface Mapper<S, R : Any?> {
        fun map(state: S): R
    }

    data class ViewBinding<S, T>(
        val property: MviMutableProperty<T>,
        val mapper: Mapper<S, out T>,
    )

    private class ArrayPool(private val size: Int) {
        // TODO v3 make thread safe
        private val queue = ArrayDeque<Array<Any?>>()

        @ComputationThread
        fun get(): Array<Any?> {
            MviThread.Computation.require()
            return queue.removeFirstOrNull() ?: arrayOfNulls(size)
        }

        @MainThread
        fun recycle(array: Array<Any?>) {
            MviThread.Main.require()
            array.fill(NULL)
            queue.add(array)
        }
    }

    class Builder<S>(
        private val initState: S,
        private val emitterScope: CoroutineScope,
    ) {
        private val bindings = mutableListOf<ViewBinding<S, Any?>>()
        private val states = mutableListOf<Any?>()

        @Suppress("UNCHECKED_CAST")
        @MainThread
        fun <T : Any?> mviProperty(
            strategy: ChangeStrategy<T> = ChangeStrategy.Value(),
            setter: Mapper<S, T>,
        ): MviProperty<T> {
            MviThread.Main.require()
            val initState = setter.map(initState)

            states.add(initState)

            return MviPropertyLiveData(initState, strategy)
                .also {
                    bindings.add(ViewBinding(it, setter) as ViewBinding<S, Any?>)
                }
        }

        @MainThread
        fun build(): MviBinder<S> {
            MviThread.Main.require()
            return MviBinder(bindings, states.toTypedArray(), emitterScope)
        }
    }

    private companion object {
        private val NULL = Any()
    }
}
