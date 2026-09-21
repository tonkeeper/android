package com.tonapps.deposit.multicoin.screens.ramp

import com.tonapps.async.Async
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowAddFundsOption
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.deposit.multicoin.analytics.DepositAnalytics
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.log.L
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.wallet.data.account.AccountRepository
import io.exchangeapi.models.ExchangeLayoutCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface RampState {
    data object Loading : RampState
    data class Data(
        val cards: List<ExchangeLayoutCard>,
        val isSendAvailable: Boolean,
        val isExchangeAvailable: Boolean,
    ) : RampState
}

class RampFeature(
    private val rampType: RampType,
    private val analyticsFrom: DepositFlowFrom,
    private val exchangeRepository: ExchangeRepository,
    private val accountRepository: AccountRepository,
) : AsyncViewModel() {

    val state: StateFlow<RampState> field = MutableStateFlow<RampState>(RampState.Loading)

    val analytics = DepositAnalytics(rampType, analyticsFrom)

    init {
        load()
    }

    private fun load() {
        bgScope.launch {
            try {
                val wallet = accountRepository.getSelectedWallet() ?: return@launch

                val isMainnet = wallet.network.isMainnet
                val isExchangeAvailable = isMainnet && !wallet.isWatchOnly
                val isSendAvailable = !wallet.isWatchOnly

                val cards = if (isExchangeAvailable) {
                    exchangeRepository.getLayoutCards(rampType).items
                } else {
                    emptyList()
                }

                state.emit(
                    RampState.Data(
                        cards = cards,
                        isSendAvailable = isSendAvailable,
                        isExchangeAvailable = isExchangeAvailable,
                    )
                )
                trackStarted(hasExchangeCards = cards.isNotEmpty())
            } catch (e: Throwable) {
                L.e(e)
                state.emit(
                    RampState.Data(
                        cards = emptyList(),
                        isSendAvailable = true,
                        isExchangeAvailable = false,
                    )
                )
                trackStarted(hasExchangeCards = false)
            }
        }
    }

    private fun trackStarted(hasExchangeCards: Boolean) {
        val options = buildList {
            add(DepositFlowAddFundsOption.ReceiveTokens)
            if (hasExchangeCards) {
                add(DepositFlowAddFundsOption.BuyWithFiat)
            }
        }
        analytics.started(options)
    }

    override fun onCleared() {
        super.onCleared()
        Async.globalScope().launch {
            exchangeRepository.clearRampCache(rampType)
        }
    }
}
