package com.tonapps.tonkeeper.fragment.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel: ViewModel() {

    private val _childTopScrolled: MutableSharedFlow<Boolean> = MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val childTopScrolled = _childTopScrolled.asSharedFlow()

    private val _childBottomScrolled: MutableSharedFlow<Boolean> = MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val childBottomScrolled = _childBottomScrolled.asSharedFlow()


    fun setTopScrolled(value: Boolean) {
        _childTopScrolled.tryEmit(value)
    }

    fun setBottomScrolled(value: Boolean) {
        _childBottomScrolled.tryEmit(value)
    }
}