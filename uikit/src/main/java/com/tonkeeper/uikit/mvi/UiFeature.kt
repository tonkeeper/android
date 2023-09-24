package com.tonkeeper.uikit.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

abstract class UiFeature<S: UiState>(
    initState: S
): ViewModel() {

    private val _uiState = MutableStateFlow(initState)
    val uiState: StateFlow<S> = _uiState.asStateFlow()


    protected fun updateUiState(function: (currentState: S) -> S) {
        _uiState.update(function)
    }

}