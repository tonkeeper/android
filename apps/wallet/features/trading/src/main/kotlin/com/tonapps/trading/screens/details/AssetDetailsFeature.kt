package com.tonapps.trading.screens.details

import com.tonapps.blockchain.contract.Blockchain
import com.tonapps.blockchain.model.legacy.BlockchainAddress
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.AssetScreen.AssetScreenButton
import com.tonapps.bus.generated.Events.AssetScreen.AssetScreenFrom
import com.tonapps.core.helper.ExternalLinkHelper
import com.tonapps.log.L
import com.tonapps.mvi.MviFeature
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviState
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.props.MviProperty
import com.tonapps.trading.asTokenEntity
import com.tonapps.trading.data.TradingRepository
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.tx.TransactionManager
import com.tonapps.wallet.data.events.tx.model.TxEvent
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.features.events.TxEventUiMapper
import io.tradingapi.models.AssetDetailsResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import uikit.chart.ChartPeriod
import uikit.chart.ChartPoint
import uikit.extensions.collectFlow

sealed interface AssetDetailsAction : MviAction {
    data object Init : AssetDetailsAction
    data object RefreshBalanceAndHistory : AssetDetailsAction
    data class SetChartPeriod(val period: ChartPeriod) : AssetDetailsAction
    data object OpenTokenExplorer : AssetDetailsAction
}

sealed interface AssetDetailsEvent {
    data class OpenUrl(val url: String) : AssetDetailsEvent
}

sealed interface AssetDetailsState : MviState {
    data object Loading : AssetDetailsState
    data object Error : AssetDetailsState
    data class Data(
        val details: AssetDetailsResponse,
        val currencyCode: String,
        val sections: AssetDetailsSections,
        val chartData: List<ChartPoint> = emptyList(),
        val chartPeriod: ChartPeriod = ChartPeriod.month,
        val isChartLoading: Boolean = false,
        val hiddenBalances: Boolean = false,
        val maxStakingApyFormatted: String? = null,
    ) : AssetDetailsState
}

class AssetDetailsViewState(
    val global: MviProperty<AssetDetailsState>,
) : MviViewState

@OptIn(ExperimentalCoroutinesApi::class)
class AssetDetailsFeature(
    private val tradingRepository: TradingRepository,
    private val tokenRepository: TokenRepository,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val eventsRepository: EventsRepository,
    private val stakingRepository: StakingRepository,
    private val txEventUiMapper: TxEventUiMapper,
    private val transactionManager: TransactionManager,
    private val api: API,
    val assetId: String,
    private val from: AssetScreenFrom,
) : MviFeature<AssetDetailsAction, AssetDetailsState, AssetDetailsViewState>(
    initState = AssetDetailsState.Loading,
    initAction = AssetDetailsAction.Init,
) {

    private val relay = MviRelay<AssetDetailsEvent>()
    val events: Flow<AssetDetailsEvent> = relay.events

    init {
        AnalyticsHelper.Default.events.assetScreen.assetView(from = from, asset = assetId)
        collectFlow(
            accountRepository.selectedWalletFlow.flatMapLatest { wallet ->
                transactionManager.eventsFlow(wallet)
            },
        ) {
            sendAction(AssetDetailsAction.RefreshBalanceAndHistory)
        }
        collectFlow(transactionManager.tronUpdatedFlow) {
            sendAction(AssetDetailsAction.RefreshBalanceAndHistory)
        }
    }

    fun trackButtonClick(button: AssetScreenButton) {
        AnalyticsHelper.Default.events.assetScreen.assetButtonClick(
            button = button,
            asset = assetId
        )
    }

    override fun createViewState(): AssetDetailsViewState {
        return buildViewState {
            AssetDetailsViewState(mviProperty { it })
        }
    }

    override suspend fun executeAction(action: AssetDetailsAction) {
        when (action) {
            AssetDetailsAction.Init -> load()
            AssetDetailsAction.RefreshBalanceAndHistory -> refreshBalanceAndHistory()
            is AssetDetailsAction.SetChartPeriod -> {
                val data = obtainSpecificState<AssetDetailsState.Data>() ?: return
                loadChart(data.details, action.period)
            }

            AssetDetailsAction.OpenTokenExplorer -> {
                val url = getExplorerUrl() ?: return
                relay.emit(AssetDetailsEvent.OpenUrl(url = url))
            }
        }
    }

    private suspend fun load() {
        setState { AssetDetailsState.Loading }
        try {
            val details = tradingRepository.getAssetDetails(assetId)
            val currencyCode = tradingRepository.getCurrency()
            val accountToken = getAccountToken(details)
            val recentEvents = loadTokenTransactions(details)
            val maxStakingApyFormatted = loadMaxStakingApy(details)
            val sections = AssetDetailsSections.build(
                details = details,
                currencyCode = currencyCode,
                hiddenBalances = settingsRepository.hiddenBalances,
                accountToken = accountToken,
                recentEvents = recentEvents,
            )
            setState {
                AssetDetailsState.Data(
                    details = details,
                    currencyCode = currencyCode,
                    sections = sections,
                    isChartLoading = true,
                    maxStakingApyFormatted = maxStakingApyFormatted,
                )
            }
            loadChart(details, ChartPeriod.month)
        } catch (e: Throwable) {
            L.e(e)
            setState { AssetDetailsState.Error }
        }
    }

    private suspend fun refreshBalanceAndHistory() {
        val data = obtainSpecificState<AssetDetailsState.Data>() ?: return
        val details = data.details
        val currencyCode = tradingRepository.getCurrency()
        val accountToken = getAccountToken(details, refresh = true)
        val recentEvents = loadTokenTransactions(details)
        val sections = AssetDetailsSections.build(
            details = details,
            currencyCode = currencyCode,
            hiddenBalances = settingsRepository.hiddenBalances,
            accountToken = accountToken,
            recentEvents = recentEvents,
        )
        setState {
            val d = this as? AssetDetailsState.Data ?: return@setState this
            d.copy(currencyCode = currencyCode, sections = sections)
        }
    }

    private suspend fun loadMaxStakingApy(details: AssetDetailsResponse): String? {
        return try {
            val token = details.asset.asTokenEntity
            if (!token.isTon) return null
            val wallet = accountRepository.getSelectedWallet() ?: return null
            val staking = stakingRepository.get(
                accountId = wallet.accountId,
                network = wallet.network,
                initializedAccount = wallet.initialized,
            )
            val enabledStaking = api.getConfig(wallet.network).enabledStaking
            val maxApy = staking.pools
                .filter { enabledStaking.contains(it.implementation.title) }
                .maxOfOrNull { it.apy }
                ?: return null
            CurrencyFormatter.formatPercent(maxApy).toString()
        } catch (e: Throwable) {
            L.e(e)
            null
        }
    }

    private suspend fun getAccountToken(details: AssetDetailsResponse, refresh: Boolean = false): AccountTokenEntity? {
        return try {
            val wallet = accountRepository.requiredSelectedWallet()
            val tokens = tokenRepository.get(
                currency = settingsRepository.currency,
                accountId = wallet.accountId,
                network = wallet.network,
                refresh = refresh,
            ) ?: throw IllegalStateException("Tokens not found for account ${wallet.accountId}")
            val tokenAddress = details.asset.asTokenEntity.address
            if (tokenAddress.equals(WalletCurrency.TON_CHAIN_KEY, ignoreCase = true)) {
                return tokens.firstOrNull { it.isTon }
            }
            return tokens.firstOrNull { it.address.equalsAddress(tokenAddress) }
        } catch (e: Throwable) {
            L.e(e)
            null
        }
    }

    private suspend fun loadChart(details: AssetDetailsResponse, period: ChartPeriod) {
        setState {
            val data = this as? AssetDetailsState.Data ?: return@setState this
            data.copy(chartPeriod = period, isChartLoading = true)
        }
        val token = details.asset.asTokenEntity
        val address = if (token.isUsdtTrc20) {
            TokenEntity.USDT.address
        } else {
            token.address
        }
        try {
            val chart = tradingRepository.getAssetChart(address, period)
            setState {
                val data = this as? AssetDetailsState.Data ?: return@setState this
                data.copy(chartData = chart, isChartLoading = false)
            }
        } catch (e: Throwable) {
            L.e(e)
            setState {
                val data = this as? AssetDetailsState.Data ?: return@setState this
                data.copy(isChartLoading = false)
            }
        }
    }

    private suspend fun loadTokenTransactions(details: AssetDetailsResponse): List<AssetDetailsSections.RecentEvents.Item>? {
        try {
            val wallet = accountRepository.getSelectedWallet()
                ?: throw IllegalStateException("Selected wallet not found")
            val token = details.asset.asTokenEntity
            val txEvents = when (token.blockchain) {
                Blockchain.TON -> loadTonTokenTransactions(wallet, token.address)
                Blockchain.TRON -> loadTronTokenTransactions(wallet)
            } ?: return emptyList()
            if (txEvents.isEmpty()) return null
            return txEvents.map {
                AssetDetailsSections.RecentEvents.Item(
                    txEvent = it,
                    uiEvent = txEventUiMapper.toUiItem(it, wallet)
                )
            }
        } catch (e: Throwable) {
            L.e(e)
            return null
        }
    }

    private suspend fun loadTonTokenTransactions(
        wallet: WalletEntity,
        tokenAddress: String,
    ): List<TxEvent>? {
        val accountEvents = eventsRepository.loadForToken(
            tokenAddress = tokenAddress,
            accountId = wallet.accountId,
            network = wallet.network,
            limit = 4,
        ) ?: return null
        val tonAddress = BlockchainAddress(
            value = wallet.address,
            network = wallet.network,
            blockchain = Blockchain.TON,
        )
        return eventsRepository.mapAccountEventsToTxEvents(
            tonAddress,
            accountEvents.events,
        )
    }

    private suspend fun loadTronTokenTransactions(
        wallet: WalletEntity,
    ): List<TxEvent>? {
        if (!wallet.hasPrivateKey || wallet.testnet) return null
        val tronAddress = accountRepository.getTronAddress(wallet.id) ?: return null
        val tonProofToken = accountRepository.requestTonProofToken(wallet) ?: return null
        val tronEvents = eventsRepository.loadTronEvents(
            tronWalletAddress = tronAddress,
            tonProofToken = tonProofToken,
            limit = 4,
        ) ?: return null
        val tronBlockchainAddress = BlockchainAddress(
            value = tronAddress,
            network = wallet.network,
            blockchain = Blockchain.TRON,
        )
        return eventsRepository.mapTronEventsToTxEvents(
            tronBlockchainAddress,
            tronEvents,
        )
    }

    private suspend fun getExplorerUrl(): String? {
        try {
            val data = obtainSpecificState<AssetDetailsState.Data>() ?: throw IllegalStateException(
                "Data state not found"
            )
            val token = data.details.asset.asTokenEntity
            val wallet = accountRepository.requiredSelectedWallet()
            val tronAddress = accountRepository.getTronAddress(wallet.id) ?: ""
            val accountExplorerUrl = api.getConfig(wallet.network).accountExplorer
            return if (token.isUsdtTrc20) {
                ExternalLinkHelper.tronToken(tronAddress, wallet.testnet)
            } else if (token.isTon) {
                accountExplorerUrl.format(wallet.address)
            } else {
                accountExplorerUrl.format("${wallet.address}/jetton/${token.address}")
            }
        } catch (e: Throwable) {
            L.e(e)
            return null
        }
    }
}
