package com.tonapps.deposit.screens.qr

import com.tonapps.blockchain.contract.Blockchain
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events
import com.tonapps.deposit.screens.qr.QrAssetFeature.Keys.Tokens
import com.tonapps.extensions.CacheKey
import com.tonapps.extensions.TimedCacheMemory
import com.tonapps.legacy.enteties.AssetsEntity
import com.tonapps.legacy.enteties.AssetsExtendedEntity
import com.tonapps.log.L
import com.tonapps.mvi.MviFeature
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviState
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.props.MviProperty
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import kotlinx.coroutines.CancellationException

sealed interface QrAssetAction : MviAction {
    object Init : QrAssetAction
    data class SelectTab(val tab: QrAssetTab) : QrAssetAction
}

enum class QrAssetTab {
    TON, TRON
}

data class QrAssetSelectedData(
    val token: TokenEntity,
    val address: String,
    val qrContent: String,
    val wallet: WalletEntity,
    val isBatteryEnabled: Boolean,
)

data class QrAssetState(
    val data: QrAssetSelectedData? = null,
    val isTabsVisible: Boolean = false,
    val isError: Boolean = false,
) : MviState

class QrAssetViewState(
    val global: MviProperty<QrAssetState>
) : MviViewState

data class QrAssetData(
    val token: TokenEntity? = null,
    val withBuyButton: Boolean = false,
    val walletId: String? = null,
)

class QrAssetFeature(
    private val data: QrAssetData,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
    private val tokenRepository: TokenRepository,
    private val api: API,
) : MviFeature<QrAssetAction, QrAssetState, QrAssetViewState>(
    initState = QrAssetState(),
    initAction = QrAssetAction.Init
) {

    interface Keys : CacheKey {
        data object Tokens : Keys
    }

    private val tokensCache = TimedCacheMemory<Keys>()

    init {
        AnalyticsHelper.Default.simpleTrackEvent("receive_open")
        AnalyticsHelper.Default.events.depositFlow.depositViewReceiveTokens(
            from = Events.DepositFlow.DepositFlowFrom.WalletScreen,
            addFundsOption = Events.DepositFlow.DepositFlowAddFundsOption.ReceiveTokens,
            network = Events.DepositFlow.DepositFlowNetwork.TON,
        )
    }

    override fun createViewState(): QrAssetViewState {
        return buildViewState {
            QrAssetViewState(mviProperty { it })
        }
    }

    private fun getDefaultToken(): TokenEntity {
        return data.token ?: TokenEntity.TON
    }

    override suspend fun executeAction(action: QrAssetAction) {
        when (action) {
            is QrAssetAction.Init -> init()
            is QrAssetAction.SelectTab -> {
                when (action.tab) {
                    QrAssetTab.TON -> selectToken(token = TokenEntity.TON)
                    QrAssetTab.TRON -> selectToken(token = TokenEntity.TRON_USDT)
                }
            }
        }
    }

    private suspend fun init() {
        val wallet = try {
            val resolved = resolveWallet()
            val token = getDefaultToken()
            val address = when (token.blockchain) {
                Blockchain.TON -> resolved.address
                Blockchain.TRON -> accountRepository.getTronAddress(resolved.id)
            }
            if (address == null) {
                setState { copy(isError = true) }
                return
            }
            val qrContent = getQrContent(address, token, resolved)
            val isBatteryEnabled = !api.getConfig(resolved.network).flags.disableBattery

            setState {
                copy(
                    data = QrAssetSelectedData(
                        token = token,
                        address = address,
                        qrContent = qrContent,
                        wallet = resolved,
                        isBatteryEnabled = isBatteryEnabled,
                    ),
                )
            }
            resolved
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e(e)
            setState { copy(isError = true) }
            return
        }

        val tokens = getTokens(wallet)
        val hasTronBalance = tokens.any { it.isTrc20Usdt && it.balance.value.isPositive }
        val hasToken = data.token != null
        val isTronEnabled = !api.getConfig(wallet.network).flags.disableTron
        val isTabsVisible =
            !hasToken && wallet.hasPrivateKey && !wallet.testnet && (isTronEnabled || hasTronBalance)

        setState { copy(isTabsVisible = isTabsVisible) }
    }

    private suspend fun selectToken(token: TokenEntity) {
        val wallet = resolveWallet()
        val tokens = getTokens(wallet)
        updateToken(tokens, token, wallet)
    }

    private suspend fun getTokens(wallet: WalletEntity): List<AssetsExtendedEntity> {
        return tokensCache.getOrLoad(Tokens) {
            val isSafeMode = settingsRepository.isSafeModeEnabled(wallet.id, wallet.network)

            tokenRepository.mustGet(
                settingsRepository.currency,
                wallet.accountId,
                wallet.network,
            )
                .mapNotNull { token ->
                    if (isSafeMode && !token.verified) {
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
                }
                .filter { !it.isTon }
                .sortedBy { it.index }
        }
    }

    private suspend fun updateToken(
        tokens: List<AssetsExtendedEntity>,
        token: TokenEntity,
        wallet: WalletEntity
    ) {
        val hasTronBalance = tokens.any {
            it.isTrc20Usdt && it.balance.value.isPositive
        }

        val hasToken = data.token != null
        val isTronEnabled = !api.getConfig(wallet.network).flags.disableTron
        val isBatteryEnabled = !api.getConfig(wallet.network).flags.disableBattery
        val isTabsVisible =
            !hasToken && wallet.hasPrivateKey && !wallet.testnet && (isTronEnabled || hasTronBalance)

        val activeAddress = when (token.blockchain) {
            Blockchain.TON -> wallet.address
            Blockchain.TRON -> accountRepository.getTronAddress(wallet.id)
        }
        if (activeAddress == null) {
            return
        }

        val qrContent = getQrContent(activeAddress, token, wallet)
        val data = QrAssetSelectedData(
            token = token,
            address = activeAddress,
            qrContent = qrContent,
            wallet = wallet,
            isBatteryEnabled = isBatteryEnabled
        )

        setState { copy(data = data, isTabsVisible = isTabsVisible) }
    }

    private suspend fun resolveWallet(): WalletEntity {
        val walletId = data.walletId
        if (walletId != null) {
            return accountRepository.getWalletById(walletId)
                ?: accountRepository.requiredSelectedWallet()
        }
        return accountRepository.requiredSelectedWallet()
    }

    private fun getQrContent(address: String, token: TokenEntity, wallet: WalletEntity): String {
        if (token.isUsdtTrc20 || token.isTrx) {
            return address
        }

        var value = "ton://transfer/${address}"
        if (!token.isTon) {
            value += "?jetton=${
                token.address.toUserFriendly(
                    wallet = false,
                    testnet = wallet.type == WalletType.Testnet
                )
            }"
        }

        return value
    }
}
