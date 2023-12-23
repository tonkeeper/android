package com.tonkeeper.fragment.send.confirm

import androidx.lifecycle.viewModelScope
import com.tonkeeper.App
import com.tonkeeper.api.account.AccountRepository
import com.tonkeeper.api.address
import com.tonkeeper.core.Coin
import com.tonkeeper.core.currency.from
import com.tonkeeper.core.transaction.TransactionHelper
import com.tonkeeper.event.WalletStateUpdateEvent
import com.tonkeeper.fragment.send.TransactionData
import core.EventBus
import io.tonapi.models.JettonBalance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ton.SupportedCurrency
import ton.SupportedTokens
import ton.wallet.Wallet
import uikit.mvi.UiFeature
import uikit.widget.ProcessTaskView

class ConfirmScreenFeature: UiFeature<ConfirmScreenState, ConfirmScreenEffect>(ConfirmScreenState()) {

    private val currency: SupportedCurrency
        get() = App.settings.currency

    private val fee: Float
        get() = Coin.toCoins(uiState.value.feeValue)

    private val accountRepository = AccountRepository()

    fun send(tx: TransactionData) {
        updateUiState {
            it.copy(
                processActive = true,
                processState = ProcessTaskView.State.LOADING
            )
        }

        viewModelScope.launch {
            transferMessage(tx)
        }
    }

    private suspend fun transferMessage(tx: TransactionData) = withContext(Dispatchers.IO) {
        try {
            val walletManager = App.walletManager
            val wallet = walletManager.getWalletInfo()!!
            val account = accountRepository.getFromCloud(wallet.accountId)?.data ?: throw Exception("failed to get account")
            val balance = Coin.toCoins(account.balance)

            if (tx.isTon) {
                val full = tx.amount + fee
                if (full > balance) {
                    throw Exception("Not enough funds")
                }
                TransactionHelper.sendTon(wallet, tx.address!!, tx.amount, tx.comment, tx.max)
            } else {
                TransactionHelper.sendJetton(wallet, tx.jetton!!, tx.address!!, tx.amount, tx.comment)
            }

            updateUiState {
                it.copy(
                    processState = ProcessTaskView.State.SUCCESS
                )
            }

            val emulatedEventItems = uiState.value.emulatedEventItems
            if (emulatedEventItems.isNotEmpty()) {
                EventBus.post(WalletStateUpdateEvent)
            }

            delay(1000)
            sendEffect(ConfirmScreenEffect.CloseScreen(true))
        } catch (e: Throwable) {
            updateUiState {
                it.copy(
                    processState = ProcessTaskView.State.FAILED
                )
            }

            delay(1000)
            sendEffect(ConfirmScreenEffect.CloseScreen(false))
        }
    }

    fun setAmount(amount: Float, tokenAddress: String, tokenSymbol: String) {
        viewModelScope.launch {
            val wallet = App.walletManager.getWalletInfo() ?: return@launch
            val accountId = wallet.accountId

            val amountFormat = Coin.format(
                currency = tokenSymbol,
                value = amount,
                decimals = Coin.MAX_DECIMALS,
                useCurrencyCode = true
            )
            val inCurrency = from(tokenAddress, accountId).value(amount).to(currency)

            updateUiState {
                it.copy(
                    amount = amountFormat,
                    amountInCurrency = "≈ " + Coin.format(currency = currency, value = inCurrency, decimals = 9)
                )
            }
        }
    }

    fun requestFee(transaction: TransactionData) {
        updateUiState {
            it.copy(
                fee = "",
                feeInCurrency = "",
                buttonEnabled = false
            )
        }

        viewModelScope.launch {
            val wallet = App.walletManager.getWalletInfo() ?: return@launch
            if (transaction.isTon) {
                calculateFeeTon(wallet, transaction.address!!, transaction.amount, transaction.comment, transaction.max)
            } else {
                calculateFeeJetton(wallet, transaction.jetton!!, transaction.address!!, transaction.amount, transaction.comment)
            }
        }
    }

    private suspend fun calculateFeeJetton(
        wallet: Wallet,
        jetton: JettonBalance,
        to: String,
        value: Float,
        comment: String? = null,
    ) = withContext(Dispatchers.IO) {
        val emulate = TransactionHelper.emulateJetton(wallet, jetton, to, value, comment)
        if (emulate == null) {
            updateUiState {
                it.copy(
                    feeValue = 0,
                    fee = "unknown",
                    buttonEnabled = true,
                )
            }
        } else {
            val feeInCurrency = from(jetton.address, wallet.accountId)
                .value(emulate.fee)
                .to(currency)

            updateUiState {
                it.copy(
                    feeValue = emulate.fee,
                    fee = "≈ " + Coin.format(value = emulate.fee, decimals = 9),
                    feeInCurrency = "≈ " + Coin.format(currency, feeInCurrency, decimals = 9),
                    buttonEnabled = true,
                    emulatedEventItems = emulate.actions
                )
            }
        }
    }

    private suspend fun calculateFeeTon(
        wallet: Wallet,
        to: String,
        value: Float,
        comment: String? = null,
        max: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val emulate = TransactionHelper.emulateTon(wallet, to, value, comment, max)
        if (emulate == null) {
            updateUiState {
                it.copy(
                    feeValue = 0,
                    fee = "unknown",
                    buttonEnabled = true,
                )
            }
        } else {
            val feeInCurrency = from(SupportedTokens.TON, wallet.accountId)
                .value(emulate.fee)
                .to(currency)

            updateUiState {
                it.copy(
                    feeValue = emulate.fee,
                    fee = "≈ " + Coin.format(currency = SupportedCurrency.TON, value = emulate.fee, decimals = 9),
                    feeInCurrency = "≈ " + Coin.format(currency, feeInCurrency, decimals = 9),
                    buttonEnabled = true,
                    emulatedEventItems = emulate.actions
                )
            }
        }
    }
}