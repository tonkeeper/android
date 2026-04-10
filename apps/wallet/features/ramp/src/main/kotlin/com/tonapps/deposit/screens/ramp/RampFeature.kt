package com.tonapps.deposit.screens.ramp

import com.tonapps.async.Async
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowSellAsset
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowBuyAsset
import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.deposit.data.toWalletCurrency
import com.tonapps.deposit.screens.method.RampAsset
import com.tonapps.deposit.toBuyAsset
import com.tonapps.deposit.toSellAsset
import com.tonapps.log.L
import com.tonapps.mvi.MviFeature
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviState
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.props.MviProperty
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import io.exchangeapi.models.CreateP2PSessionRequest
import io.exchangeapi.models.ExchangeLayoutItem
import io.exchangeapi.models.ExchangeLayoutItemType
import io.exchangeapi.models.ExchangeMerchantInfo
import kotlinx.coroutines.launch

sealed interface RampAction : MviAction {
    data object Init : RampAction
}

sealed interface RampState : MviState {
    object Loading : RampState

    object Empty : RampState

    data class Data(
        val fiatItem: ExchangeLayoutItem? = null,
        val cryptoItem: ExchangeLayoutItem? = null,
        val cryptoAsset: RampAsset? = null,
        val stablecoinItem: ExchangeLayoutItem? = null,
        val isExchangeAvailable: Boolean = true,
        val isSendAvailable: Boolean = true,
    ) : RampState
}

class RampViewState(
    val global: MviProperty<RampState>
) : MviViewState

class RampFeature(
    private val rampType: RampType,
    private val onRampRepository: ExchangeRepository,
    private val accountRepository: AccountRepository,
) : MviFeature<RampAction, RampState, RampViewState>(
    initState = RampState.Loading,
    initAction = RampAction.Init
) {

    override fun createViewState(): RampViewState {
        return buildViewState {
            RampViewState(mviProperty { it })
        }
    }

    override suspend fun executeAction(action: RampAction) {
        when (action) {
            is RampAction.Init -> loadAssets()
        }
    }

    private suspend fun loadAssets() {
        try {
            setState { RampState.Loading }
            updateAssets()
        } catch (e: Throwable) {
            L.e(e)
            setState { RampState.Empty }
        }
    }

    private suspend fun updateAssets() {
        val wallet = accountRepository.getSelectedWallet()
            ?: return

        val network = wallet.network
        if (!network.isMainnet) {
            setState {
                RampState.Data(
                    isExchangeAvailable = false,
                    isSendAvailable = !wallet.isWatchOnly,
                )
            }
            return
        }

        if (wallet.isWatchOnly) {
            setState {
                RampState.Data(
                    isExchangeAvailable = false,
                    isSendAvailable = false,
                )
            }
            return
        }

        val layout = onRampRepository.getLayout(rampType)

        val fiatItem = layout.items.firstOrNull { it.type == ExchangeLayoutItemType.fiat }
        val cryptoItem = layout.items.firstOrNull { it.type == ExchangeLayoutItemType.crypto }
        val stablecoinItem = layout.items.firstOrNull { it.type == ExchangeLayoutItemType.stablecoin }

        // Crypto always has a single asset — pre-extract it for direct navigation
        val cryptoAsset = cryptoItem?.assets?.firstOrNull()?.let {
            RampAsset.Currency(it.toWalletCurrency())
        }

        setState {
            RampState.Data(
                fiatItem = fiatItem,
                cryptoItem = cryptoItem,
                cryptoAsset = cryptoAsset,
                stablecoinItem = stablecoinItem,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        Async.globalScope().launch {
            onRampRepository.clearRampCache(rampType)
        }
    }
}
