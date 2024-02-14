package com.tonapps.tonkeeper.fragment.send.confirm

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.api.getAddress
import com.tonapps.tonkeeper.api.totalFees
import com.tonapps.tonkeeper.core.Coin
import com.tonapps.tonkeeper.core.currency.currency
import com.tonapps.tonkeeper.core.currency.from
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.event.WalletStateUpdateEvent
import com.tonapps.tonkeeper.extensions.emulate
import com.tonapps.tonkeeper.extensions.getSeqno
import com.tonapps.tonkeeper.extensions.sendToBlockchain
import com.tonapps.tonkeeper.fragment.send.TransactionData
import com.tonapps.wallet.data.core.Currency
import core.EventBus
import core.formatter.CurrencyFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.block.Coins
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.crypto.base64
import ton.wallet.Wallet
import uikit.mvi.UiFeature
import uikit.widget.ProcessTaskView

class ConfirmScreenFeature: UiFeature<ConfirmScreenState, ConfirmScreenEffect>(ConfirmScreenState()) {

    private val currency: Currency
        get() = App.settings.currency

    private var lastSeqno = 0

    init {
        viewModelScope.launch {
            val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo() ?: return@launch
            updateUiState {
                it.copy(
                    signer = wallet.signer
                )
            }
        }
    }

    private fun buildUnsignedBody(
        wallet: Wallet,
        seqno: Int,
        tx: TransactionData
    ): Cell {
        val transfer = tx.buildWalletTransfer(wallet.contract.address)
        return wallet.contract.createTransferUnsignedBody(seqno = seqno, gifts = arrayOf(transfer))
    }

    fun sendSignedBoc(boc: String) {
        updateUiState {
            it.copy(
                processActive = true,
                processState = ProcessTaskView.State.LOADING
            )
        }

        viewModelScope.launch {
            try {
                val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo() ?: throw Exception("failed to get wallet")
                val seqno = getSeqno(wallet)
                val cell = BagOfCells(base64(boc)).first()
                val b = wallet.contract.createTransferMessageCell(wallet.contract.address, seqno, cell)
                if (!wallet.sendToBlockchain(b)) {
                    failedResult()
                    return@launch
                }
                successResult()
            } catch (e: Throwable) {
                failedResult()
            }
        }
    }

    fun sign(tx: TransactionData) {
        updateUiState {
            it.copy(
                processActive = true,
                processState = ProcessTaskView.State.LOADING
            )
        }

        viewModelScope.launch {
            val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo() ?: throw Exception("failed to get wallet")
            val seqno = getSeqno(wallet)
            val cell = buildUnsignedBody(wallet, seqno, tx)

            sendEffect(ConfirmScreenEffect.OpenSignerApp(cell, wallet.publicKey))
        }
    }

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
            val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo() ?: throw Exception("failed to get wallet")
            val privateKey = com.tonapps.tonkeeper.App.walletManager.getPrivateKey(wallet.id)
            val gift = tx.buildWalletTransfer(wallet.contract.address)

            wallet.sendToBlockchain(privateKey, gift) ?: throw Exception("failed to send to blockchain")

            successResult()
        } catch (e: Throwable) {
            failedResult()
        }
    }

    fun setFailedResult() {
        viewModelScope.launch {
            failedResult()
        }
    }

    private suspend fun failedResult() {
        updateUiState {
            it.copy(
                processState = ProcessTaskView.State.FAILED
            )
        }

        delay(5000)
        sendEffect(ConfirmScreenEffect.CloseScreen(false))
    }

    private suspend fun successResult() {
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
    }

    fun setAmount(amount: Coins, tokenAddress: String, tokenSymbol: String) {
        viewModelScope.launch {
            val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo() ?: return@launch
            val accountId = wallet.accountId

            val value = Coin.toCoins(amount.amount.value.toLong())
            val amountFormat = CurrencyFormatter.format(tokenSymbol, value)
            val inCurrency = from(tokenAddress, accountId, wallet.testnet).value(value).convert(currency.code)

            updateUiState {
                it.copy(
                    amount = amountFormat,
                    amountInCurrency = "≈ " + CurrencyFormatter.formatFiat(currency.code, inCurrency)
                )
            }
        }
    }

    fun requestFee(tx: TransactionData) {
        updateUiState {
            it.copy(
                fee = "",
                feeInCurrency = "",
                buttonEnabled = false
            )
        }

        viewModelScope.launch {
            val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo() ?: return@launch
            try {
                val gift = tx.buildWalletTransfer(wallet.contract.address)
                val emulate = wallet.emulate(gift)
                val fee = emulate.totalFees
                val actions = HistoryHelper.mapping(wallet, emulate.event, false)
                val jettonAddress = tx.jetton?.getAddress(wallet.testnet) ?: "TON"

                val feeInCurrency = wallet.currency(jettonAddress)
                    .value(fee)
                    .convert(currency.code)

                val amount = Coin.toCoins(fee)
                updateUiState {
                    it.copy(
                        feeValue = fee,
                        fee = "≈ " + CurrencyFormatter.format("TON", amount),
                        feeInCurrency = "≈ " + CurrencyFormatter.formatFiat(currency.code, feeInCurrency),
                        buttonEnabled = true,
                        emulatedEventItems = actions
                    )
                }

            } catch (e: Throwable) {
                Log.d("ConfirmScreenFeature", "requestFee: failed to get fee", e)
                updateUiState {
                    it.copy(
                        feeValue = 0,
                        fee = "unknown",
                        buttonEnabled = true,
                    )
                }
            }
        }
    }

    private suspend fun getSeqno(wallet: Wallet): Int {
        if (lastSeqno == 0) {
            lastSeqno = wallet.getSeqno()
        }
        return lastSeqno
    }

}