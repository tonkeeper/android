package com.tonapps.tonkeeper.ui.screen.browser.main

import android.app.Application
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import java.util.Locale

class BrowserMainViewModel(
    app: Application,
    private val settings: SettingsRepository
): BaseWalletVM(app) {

    private val _childBottomScrolled = MutableEffectFlow<Boolean>()
    val childBottomScrolled = _childBottomScrolled.asSharedFlow()

    private val _childTopScrolled = MutableEffectFlow<Boolean>()
    val childTopScrolled = _childTopScrolled.asSharedFlow()

    val countryFlow = settings.countryFlow.map {
        Locale("", it)
    }


    fun setBottomScrolled(value: Boolean) {
        _childBottomScrolled.tryEmit(value)
    }

    fun setTopScrolled(value: Boolean) {
        _childTopScrolled.tryEmit(value)
    }

}