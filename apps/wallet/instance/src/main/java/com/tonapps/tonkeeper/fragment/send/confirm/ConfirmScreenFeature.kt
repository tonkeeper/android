package com.tonapps.tonkeeper.fragment.send.confirm

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.api.totalFees
import com.tonapps.blockchain.Coin
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.extensions.label
import com.tonapps.tonkeeper.fragment.send.TransactionData
import com.tonapps.tonkeeper.password.PasscodeRepository
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.WalletRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.WalletCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.ton.cell.Cell
import com.tonapps.wallet.data.account.legacy.WalletLegacy
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withContext
import org.ton.bitstring.BitString
import org.ton.block.StateInit
import org.ton.contract.wallet.WalletTransfer
import uikit.extensions.collectFlow
import uikit.mvi.UiFeature
import uikit.widget.ProcessTaskView

@Deprecated("Need refactoring")
class ConfirmScreenFeature(
    private val passcodeRepository: PasscodeRepository,
    private val ratesRepository: RatesRepository,
    private val api: API,
    private val historyHelper: HistoryHelper,
    private val settingsRepository: SettingsRepository,
    private val walletRepository: WalletRepository,
): UiFeature<ConfirmScreenState, ConfirmScreenEffect>(ConfirmScreenState()) {

    private val currency: WalletCurrency
        get() = settingsRepository.currency

    private var lastSeqno = -1
    private var lastUnsignedBody: Cell? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val wallet = App.walletManager.getWalletInfo() ?: return@launch
            updateUiState {
                it.copy(
                    signer = wallet.signer,
                    walletLabel = wallet.label
                )
            }
        }
    }

    fun sendSignature(signature: BitString) {
        updateUiState {
            it.copy(
                processActive = true,
                processState = ProcessTaskView.State.LOADING
            )
        }
        walletRepository.activeWalletFlow.onEach { wallet ->
            val contract = wallet.contract
            val unsignedBody = lastUnsignedBody ?: throw Exception("unsigned body is null")
            val signerBody = contract.signedBody(signature, unsignedBody)
            val bodyCell = contract.createTransferMessageCell(wallet.contract.address, lastSeqno, signerBody)
            if (!api.sendToBlockchain(bodyCell, wallet.testnet)) {
                throw Exception("failed to send to blockchain")
            }
            successResult()
        }.catch {
            failedResult()
        }.flowOn(Dispatchers.IO).take(1).launchIn(viewModelScope)
    }

    fun sign(tx: TransactionData) {
        updateUiState {
            it.copy(
                processActive = true,
                processState = ProcessTaskView.State.LOADING
            )
        }

        walletRepository.activeWalletFlow.map {
            createMessage(tx, it)
        }.onEach {
            val publicKey = it.wallet.publicKey
            lastUnsignedBody = it.wallet.createBody(it.seqno, it.validUntil, listOf(it.transfer))
            lastSeqno = it.seqno
            sendEffect(ConfirmScreenEffect.OpenSignerApp(lastUnsignedBody!!, publicKey))
        }.flowOn(Dispatchers.IO).take(1).launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun send(context: Context, tx: TransactionData) {
        updateUiState {
            it.copy(
                processActive = true,
                processState = ProcessTaskView.State.LOADING
            )
        }

        passcodeRepository.confirmationFlow(context).flatMapLatest {
            walletRepository.activeWalletFlow
        }.map {
            createMessage(tx, it)
        }.onEach { message ->
            val privateKey = walletRepository.getPrivateKey(message.wallet.id)
            val bodyCell = walletRepository.createSignedMessage(
                wallet = message.wallet,
                seqno = message.seqno,
                privateKeyEd25519 = privateKey,
                validUntil = message.validUntil,
                transfers = listOf(message.transfer)
            )

            if (!api.sendToBlockchain(bodyCell, message.wallet.testnet)) {
                throw Exception("failed to send to blockchain")
            }
            successResult()
        }.catch {
            failedResult()
        }.flowOn(Dispatchers.IO).take(1).launchIn(viewModelScope)
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

        delay(1000)
        sendEffect(ConfirmScreenEffect.CloseScreen(true))
    }

    fun setAmount(amountRaw: String, decimals: Int, tokenAddress: String, symbol: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val value = Coin.prepareValue(amountRaw).toFloatOrNull() ?: 0f
            val rates = ratesRepository.getRates(currency, tokenAddress)
            val fiat = rates.convert(tokenAddress, value)

            updateUiState {
                it.copy(
                    amount = CurrencyFormatter.format(symbol, value),
                    amountInCurrency = "≈ " + CurrencyFormatter.format(currency.code, fiat)
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

        walletRepository.activeWalletFlow.map {
            createMessage(tx, it)
        }.onEach { message ->
            val bodyCell = walletRepository.createSignedMessage(
                wallet = message.wallet,
                seqno = message.seqno,
                privateKeyEd25519 = EmptyPrivateKeyEd25519,
                validUntil = message.validUntil,
                transfers = listOf(message.transfer)
            )
            val emulated = api.emulate(bodyCell, message.wallet.testnet)
            val feeInTon = emulated.totalFees
            val actions = historyHelper.mapping(message.wallet, emulated.event, false)
            val tokenAddress = tx.tokenAddress

            val rates = ratesRepository.getRates(currency, tokenAddress)
            val feeInCurrency = rates.convert(tokenAddress, Coin.toCoins(feeInTon))

            val amount = Coin.toCoins(feeInTon)
            updateUiState {
                it.copy(
                    feeValue = feeInTon,
                    fee = "≈ " + CurrencyFormatter.format("TON", amount),
                    feeInCurrency = "≈ " + CurrencyFormatter.formatFiat(currency.code, feeInCurrency),
                    buttonEnabled = true,
                    emulatedEventItems = actions
                )
            }
        }.catch {
            updateUiState {
                it.copy(
                    feeValue = 0,
                    fee = "unknown",
                    buttonEnabled = true,
                )
            }
        }.flowOn(Dispatchers.IO).take(1).launchIn(viewModelScope)
    }

    data class Message(
        val tx: TransactionData,
        val wallet: WalletEntity,
        val seqno: Int,
        val validUntil: Long,
    ) {

        val stateInit: StateInit?
            get() = if (seqno == 0) wallet.contract.stateInit else null

        val transfer: WalletTransfer
            get() = tx.buildWalletTransfer(wallet.contract.address, stateInit)
    }

    private suspend fun createMessage(
        tx: TransactionData,
        wallet: WalletEntity
    ): Message = withContext(Dispatchers.IO) {
        val seqnoDeferred = async { walletRepository.getSeqno(wallet) }
        val validUntilDeferred = async { walletRepository.getValidUntil(wallet.testnet) }

        val seqno = seqnoDeferred.await()
        val validUntil = validUntilDeferred.await()

        Message(tx, wallet, seqno, validUntil)
    }
}