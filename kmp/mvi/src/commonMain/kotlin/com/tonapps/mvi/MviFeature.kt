package com.tonapps.mvi

import androidx.annotation.AnyThread
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import com.tonapps.log.L
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviState
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.contract.internal.Stateful
import com.tonapps.mvi.thread.MainThread
import com.tonapps.mvi.thread.MviThread
import com.tonapps.mvi.thread.StateThread
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

@Stable
abstract class MviFeature<A : MviAction, S : MviState, VS : MviViewState>(
    initState: S,
    initAction: A? = null
) : AsyncViewModel(), Stateful<S> {

    @Volatile
    private var innerState = initState
    val state: VS get() = viewState

    private lateinit var binding: MviBinder<S>
    private val viewState: VS

    init {
        @Suppress("LeakingThis")
        viewState = createViewState()

        if (initAction != null) {
            sendAction(initAction)
        }
    }

    @AnyThread
    fun sendAction(action: A) {
        stateScope.launch {
            try {
                executeAction(action)
            } catch (e: Throwable) {
                L.e(e)
            }
        }
    }

    @StateThread
    protected abstract fun createViewState(): VS

    @StateThread
    protected abstract suspend fun executeAction(action: A)

    @MainThread
    protected fun buildViewState(filler: @MainThread MviBinder.Builder<S>.() -> VS): VS {
        val builder = MviBinder.Builder(innerState, viewModelScope)
        val state = builder.filler()
        binding = builder.build()
        return state
    }

    @StateThread
    internal fun internalObtainState(): S {
        MviThread.State.require()
        return innerState
    }

    @StateThread
    override fun Stateful<S>.obtainState(): S {
        MviThread.State.require()
        return innerState
    }

    @AnyThread
    internal fun internalSetState(mapper: @StateThread S.() -> S) {
        setState(mapper)
    }

    @AnyThread
    override fun Stateful<S>.setState(mapper: @StateThread S.() -> S) {
        if (MviThread.State.check()) {
            val newValue = innerState.mapper()
            updateState(newValue)
        } else {
            stateScope.launch {
                val newValue = innerState.mapper()
                updateState(newValue)
            }
        }
    }

    @AnyThread
    internal fun <State : S> internalSetState(
        failure: @StateThread (() -> Unit)? = null,
        mapper: @StateThread State.() -> S,
    ) {
        setState(failure, mapper)
    }

    @AnyThread
    override fun <State : S> Stateful<S>.setState(
        failure: @StateThread (() -> Unit)?,
        mapper: @StateThread State.() -> S,
    ) {
        if (MviThread.State.check()) {
            innerSetState(failure, mapper)
        } else {
            stateScope.launch {
                innerSetState(failure, mapper)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    @AnyThread
    private fun <State : S> Stateful<S>.innerSetState(
        failure: @StateThread (() -> Unit)?,
        mapper: @StateThread State.() -> S,
    ) {
        val currentValue = obtainState()
        val state = currentValue as? State
        if (state != null) {
            val newValue = currentValue.mapper()
            updateState(newValue)
        } else {
            failure?.invoke()
        }
    }

    @StateThread
    internal fun <T : S> internalOnState() : T? {
        return obtainSpecificState()
    }

    @Suppress("UNCHECKED_CAST")
    @StateThread
    override fun <T : S> Stateful<S>.obtainSpecificState() : T? {
        return obtainState() as? T
    }

    @StateThread
    private fun <ES : S> updateState(newState: ES) {
        MviThread.State.require()
        innerState = newState

        try {
            binding.update(newState)
        } catch (e: Throwable) {
            L.e(e)

            if (Mvi.config().isFastFail) {
                MviThread.Computation.finish()
            }
        }
    }
}
