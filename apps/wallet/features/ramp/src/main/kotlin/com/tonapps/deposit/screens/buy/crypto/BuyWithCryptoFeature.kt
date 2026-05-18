package com.tonapps.deposit.screens.buy.crypto

import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.deposit.screens.method.RampAsset
import com.tonapps.deposit.toBuyAsset
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.log.L
import com.tonapps.mvi.MviFeature
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviState
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.props.MviProperty
import com.tonapps.wallet.data.account.AccountRepository
import io.exchangeapi.infrastructure.ApiResult
import io.exchangeapi.models.CreateExchangeRequest
import io.exchangeapi.models.ExchangeFlow

sealed interface BuyWithCryptoAction : MviAction {
    data object Init : BuyWithCryptoAction
}

sealed interface BuyWithCryptoState : MviState {
    data object Loading : BuyWithCryptoState

    data class Data(
        val payinAddress: String,
        val amountExpectedFrom: String,
        val rate: String?,
        val fromCode: String,
        val toCode: String,
        val minDeposit: String?,
        val maxDeposit: String?,
        val network: String?,
        val estimatedDurationSeconds: Int?,
    ) : BuyWithCryptoState

    data class Error(val message: String?) : BuyWithCryptoState
}

class BuyWithCryptoViewState(
    val global: MviProperty<BuyWithCryptoState>
) : MviViewState

class BuyWithCryptoFeature(
    val from: WalletCurrency,
    val to: RampAsset,
    private val onRampRepository: ExchangeRepository,
    private val accountRepository: AccountRepository,
) : MviFeature<BuyWithCryptoAction, BuyWithCryptoState, BuyWithCryptoViewState>(
    initState = BuyWithCryptoState.Loading,
    initAction = BuyWithCryptoAction.Init
) {

    init {
        AnalyticsHelper.Default.events.depositFlow.depositViewC2c(
            buyAsset = to.toCurrency.toBuyAsset(),
            sellAsset = from.code
        )
    }

    override fun createViewState(): BuyWithCryptoViewState {
        return buildViewState {
            BuyWithCryptoViewState(mviProperty { it })
        }
    }

    override suspend fun executeAction(action: BuyWithCryptoAction) {
        when (action) {
            is BuyWithCryptoAction.Init -> loadData()
        }
    }

    private suspend fun loadData() {
        try {
            val wallet = accountRepository.getSelectedWallet()!!
            val walletAddress = resolveWalletAddress(wallet)

            val networkDisplayName = from.title

            val apiResult = onRampRepository.createExchange(
                CreateExchangeRequest(
                    from = from.code,
                    to = to.currencyCode,
                    wallet = walletAddress,
                    fromNetwork = from.network,
                    toNetwork = to.network,
                    flow = ExchangeFlow.deposit,
                )
            )

            when (apiResult) {
                is ApiResult.Success -> {
                    val result = apiResult.data
                    setState {
                        BuyWithCryptoState.Data(
                            payinAddress = result.payinAddress,
                            amountExpectedFrom = result.amountExpectedFrom,
                            rate = formatRate(from.code, to.currencyCode, result.rate),
                            fromCode = from.code,
                            toCode = to.currencyCode,
                            minDeposit = result.minDeposit,
                            maxDeposit = result.maxDeposit,
                            network = networkDisplayName,
                            estimatedDurationSeconds = result.estimatedDuration,
                        )
                    }
                }
                is ApiResult.Error -> {
                    setState { BuyWithCryptoState.Error(apiResult.message) }
                }
            }
        } catch (e: Throwable) {
            L.e(e)
            setState { BuyWithCryptoState.Error(e.message) }
        }
    }

    private suspend fun resolveWalletAddress(wallet: WalletEntity): String {
        val toChain = (to as? RampAsset.Currency)?.currency?.chain
        return when (toChain) {
            is WalletCurrency.Chain.TRON -> {
                accountRepository.getTronAddress(wallet.id) ?: wallet.address
            }

            else -> wallet.address
        }
    }

    // TODO duplication
    private fun formatRate(
        fromCode: String,
        toCode: String,
        receiveAmountStr: String,
    ): String? {
        val receiveAmount = runCatching { Coins.of(receiveAmountStr) }
            .getOrNull()
            ?: return null

        val fromFormat = CurrencyFormatter.format(fromCode, Coins.ONE, replaceSymbol = false)
        val rateFormat = CurrencyFormatter.format(toCode, receiveAmount, replaceSymbol = false)
        return "$fromFormat ≈ $rateFormat"
    }
}
