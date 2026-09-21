package com.tonapps.tonkeeper.ui.screen.send.transaction

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.icu.Coins
import com.tonapps.ledger.ton.Transaction
import com.tonapps.blockchain.model.legacy.Amount
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events
import com.tonapps.core.helper.TON_COIN_ASSET_ID
import com.tonapps.core.helper.TransactionSentAnalytics
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.extensions.getTransfers
import com.tonapps.tonkeeper.extensions.method
import com.tonapps.tonkeeper.helper.BatteryHelper
import com.tonapps.wallet.data.tx.TransactionManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.blockchain.model.legacy.errors.InsufficientBalanceType
import com.tonapps.deposit.screens.send.state.SendFee
import com.tonapps.deposit.usecase.emulation.Emulated
import com.tonapps.deposit.usecase.emulation.Emulated.Companion.buildFee
import com.tonapps.deposit.usecase.emulation.EmulationUseCase
import com.tonapps.deposit.usecase.emulation.InsufficientBalanceError
import com.tonapps.deposit.usecase.sign.SignUseCase
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.APIException
import com.tonapps.wallet.api.getDebugMessage
import com.tonapps.wallet.api.extensions.toTokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.jettonAssetId
import com.tonapps.wallet.data.multichain.account.nftAssetId
import com.tonapps.wallet.data.multichain.account.tonCoinAssetId
import com.tonapps.blockchain.model.legacy.MessageBodyEntity
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.bus.core.IssueHelper
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.BatteryTransaction
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.PreferredFeeMethod
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class SendTransactionViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val request: SignRequestEntity,
    private val batteryTransactionType: BatteryTransaction,
    private val forceRelayer: Boolean,
    private val broadcastVia: BroadcastVia,
    private val analyticsContext: SendTransactionAnalyticsContext,
    private val accountRepository: AccountRepository,
    private val tokenRepository: TokenRepository,
    private val settingsRepository: SettingsRepository,
    private val signUseCase: SignUseCase,
    private val api: API,
    private val historyHelper: HistoryHelper,
    private val emulationUseCase: EmulationUseCase,
    private val transactionManager: TransactionManager,
    private val batteryRepository: BatteryRepository,
    private val ratesRepository: RatesRepository,
    private val analytics: AnalyticsHelper
) : BaseWalletVM(app) {

    private val currency = settingsRepository.currency
    private val isBattery = AtomicBoolean(false)

    private val _stateFlow = MutableStateFlow<SendTransactionState>(SendTransactionState.Loading)
    val stateFlow = _stateFlow.asStateFlow()

    private val emulationReadyDate = AtomicLong(0)

    var message: MessageBodyEntity? = null

    private var tonDetails: SendTransactionState.Details? = null
    private var batteryDetails: SendTransactionState.Details? = null

    private data class AnalyticsTransfer(
        val asset: String,
        val amount: Double,
    )

    @Volatile
    private var tonAnalyticsTransfer: AnalyticsTransfer? = null

    @Volatile
    private var batteryAnalyticsTransfer: AnalyticsTransfer? = null

    val feeOptions: List<SendFee>
        get() = listOfNotNull(
            batteryDetails?.fee,
            tonDetails?.fee,
        )

    private val currentFeeAsset: Events.SendNative.SendNativeFeeAsset
        get() = if (isBattery.get()) {
            Events.SendNative.SendNativeFeeAsset.BatteryCharges
        } else {
            Events.SendNative.SendNativeFeeAsset.Coin
        }

    init {
        analytics.tcViewConfirm(
            request.appUri.toString(),
            request.targetAddressValue
        )
        viewModelScope.launch(Dispatchers.IO) {
            val tokens = getTokens()
            val useBattery = BatteryHelper.isBatteryIsEnabledTx(
                wallet,
                batteryTransactionType,
                settingsRepository,
                accountRepository,
                batteryRepository
            )
            try {
                val transfers = transfers(tokens.filter { it.isRequestMinting }, true, useBattery)

                message = messageBody(transfers)

                val tonDeferred = async {
                    emulationUseCase(
                        message = message!!,
                        useBattery = false,
                        forceRelayer = false,
                        params = !request.ignoreInsufficientBalance,
                        checkTonBalance = true,
                    )
                }

                val batteryDeferred = async {
                    if (useBattery || forceRelayer) {
                        BatteryHelper.emulation(
                            wallet = wallet,
                            message = message!!,
                            emulationUseCase = emulationUseCase,
                            accountRepository = accountRepository,
                            batteryRepository = batteryRepository,
                            forceRelayer = forceRelayer,
                            params = !request.ignoreInsufficientBalance
                        )
                    } else {
                        null
                    }
                }

                val batteryEmulated = batteryDeferred.await()

                batteryDetails = if (batteryEmulated != null && !batteryEmulated.failed) {
                    batteryAnalyticsTransfer = batteryEmulated.analyticsTransfer()
                    createDetails(batteryEmulated)
                } else {
                    null
                }

                val tonEmulated = tonDeferred.await()

                emulationReadyDate.set(System.currentTimeMillis())

                val error = tonEmulated.error
                if (tonEmulated.failed && error is InsufficientBalanceError && batteryDetails == null && !request.ignoreInsufficientBalance) {
                    _stateFlow.value = SendTransactionState.InsufficientBalance(
                        wallet = wallet,
                        balance = Amount(error.accountBalance),
                        required = Amount(error.totalAmount),
                        withRechargeBattery = forceRelayer || useBattery,
                        singleWallet = isSingleWallet(),
                        type = InsufficientBalanceType.InsufficientTONBalance
                    )

                    return@launch
                }

                tonDetails = if (!tonEmulated.failed) {
                    tonAnalyticsTransfer = tonEmulated.analyticsTransfer()
                    createDetails(tonEmulated)
                } else {
                    null
                }

                val preferredFeeMethod = settingsRepository.getPreferredFeeMethod(wallet.id)
                if (preferredFeeMethod == PreferredFeeMethod.BATTERY && batteryDetails != null) {
                    _stateFlow.value = batteryDetails!!
                    isBattery.set(true)
                    return@launch
                }
                if (preferredFeeMethod == PreferredFeeMethod.TON && tonDetails != null) {
                    _stateFlow.value = tonDetails!!
                    isBattery.set(false)
                    return@launch
                }

                if (batteryDetails != null) {
                    _stateFlow.value = batteryDetails!!
                    isBattery.set(true)
                } else if (tonDetails != null) {
                    _stateFlow.value = tonDetails!!
                    isBattery.set(false)
                } else {
                    _stateFlow.value = SendTransactionState.FailedEmulation
                }
            } catch (e: Throwable) {
                IssueHelper.recordException(
                    APIException.Emulation(
                        boc = message?.createSignedBody(
                            EmptyPrivateKeyEd25519.invoke(),
                            forceRelayer || useBattery
                        )?.base64() ?: "failed",
                        sourceUri = request.appUri,
                        cause = e
                    )
                )

                val tonBalance = getTONBalance()
                if (tonBalance == Coins.ZERO && !request.ignoreInsufficientBalance) {
                    _stateFlow.value = SendTransactionState.InsufficientBalance(
                        wallet = wallet,
                        balance = Amount(tonBalance),
                        required = Amount(Coins.of(0.1)),
                        withRechargeBattery = forceRelayer || useBattery,
                        singleWallet = isSingleWallet(),
                        type = InsufficientBalanceType.InsufficientTONBalance
                    )
                } else {
                    toast(e.getDebugMessage() ?: getString(Localization.unknown_error))
                    _stateFlow.value = SendTransactionState.FailedEmulation
                }
            }
        }
    }

    private suspend fun createDetails(emulated: Emulated): SendTransactionState.Details {
        val fee: SendFee =
            emulated.buildFee(wallet, api, accountRepository, batteryRepository, ratesRepository)

        val details = historyHelper.create(wallet, emulated, fee)
        val totalFormatBuilder = StringBuilder(getString(Localization.total, emulated.totalFormat))
        if (emulated.nftCount > 0) {
            totalFormatBuilder.append(" + ").append(emulated.nftCount).append(" NFT")
        }

        return SendTransactionState.Details(
            emulated = details,
            totalFormat = if (emulated.failed) {
                getString(Localization.unknown)
            } else {
                totalFormatBuilder.toString()
            },
            isDangerous = emulated.total.isDangerous,
            nftCount = emulated.nftCount,
            failed = emulated.failed,
            fee = fee,
        )
    }

    private suspend fun isSingleWallet(): Boolean {
        return 1 >= accountRepository.getWallets().size
    }

    private suspend fun getTONBalance(): Coins {
        val balance = tokenRepository.getTON(
            settingsRepository.currency,
            wallet.accountId,
            wallet.network
        )?.balance?.value
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
            transactions.add(
                Transaction.fromWalletTransfer(
                    walletTransfer = transfer,
                    seqno = message.seqNo + index,
                    timeout = message.validUntil
                )
            )
        }

        return transactions.toList()
    }

    private suspend fun messageBody(transfers: List<WalletTransfer>): MessageBodyEntity {
        val seqNo = request.seqNo ?: accountRepository.getSeqno(wallet)
        return accountRepository.messageBody(
            wallet = wallet,
            seqNo = seqNo,
            validUntil = request.validUntil,
            transfers = transfers
        )
    }

    fun send() = flow {
        val isBattery = isBattery.get()

        val compressedTokens = getTokens().filter { it.isRequestMinting }
        val transfers = transfers(compressedTokens, false, isBattery)
        val message = messageBody(transfers)
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
        for (cell in cells) {
            transactionManager.send(
                wallet = wallet,
                boc = cell,
                withBattery = isBattery,
                source = source,
                confirmationTime = confirmationTimeSeconds,
                viaBattery = isBattery || broadcastVia == BroadcastVia.Battery,
            )
        }
        val feePaid = when {
            isBattery -> "battery"
            else -> "ton"
        }
        analytics.tcSendSuccess(
            url = request.appUri.toString(),
            address = request.targetAddressValue,
            feePaid = feePaid
        )
        val initiatedBy = analyticsContext.sendNativeFrom?.let(TransactionSentAnalytics::initiatedBy)
            ?: if (request.appUri.host == "signRaw") {
                Events.TransactionSent.TransactionSentInitiatedBy.DeepLink
            } else {
                Events.TransactionSent.TransactionSentInitiatedBy.User
            }
        val attachedNano = request.messages.sumOf { it.amount }
        val dappHost = when (initiatedBy) {
            Events.TransactionSent.TransactionSentInitiatedBy.TonconnectLocal,
            Events.TransactionSent.TransactionSentInitiatedBy.TonconnectRemote -> request.appUri.host
            else -> null
        }
        TransactionSentAnalytics.transactionSent(
            wallet = wallet,
            category = Events.TransactionSent.TransactionSentCategory.Call,
            categoryDetail = analyticsContext.transactionSentDetail
                ?: Events.TransactionSent.TransactionSentCategoryDetail.Unknown,
            asset = TON_COIN_ASSET_ID,
            amount = Coins.ofNano(attachedNano.toString()).value.toDouble(),
            feeAsset = TransactionSentAnalytics.feeAsset(currentFeeAsset),
            initiatedBy = initiatedBy,
            appId = dappHost,
            dappUrl = dappHost,
        )
        logSendSuccess(isBattery)
        emit(cells.map { it.base64() }.toTypedArray())
    }.flowOn(Dispatchers.IO)

    private fun Emulated.analyticsTransfer(): AnalyticsTransfer? {
        val actions = consequences?.event?.actions ?: return null

        actions.mapNotNull { action ->
            val swap = action.jettonSwap ?: return@mapNotNull null
            if (!swap.userWallet.address.equalsAddress(wallet.address)) {
                return@mapNotNull null
            }
            val jettonIn = swap.jettonMasterIn
            if (jettonIn != null) {
                AnalyticsTransfer(
                    asset = jettonAssetId(jettonIn.address, wallet.testnet),
                    amount = jettonIn.toTokenEntity().toUIAmount(
                        Coins.ofNano(swap.amountIn, jettonIn.decimals)
                    ).value.toDouble(),
                )
            } else {
                val tonIn = swap.tonIn ?: swap.gramIn ?: return@mapNotNull null
                AnalyticsTransfer(
                    asset = tonCoinAssetId(wallet.testnet),
                    amount = Coins.of(tonIn).value.toDouble(),
                )
            }
        }.mergeSameAsset()?.let { return it }

        actions.mapNotNull { action ->
            val transfer = action.jettonTransfer ?: return@mapNotNull null
            if (transfer.sender?.address?.equalsAddress(wallet.address) != true) {
                return@mapNotNull null
            }
            val token = transfer.jetton.toTokenEntity()
            AnalyticsTransfer(
                asset = jettonAssetId(transfer.jetton.address, wallet.testnet),
                amount = token.toUIAmount(
                    Coins.ofNano(transfer.amount, transfer.jetton.decimals)
                ).value.toDouble(),
            )
        }.mergeSameAsset()?.let { return it }

        actions.mapNotNull { action ->
            val transfer = action.nftItemTransfer ?: return@mapNotNull null
            if (transfer.sender?.address?.equalsAddress(wallet.address) != true) {
                return@mapNotNull null
            }
            AnalyticsTransfer(
                asset = nftAssetId(transfer.nft, wallet.testnet),
                amount = 1.0,
            )
        }.mergeSameAsset()?.let { return it }

        return actions.mapNotNull { action ->
            val transfer = action.tonTransfer ?: return@mapNotNull null
            if (!transfer.sender.address.equalsAddress(wallet.address)) {
                return@mapNotNull null
            }
            AnalyticsTransfer(
                asset = tonCoinAssetId(wallet.testnet),
                amount = Coins.of(transfer.amount).value.toDouble(),
            )
        }.mergeSameAsset()
    }

    private fun List<AnalyticsTransfer>.mergeSameAsset(): AnalyticsTransfer? {
        val first = firstOrNull() ?: return null
        return first.copy(
            amount = filter { it.asset == first.asset }.sumOf { it.amount },
        )
    }

    private fun nativeAnalyticsTransfer(): AnalyticsTransfer {
        val amountNano = request.messages.fold(BigInteger.ZERO) { total, message ->
            total + message.amount
        }
        return AnalyticsTransfer(
            asset = tonCoinAssetId(wallet.testnet),
            amount = Coins.ofNano(amountNano.toString()).value.toDouble(),
        )
    }

    private fun logSendSuccess(isBattery: Boolean) {
        val from = analyticsContext.sendNativeFrom ?: return
        val emulatedTransfer = if (isBattery) {
            batteryAnalyticsTransfer ?: tonAnalyticsTransfer
        } else {
            tonAnalyticsTransfer
        }
        val transfer = emulatedTransfer ?: nativeAnalyticsTransfer()
        analytics.events.sendNative.sendSuccess(
            from = from,
            asset = transfer.asset,
            amount = transfer.amount,
            feeAsset = currentFeeAsset,
            appId = null,
        )
    }

    private fun getConfirmationTimeMillis(): Long {
        return emulationReadyDate.get() - System.currentTimeMillis()
    }

    // private suspend fun getTonBalance() = tokenRepository.getTonBalance(settingsRepository.currency, wallet.accountId, wallet.network)

    private suspend fun transfers(
        compressedTokens: List<AccountTokenEntity>,
        forEmulation: Boolean,
        batteryEnabled: Boolean
    ): List<WalletTransfer> {
        val excessesAddress = if (!forEmulation && isBattery.get()) {
            batteryRepository.getConfig(wallet.network).excessesAddress
        } else {
            null
        }

        return request.getTransfers(
            wallet = wallet,
            compressedTokens = compressedTokens,
            excessesAddress = excessesAddress,
            api = api,
            batteryEnabled = batteryEnabled,
            tonBalance = null
        )
    }

    private suspend fun getTokens(): List<AccountTokenEntity> {
        return tokenRepository.get(currency, wallet.accountId, wallet.network, true) ?: emptyList()
    }

    fun setFeeMethod(fee: SendFee) {
        fee.method?.let {
            settingsRepository.setPreferredFeeMethod(wallet.id, it)
        }

        if (fee is SendFee.Battery) {
            _stateFlow.value = batteryDetails!!
            isBattery.set(true)
        } else {
            _stateFlow.value = tonDetails!!
            isBattery.set(false)
        }
    }

}
