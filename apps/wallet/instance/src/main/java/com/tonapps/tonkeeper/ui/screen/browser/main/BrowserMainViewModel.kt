package com.tonapps.tonkeeper.ui.screen.browser.main

import androidx.lifecycle.ViewModel
import com.tonapps.extensions.MutableEffectFlow
import kotlinx.coroutines.flow.asSharedFlow

class BrowserMainViewModel: ViewModel() {

    private val _childBottomScrolled = MutableEffectFlow<Boolean>()
    val childBottomScrolled = _childBottomScrolled.asSharedFlow()

    private val _childTopScrolled = MutableEffectFlow<Boolean>()
    val childTopScrolled = _childTopScrolled.asSharedFlow()


    fun setBottomScrolled(value: Boolean) {
        _childBottomScrolled.tryEmit(value)
    }

    fun setTopScrolled(value: Boolean) {
        _childTopScrolled.tryEmit(value)
    }

}