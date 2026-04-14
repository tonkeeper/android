package com.tonapps.tonkeeper.ui.screen.battery

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.battery.recharge.BatteryRechargeScreen
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class BatteryViewModel(
    app: Application,
    private val wallet: WalletEntity,
    jetton: String,
    private val settingsRepository: SettingsRepository,
    private val batteryRepository: BatteryRepository,
    private val tokenRepository: TokenRepository,
    private val accountRepository: AccountRepository,
) : BaseWalletVM(app) {

    private val _routeFlow = MutableEffectFlow<BatteryRoute>()
    val routeFlow = _routeFlow.asSharedFlow().filterNotNull()

    val installId: String
        get() = settingsRepository.installId

    init {
        routeToRefill()
        if (jetton.isNotEmpty()) {
            openRecharge(jetton)
        }
    }

    private fun openRecharge(jetton: String) {
        viewModelScope.launch {
            val rechargeToken =
                batteryRepository.getRechargeMethodByJetton(wallet.network, jetton)?.jettonMaster
                    ?: "TON"
            val tokens =
                tokenRepository.get(settingsRepository.currency, wallet.accountId, wallet.network)
                    ?: return@launch
            val token =
                tokens.firstOrNull { it.address.equalsAddress(rechargeToken) } ?: return@launch
            openScreen(BatteryRechargeScreen.newInstance(wallet, token))
        }
    }

    fun routeToSettings() {
        _routeFlow.tryEmit(BatteryRoute.Settings)
    }

    private fun routeToRefill() {
        _routeFlow.tryEmit(BatteryRoute.Refill)
    }

    fun setBatteryViewed() {
        if (!settingsRepository.batteryViewed) {
            viewModelScope.launch {
                settingsRepository.batteryViewed = true
                val tonProofToken =
                    accountRepository.requestTonProofToken(wallet) ?: return@launch
                batteryRepository.getBalance(
                    tonProofToken = tonProofToken,
                    publicKey = wallet.publicKey,
                    network = wallet.network,
                    ignoreCache = true
                )
            }
        }
    }
}