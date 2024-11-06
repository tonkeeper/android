package com.tonapps.tonkeeper.ui.screen.browser.main

import android.app.Application
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.tonkeeper.extensions.getLocaleCountryFlow
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.flow.asSharedFlow

class BrowserMainViewModel(
    app: Application,
    private val settings: SettingsRepository,
    private val api: API,
): BaseWalletVM(app) {

    private val _childBottomScrolled = MutableEffectFlow<Boolean>()
    val childBottomScrolled = _childBottomScrolled.asSharedFlow()

    private val _childTopScrolled = MutableEffectFlow<Boolean>()
    val childTopScrolled = _childTopScrolled.asSharedFlow()

    val countryFlow = settings.getLocaleCountryFlow(api)

    val installId: String
        get() = settings.installId

    fun setBottomScrolled(value: Boolean) {
        _childBottomScrolled.tryEmit(value)
    }

    fun setTopScrolled(value: Boolean) {
        _childTopScrolled.tryEmit(value)
    }

}