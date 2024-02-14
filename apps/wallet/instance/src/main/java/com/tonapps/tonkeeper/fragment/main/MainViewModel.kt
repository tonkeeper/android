package com.tonapps.tonkeeper.fragment.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel: ViewModel() {

    private val _childTopScrolled: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val childTopScrolled = _childTopScrolled.asStateFlow()

    private val _childBottomScrolled: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val childBottomScrolled = _childBottomScrolled.asStateFlow()


    fun setTopScrolled(value: Boolean) {
        _childTopScrolled.value = value
    }

    fun setBottomScrolled(value: Boolean) {
        _childBottomScrolled.value = value
    }
}