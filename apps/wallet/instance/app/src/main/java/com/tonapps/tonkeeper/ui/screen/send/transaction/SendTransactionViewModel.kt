package com.tonapps.tonkeeper.ui.screen.send.transaction

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.common.util.concurrent.AtomicDouble
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.icu.Coins
import com.tonapps.ledger.ton.Transaction
import com.tonapps.tonkeeper.core.Amount
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.extensions.getTransfers
import com.tonapps.tonkeeper.manager.tx.TransactionManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.send.main.helper.InsufficientBalanceType
import com.tonapps.tonkeeper.usecase.emulation.EmulationUseCase
import com.tonapps.tonkeeper.usecase.sign.SignUseCase
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.APIException
import com.tonapps.wallet.api.SendBlockchainState
import com.tonapps.wallet.api.getDebugMessage
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.MessageBodyEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.settings.BatteryTransaction
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.localization.Localization
import io.tonapi.models.JettonVerificationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class SendTransactionViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val request: SignRequestEntity,
    private val batteryTransactionType: BatteryTransaction,
    private val forceRelayer: Boolean,
    private val accountRepository: AccountRepository,
    private val tokenRepository: TokenRepository,
    private val settingsRepository: SettingsRepository,
    private val signUseCase: SignUseCase,
    private val api: API,
    private val historyHelper: HistoryHelper,
    private val emulationUseCase: EmulationUseCase,
    private val transactionManager: TransactionManager,
    private val batteryRepository: BatteryRepository
) : BaseWalletVM(app) {

    private val currency = settingsRepository.currency
    private val isBattery = AtomicBoolean(false)

    private val _stateFlow = MutableStateFlow<SendTransactionState>(SendTransactionState.Loading)
    val stateFlow = _stateFlow.asStateFlow()

    private val emulationReadyDate = AtomicLong(0)

    var message: MessageBodyEntity? = null

    init {
        AnalyticsHelper.tcViewConfirm(settingsRepository.installId, request.appUri.toString(), request.targetAddressValue)
        viewModelScope.launch(Dispatchers.IO) {
            val tokens = getTokens()
            val useBattery = settingsRepository.batteryIsEnabledTx(wallet.accountId, batteryTransactionType)
            try {
                val transfers = transfers(tokens.filter { it.isRequestMinting }, true, useBattery)
                message = accountRepository.messageBody(wallet, request.validUntil, transfers)

                val emulated = emulationUseCase(
                    message = message!!,
                    useBattery = useBattery,
                    forceRelayer = forceRelayer,
                    params = true
                )
                isBattery.set(emulated.withBattery)

                val details = historyHelper.create(wallet, emulated)

                val totalFormatBuilder = StringBuilder(getString(Localization.total, emulated.totalFormat))
                if (emulated.nftCount > 0) {
                    totalFormatBuilder.append(" + ").append(emulated.nftCount).append(" NFT")
                }

                val jettons = emulated.loadTokens(wallet.testnet, tokenRepository)
                val hasCompressedJetton = jettons.any { it.isRequestMinting || it.customPayloadApiUri != null }
                val tonBalance = getTONBalance()
                val transferAmount = EmulationUseCase.calculateTransferAmount(transfers)
                var transferFee = (if (!emulated.extra.isRefund) {
                    Coins.ZERO
                } else {
                    emulated.extra.value
                }) + Coins.of(0.05)

                if (hasCompressedJetton) {
                    transferFee += Coins.of(0.1)
                }
                if (jettons.size > 1) {
                    transferFee += Coins.of(0.05)
                }

                val transferTotal = transferAmount + transferFee

                emulationReadyDate.set(System.currentTimeMillis())

                if (!emulated.withBattery && transferTotal > tonBalance) {
                    _stateFlow.value = SendTransactionState.InsufficientBalance(
                        wallet = wallet,
                        balance = tonBalance,
                        required = transferTotal,
                        withRechargeBattery = forceRelayer || useBattery,
                        singleWallet = isSingleWallet()
                    )
                } else {
                    _stateFlow.value = SendTransactionState.Details(
                        emulated = details,
                        totalFormat = if (emulated.failed) getString(Localization.unknown) else totalFormatBuilder.toString(),
                        isDangerous = emulated.total.isDangerous,
                        nftCount = emulated.nftCount,
                        failed = emulated.failed
                    )
                }
            } catch (e: Throwable) {
                FirebaseCrashlytics.getInstance().recordException(APIException.Emulation(
                    boc = message?.createSignedBody(EmptyPrivateKeyEd25519.invoke(), forceRelayer || useBattery)?.base64() ?: "failed",
                    sourceUri = request.appUri,
                    cause = e
                ))

                val tonBalance = getTONBalance()
                if (tonBalance == Coins.ZERO) {
                    _stateFlow.value = SendTransactionState.InsufficientBalance(
                        wallet = wallet,
                        balance = tonBalance,
                        required = Coins.of(0.1),
                        withRechargeBattery = forceRelayer || useBattery,
                        singleWallet = isSingleWallet()
                    )
                } else {
                    toast(e.getDebugMessage() ?: getString(Localization.unknown_error))
                    _stateFlow.value = SendTransactionState.FailedEmulation
                }
            }
        }
    }

    private suspend fun isBatteryIsEnabledTx(): Boolean = withContext(Dispatchers.IO) {
        if (settingsRepository.batteryIsEnabledTx(wallet.accountId, batteryTransactionType)) {
            getBatteryBalance().isPositive
        } else {
            false
        }
    }

    private suspend fun getBatteryBalance(): Coins {
        val tonProof = accountRepository.requestTonProofToken(wallet) ?: return Coins.ZERO
        val entity = batteryRepository.getBalance(
            tonProofToken = tonProof,
            publicKey = wallet.publicKey,
            testnet = wallet.testnet,
            ignoreCache = true
        )
        return entity.balance
    }

    private suspend fun isSingleWallet(): Boolean {
        return 1 >= accountRepository.getWallets().size
    }

    private suspend fun getTONBalance(): Coins {
        val balance = tokenRepository.getTON(settingsRepository.currency, wallet.accountId, wallet.testnet)?.balance?.value
        return balance ?: Coins.ZERO
    }

    private fun getLedgerTransaction(
        message: MessageBodyEntity
    ): List<Transaction> {
        if (!message.wallet.isLedger) {
            return emptyList()
        }
        /*if (message.transfers.size > 1) {
            throw IllegalStateException("Ledger does not support multiple messages")
        }
        val transfer = message.transfers.firstOrNull() ?: return null */
        val transactions = mutableListOf<Transaction>()
        for ((index, transfer) in message.transfers.withIndex()) {
            transactions.add(Transaction.fromWalletTransfer(
                walletTransfer = transfer,
                seqno = message.seqNo + index,
                timeout = message.validUntil
            ))
        }

        return transactions.toList()
    }

    fun send() = flow {
        val isBattery = isBattery.get()
        val compressedTokens = getTokens().filter { it.isRequestMinting }
        val transfers = transfers(compressedTokens, false, isBattery)
        val message = accountRepository.messageBody(wallet, request.validUntil, transfers)
        val unsignedBody = message.createUnsignedBody(isBattery)
        val ledgerTransactions = getLedgerTransaction(message)

        val cells = mutableListOf<Cell>()
        if (ledgerTransactions.size > 1) {
            for ((index, transaction) in ledgerTransactions.withIndex()) {
                val cell = signUseCase(
                    context = context,
                    wallet = wallet,
                    seqNo = transaction.seqno,
                    ledgerTransaction = transaction,
                    transactionIndex = index,
                    transactionCount = ledgerTransactions.size
                )
                cells.add(cell)
            }
        } else {
            val cell = signUseCase(
                context = context,
                wallet = wallet,
                unsignedBody = unsignedBody,
                ledgerTransaction = ledgerTransactions.firstOrNull(),
                seqNo = message.seqNo
            )
            cells.add(cell)
        }

        val confirmationTimeSeconds = getConfirmationTimeMillis() / 1000.0

        val source = if (request.appUri.host == "signRaw") {
            "transfer-url"
        } else if (request.appUri.scheme == "tonkeeper") {
            "local"
        } else {
            request.appUri.host ?: "unknown"
        }

        val states = mutableListOf<SendBlockchainState>()
        for (cell in cells) {
            val boc = cell.base64()
            val status = transactionManager.send(
                wallet = wallet,
                boc = boc,
                withBattery = isBattery,
                source = source,
                confirmationTime = confirmationTimeSeconds
            )
            states.add(status)
        }

        val isSuccessful = states.all { it == SendBlockchainState.SUCCESS }

        if (isSuccessful) {
            val feePaid = when {
                isBattery -> "battery"
                else -> "ton"
            }
            AnalyticsHelper.tcSendSuccess(
                installId = settingsRepository.installId,
                url = request.appUri.toString(),
                address = request.targetAddressValue,
                feePaid = feePaid
            )
            emit(cells.map { it.base64() }.toTypedArray())
        } else {
            throw IllegalStateException("Failed to send transaction to blockchain: $states")
        }
    }.flowOn(Dispatchers.IO)

    private fun getConfirmationTimeMillis(): Long {
        return emulationReadyDate.get() - System.currentTimeMillis()
    }

    private suspend fun transfers(compressedTokens: List<AccountTokenEntity>, forEmulation: Boolean, batteryEnabled: Boolean): List<WalletTransfer> {
        val excessesAddress = if (!forEmulation && isBattery.get()) {
            batteryRepository.getConfig(wallet.testnet).excessesAddress
        } else null

        return request.getTransfers(
            wallet = wallet,
            compressedTokens = compressedTokens,
            excessesAddress = excessesAddress,
            api = api,
            batteryEnabled = batteryEnabled
        )
    }

    private suspend fun getTokens(): List<AccountTokenEntity> {
        return tokenRepository.get(currency, wallet.accountId, wallet.testnet, true) ?: emptyList()
    }

}