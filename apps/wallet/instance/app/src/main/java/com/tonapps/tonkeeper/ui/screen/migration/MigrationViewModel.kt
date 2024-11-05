package com.tonapps.tonkeeper.ui.screen.migration

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.data.rn.RNLegacy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class MigrationViewModel(
    app: Application,
    private val rnLegacy: RNLegacy,
): BaseWalletVM(app) {

    data class LegacyState(
        val walletsCount: Int,
        val biometryEnabled: Boolean,
        val lockScreenEnabled: Boolean,
        val selectedIdentifier: String
    )

    private val _legacyStateFlow = MutableStateFlow<LegacyState?>(null)
    val legacyStateFlow = _legacyStateFlow.asStateFlow().filterNotNull()

    init {
        viewModelScope.launch {
            val wallets = rnLegacy.getWallets()
            _legacyStateFlow.value = LegacyState(
                walletsCount = wallets.wallets.size,
                biometryEnabled = wallets.biometryEnabled,
                lockScreenEnabled = wallets.lockScreenEnabled,
                selectedIdentifier = wallets.selectedIdentifier
            )
        }

    }
}