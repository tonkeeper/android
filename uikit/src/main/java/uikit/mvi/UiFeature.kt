package uikit.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

abstract class UiFeature<S: UiState, E: UiEffect>(
    initState: S
): ViewModel() {

    private val _uiState = MutableStateFlow(initState)
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    private val _uiEffect = MutableStateFlow(null as E?)
    val uiEffect: StateFlow<E?> = _uiEffect.asStateFlow()

    fun sendEffect(effect: E) {
        _uiEffect.value = effect
    }

    protected fun updateUiState(function: (currentState: S) -> S) {
        _uiState.update(function)
    }

    fun destroy() {

    }
}