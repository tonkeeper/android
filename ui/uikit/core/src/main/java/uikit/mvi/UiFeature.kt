package uikit.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Deprecated("Use default ViewModel and Flow logic")
abstract class UiFeature<S: UiState, E: UiEffect>(
    initState: S
): ViewModel() {

    private val _uiState = MutableStateFlow(initState)
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<E>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val uiEffect: SharedFlow<E?> = _uiEffect.asSharedFlow()

    fun sendEffect(effect: E) {
        _uiEffect.tryEmit(effect)
    }

    protected fun updateUiState(function: (currentState: S) -> S) {
        _uiState.update(function)
    }

    fun destroy() {

    }
}