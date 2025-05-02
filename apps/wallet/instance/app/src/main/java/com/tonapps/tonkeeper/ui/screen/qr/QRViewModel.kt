package com.tonapps.tonkeeper.ui.screen.qr

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.launch

class QRViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val initialToken: TokenEntity,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
) : BaseWalletVM(app) {

    enum class Tab {
        TON, TRON
    }

    val installId: String
        get() = settingsRepository.installId

    val tronUsdtEnabled: Boolean
        get() = settingsRepository.getTronUsdtEnabled(wallet.id)

    var token: TokenEntity by mutableStateOf(initialToken)
        private set

    var address: String by mutableStateOf("")
        private set

    private lateinit var tronAddress: String

    init {
        viewModelScope.launch {
            tronAddress = accountRepository.getTronAddress(wallet.id) ?: ""
            address = if (token.isTrc20) {
                tronAddress
            } else {
                wallet.address
            }
        }
    }

    fun setTron() {
        token = TokenEntity.TRON_USDT
        address = tronAddress
    }

    fun setTon() {
        token = TokenEntity.TON
        address = wallet.address
    }

}