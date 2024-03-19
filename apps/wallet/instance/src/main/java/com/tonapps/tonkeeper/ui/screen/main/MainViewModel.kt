package com.tonapps.tonkeeper.ui.screen.main

import androidx.lifecycle.ViewModel
import com.tonapps.extensions.MutableEffectFlow
import kotlinx.coroutines.flow.asSharedFlow

class MainViewModel: ViewModel() {

    private val _childBottomScrolled = MutableEffectFlow<Boolean>()
    val childBottomScrolled = _childBottomScrolled.asSharedFlow()

    fun setBottomScrolled(value: Boolean) {
        _childBottomScrolled.tryEmit(value)
    }
}