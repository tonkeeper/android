package com.tonapps.tonkeeper.ui.screen.browser.base

import android.app.Application
import android.util.Log
import androidx.core.view.WindowInsetsCompat
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.tonkeeper.extensions.getFixedCountryCode
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.browser.BrowserRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

class BrowserBaseViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val browserRepository: BrowserRepository,
    private val settingsRepository: SettingsRepository,
    private val api: API,
): BaseWalletVM(app) {

    private val _childBottomScrolled = MutableEffectFlow<Boolean>()
    val childBottomScrolled = _childBottomScrolled.asSharedFlow()

    private val _insetsRootFlow = MutableEffectFlow<WindowInsetsCompat>()
    val insetsRootFlow = _insetsRootFlow.asSharedFlow()

    fun setInsetsRoot(value: WindowInsetsCompat) {
        _insetsRootFlow.tryEmit(value)
    }

    fun setBottomScrolled(value: Boolean) {
        _childBottomScrolled.tryEmit(value)
    }

    suspend fun hasCategory(category: String): Boolean = withContext(Dispatchers.IO) {
        val categories = browserRepository.loadCategories(
            country = settingsRepository.getFixedCountryCode(api),
            testnet = wallet.testnet,
            locale = settingsRepository.getLocale()
        )
        categories.any { it == category }
    }

}