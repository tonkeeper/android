package com.tonapps.tonkeeper.ui.screen.qr

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.core.entities.AssetsEntity
import com.tonapps.tonkeeper.core.entities.AssetsExtendedEntity
import com.tonapps.tonkeeper.extensions.isSafeModeEnabled
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class QRViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val initialToken: TokenEntity,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
    private val tokenRepository: TokenRepository,
    private val api: API,
) : BaseWalletVM(app) {

    enum class Tab {
        TON, TRON
    }

    private val safeMode: Boolean = settingsRepository.isSafeModeEnabled(api)

    val installId: String
        get() = settingsRepository.installId

    val tronUsdtEnabled: Boolean
        get() = settingsRepository.getTronUsdtEnabled(wallet.id)

    var token: TokenEntity by mutableStateOf(initialToken)
        private set

    var address: String by mutableStateOf("")
        private set

    private lateinit var tronAddress: String

    private val tokensFlow = settingsRepository.tokenPrefsChangedFlow.map { _ ->
        tokenRepository.mustGet(settingsRepository.currency, wallet.accountId, wallet.testnet)
            .mapNotNull { token ->
                if (safeMode && !token.verified) {
                    return@mapNotNull null
                }
                AssetsExtendedEntity(
                    raw = AssetsEntity.Token(token),
                    prefs = settingsRepository.getTokenPrefs(
                        wallet.id,
                        token.address,
                        token.blacklist
                    ),
                    accountId = wallet.accountId,
                )
            }.filter { !it.isTon }.sortedBy { it.index }
    }

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

    suspend fun enableTron() {
        val tokens = tokensFlow.first()
        val usdtIndex = tokens.indexOfFirst { it.address == TokenEntity.USDT.address }
        val sortAddresses = mutableListOf<String>()
        tokens.forEachIndexed { index, token ->
            sortAddresses.add(token.address)
            if (index == usdtIndex + 1 && token.address != TokenEntity.TRON_USDT.address) {
                sortAddresses.add(TokenEntity.TRON_USDT.address)
            }
        }
        settingsRepository.setTokenHidden(wallet.id, TokenEntity.TRON_USDT.address, false)
        settingsRepository.setTokenPinned(wallet.id, TokenEntity.TRON_USDT.address, true)
        settingsRepository.setTokensSort(wallet.id, sortAddresses)
        setTron()
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