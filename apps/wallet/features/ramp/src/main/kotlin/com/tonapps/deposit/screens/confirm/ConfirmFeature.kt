package com.tonapps.deposit.screens.confirm

import android.app.Application
import android.content.Context
import com.tonapps.blockchain.model.legacy.Amount
import com.tonapps.blockchain.model.legacy.Fee
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.TransferEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.errors.InsufficientBalanceType
import com.tonapps.blockchain.model.legacy.errors.SendBlockchainException
import com.tonapps.blockchain.ton.contract.WalletFeature
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.tron.TronTransfer
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.opTerminal
import com.tonapps.deposit.screens.send.SendException
import com.tonapps.deposit.screens.send.SendParams
import com.tonapps.deposit.screens.send.state.SendDestination
import com.tonapps.deposit.screens.send.state.SendFee
import com.tonapps.deposit.usecase.emulation.EmulationUseCase
import com.tonapps.deposit.usecase.emulation.InsufficientBalanceError
import com.tonapps.deposit.usecase.sign.SignUseCase
import com.tonapps.extensions.currentTimeMillis
import com.tonapps.extensions.currentTimeSecondsInt
import com.tonapps.extensions.generateUuid
import com.tonapps.extensions.getUserMessage
import com.tonapps.extensions.isPositive
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.ledger.ton.getLedgerTransaction
import com.tonapps.legacy.enteties.SendMetadataEntity
import com.tonapps.log.L
import com.tonapps.mvi.MviFeature
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviState
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.props.MviProperty
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.tron.entity.TronResourcesEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.battery.BatteryMapper
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.BatteryTransaction
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.PreferredFeeMethod
import com.tonapps.wallet.data.settings.entities.PreferredTronFeeMethod
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.tx.TransactionManager
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.Plurals
import io.batteryapi.models.EstimatedTronTx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.ton.block.AddrStd
import org.ton.cell.Cell
import java.math.BigInteger
import java.util.concurrent.CancellationException
import kotlin.math.abs

// region MVI Contract
sealed interface ConfirmAction : MviAction {
    data object Init : ConfirmAction
    data class SelectFee(val fee: SendFee) : ConfirmAction
    data class Sign(val context: Context) : ConfirmAction // TODO remove context
    data class Retry(val context: Context) : ConfirmAction // TODO remove context
}

sealed interface ConfirmationError {
    data object Unknown : ConfirmationError
    data object InsufficientBalance : ConfirmationError
    data class Message(val text: String) : ConfirmationError
}

sealed interface ConfirmState : MviState {

    data object Loading : ConfirmState

    data class Ready(
        val token: TokenEntity? = null,
        val walletName: String = "",
        val recipientDisplay: String? = null,
        val recipientAddress: String = "",
        val displayCurrency: WalletCurrency? = null,
        val amountFormatted: CharSequence = "",
        val amountFiatFormatted: CharSequence = "",
        val feeFormatted: CharSequence = "",
        val feeFiatFormatted: CharSequence = "",
        val comment: String? = null,
        val encryptedComment: Boolean = false,
        val selectedFee: SendFee? = null,
        val feeOptions: List<SendFee> = emptyList(),
        val hasMultipleFeeOptions: Boolean = false,
        val signingState: SigningState = SigningState.Idle,
        val isMax: Boolean = false,
        val error: ConfirmationError? = null,
        val estimatedDurationSeconds: Int? = null,
        val withdrawalFeeFormatted: CharSequence? = null,
        val withdrawalFeeFiatFormatted: CharSequence? = null,
        val totalFormatted: CharSequence? = null,
        val totalFiatFormatted: CharSequence? = null,
        val exchangeData: SendParams.Exchange? = null,
    ) : ConfirmState {
        val isExchangeMode: Boolean get() {
            return exchangeData != null
        }
    }

    data class Error(val message: String) : ConfirmState
}

enum class SigningState { Idle, Loading, Success, Failed }

class ConfirmViewState(
    val global: MviProperty<ConfirmState>,
) : MviViewState

sealed interface ConfirmEvent {
    data class ShowInsufficientBalance(
        val balance: Amount,
        val required: Amount,
        val withRechargeBattery: Boolean,
        val singleWallet: Boolean,
        val type: InsufficientBalanceType,
    ) : ConfirmEvent
}

// endregion

class ConfirmFeature(
    val params: SendParams,
    private val app: Application, // TODO remove from here
    private val accountRepository: AccountRepository,
    private val api: API,
    private val settingsRepository: SettingsRepository,
    private val tokenRepository: TokenRepository,
    private val ratesRepository: RatesRepository,
    private val batteryRepository: BatteryRepository,
    private val transactionManager: TransactionManager,
    private val emulationUseCase: EmulationUseCase,
    private val signUseCase: SignUseCase,
) : MviFeature<ConfirmAction, ConfirmState, ConfirmViewState>(
    initState = ConfirmState.Loading,
    initAction = ConfirmAction.Init,
) {

    private val relay = MviRelay<ConfirmEvent>()
    val events = relay.events

    // region Internal State

    private val currency: WalletCurrency get() = params.currency
    private val isNft: Boolean get() = params.nftAddress.isNotBlank()

    private val isBatteryDisabled: Boolean
        get() = api.getConfig(wallet.network).flags.disableBattery

    private lateinit var wallet: WalletEntity
    private var tonTransfer: TransferEntity? = null
    private var tronTransfer: TronTransfer? = null
    private var tronResources: TronResourcesEntity? = null

    private var tonFee: SendFee.Ton? = null
    private var gaslessFee: SendFee.Gasless? = null
    private var batteryFee: SendFee.Battery? = null
    private var tronTrxFee: SendFee.TronTrx? = null
    private var tronTonFee: SendFee.TronTon? = null

    private val feeOptions: List<SendFee>
        get() = listOfNotNull(batteryFee, tonFee, gaslessFee, tronTonFee, tronTrxFee)

    private val queryId: BigInteger = TransferEntity.newWalletQueryId()
    private var tokenCustomPayload: TokenEntity.TransferPayload? = null

    // endregion

    private fun getWithdrawFeePaidIn(fee: SendFee?): Events.WithdrawFlow.WithdrawFlowFeePaidIn {
        return when (fee) {
            is SendFee.Ton -> Events.WithdrawFlow.WithdrawFlowFeePaidIn.Ton
            is SendFee.Gasless -> Events.WithdrawFlow.WithdrawFlowFeePaidIn.Gasless
            is SendFee.Battery -> Events.WithdrawFlow.WithdrawFlowFeePaidIn.Battery
            is SendFee.TronTrx -> Events.WithdrawFlow.WithdrawFlowFeePaidIn.Trx
            is SendFee.TronTon -> Events.WithdrawFlow.WithdrawFlowFeePaidIn.Ton
            null -> Events.WithdrawFlow.WithdrawFlowFeePaidIn.Ton
        }
    }

    private fun getSellAsset(token: TokenEntity): Events.WithdrawFlow.WithdrawFlowSellAsset {
        return when {
            token.address == WalletCurrency.USDT_TON.address -> Events.WithdrawFlow.WithdrawFlowSellAsset.TonJettonUSDT
            token.address == WalletCurrency.USDT_TRON.address -> Events.WithdrawFlow.WithdrawFlowSellAsset.TronTrc20USDT
            else -> Events.WithdrawFlow.WithdrawFlowSellAsset.TonNativeTON
        }
    }

    private fun getFeePaidIn(fee: SendFee?): Events.SendNative.SendNativeFeePaidIn {
        return when (fee) {
            is SendFee.Ton -> Events.SendNative.SendNativeFeePaidIn.Ton
            is SendFee.Gasless -> Events.SendNative.SendNativeFeePaidIn.Gasless
            is SendFee.Battery -> Events.SendNative.SendNativeFeePaidIn.Battery
            is SendFee.TronTrx -> Events.SendNative.SendNativeFeePaidIn.Trx
            is SendFee.TronTon -> Events.SendNative.SendNativeFeePaidIn.Ton
            null -> Events.SendNative.SendNativeFeePaidIn.Ton
        }
    }

    override fun createViewState(): ConfirmViewState {
        return buildViewState {
            ConfirmViewState(mviProperty { it })
        }
    }

    override suspend fun executeAction(action: ConfirmAction) {
        when (action) {
            is ConfirmAction.Init -> handleInit()
            is ConfirmAction.SelectFee -> handleSelectFee(action.fee)
            is ConfirmAction.Sign -> handleSign(action.context)
            is ConfirmAction.Retry -> {
                setState<ConfirmState.Ready> {
                    copy(
                        error = null,
                        signingState = SigningState.Idle
                    )
                }
                handleSign(action.context)
            }
        }
    }

    // region Init

    private suspend fun handleInit() {
        wallet = accountRepository.getWalletById(params.walletId)
            ?: return // TODO hide

        val operationId = generateUuid()
        val startedAtMs = currentTimeMillis()
        AnalyticsHelper.Default.events.redOperations.opAttempt(
            operationId = operationId,
            flow = Events.RedOperations.RedOperationsFlow.Transfer,
            operation = Events.RedOperations.RedOperationsOperation.Emulate,
            attemptSource = null,
            startedAtMs = currentTimeSecondsInt(),
            otherMetadata = null,
        )
        try {
            if (params.selectedToken.isTrc20) {
                initTron()
            } else {
                initTon()
            }
            val finishedAtMs = currentTimeMillis()
            AnalyticsHelper.Default.events.redOperations.opTerminal(
                operationId = operationId,
                flow = Events.RedOperations.RedOperationsFlow.Transfer,
                operation = Events.RedOperations.RedOperationsOperation.Emulate,
                durationMs = (finishedAtMs - startedAtMs).toDouble(),
                finishedAtMs = currentTimeSecondsInt(),
                error = null,
            )
        } catch (e: Throwable) {
            L.e("ConfirmFeature", "Init error", e)
            val finishedAtMs = currentTimeMillis()
            AnalyticsHelper.Default.events.redOperations.opTerminal(
                operationId = operationId,
                flow = Events.RedOperations.RedOperationsFlow.Transfer,
                operation = Events.RedOperations.RedOperationsOperation.Emulate,
                durationMs = (finishedAtMs - startedAtMs).toDouble(),
                finishedAtMs = currentTimeSecondsInt(),
                error = e,
            )
            setState { ConfirmState.Error(e.message ?: "Unknown error") }
        }
    }

    private suspend fun initTon() {
        withContext(Dispatchers.IO) {
            val transfer = buildTonTransfer()
            tonTransfer = transfer
            val fee = calculateFee(transfer)

            val insufficientInfo = checkInsufficientBalance(fee, transfer)
            if (insufficientInfo != null) {
                relay.emit(insufficientInfo)
                setState { ConfirmState.Error("Insufficient balance") }
                return@withContext
            }

            transitionToReady(fee)
        }
    }

    private suspend fun initTron() {
        withContext(Dispatchers.IO) {
            val transfer = buildTronTransfer()
            tronTransfer = transfer

            resetFees()
            val resources = api.tron.estimateTransferResources(transfer)
            tronResources = resources

            coroutineScope {
                val batteryFeeDeferred = async { getTronBatteryFee(transfer, resources) }
                val trxFeeDeferred = async { getTronTrxFee(resources) }

                batteryFee = batteryFeeDeferred.await()
                tronTonFee = getTronTonFee(batteryFee?.estimatedTron)
                tronTrxFee = trxFeeDeferred.await()
            }

            var fee: SendFee? = null

            // Default selection
            if (batteryFee != null && batteryFee!!.enoughCharges) fee = batteryFee
            else if (tronTonFee != null && tronTonFee!!.enoughBalance) fee = tronTonFee
            else if (tronTrxFee != null && tronTrxFee!!.enoughBalance) fee = tronTrxFee

            // Preferred method override
            val preferredFeeMethod = settingsRepository.getPreferredTronFeeMethod(wallet.id)
            if (preferredFeeMethod == PreferredTronFeeMethod.BATTERY && batteryFee?.enoughCharges == true) fee =
                batteryFee
            else if (preferredFeeMethod == PreferredTronFeeMethod.TON && tronTonFee?.enoughBalance == true) fee =
                tronTonFee
            else if (preferredFeeMethod == PreferredTronFeeMethod.TRX && tronTrxFee?.enoughBalance == true) fee =
                tronTrxFee

            if (fee != null) {
                transitionToReady(fee)
            } else {
                relay.emit(
                    ConfirmEvent.ShowInsufficientBalance(
                        balance = Amount(tronTrxFee!!.balance, TokenEntity.TRX),
                        required = Amount(tronTrxFee!!.amount.value, TokenEntity.TRX),
                        singleWallet = 1 >= getWalletCount(),
                        withRechargeBattery = false,
                        type = InsufficientBalanceType.InsufficientBalanceForFee,
                    )
                )
                setState { ConfirmState.Error("Insufficient balance for fee") }
            }
        }
    }

    private suspend fun transitionToReady(fee: SendFee) {
        val token = params.selectedToken
        val dest = params.destination
        val tokenAmount = params.tokenAmount

        val rates = ratesRepository.getRates(wallet.network, currency, token.address)

        // Adjust amount for max + fee
        var displayAmount = tokenAmount
        if (params.isMaxAmount) {
            when {
                fee is SendFee.Gasless -> displayAmount = tokenAmount - fee.amount.value
                token.isTon && fee is SendFee.Ton -> displayAmount = tokenAmount - fee.amount.value
            }
            if (displayAmount.isNegative) displayAmount = Coins.ZERO
        }

        val amountFormatted =
            CurrencyFormatter.formatFull(token.symbol, displayAmount, token.balance.token.decimals)
        val amountFiatFormatted =
            CurrencyFormatter.formatFiat(currency.code, rates.convert(token.address, displayAmount))

        val feeDisplay = formatFeeDisplay(fee)

        val recipientDisplay = when {
            params.exchangeData != null -> params.exchangeData.exchangeAddress
            else -> when (dest) {
                is SendDestination.TonAccount -> dest.displayName
                is SendDestination.TronAccount -> null
                else -> null
            }
        }

        val recipientAddress = when (dest) {
            is SendDestination.TonAccount -> dest.displayAddress
            is SendDestination.TronAccount -> dest.address
            else -> ""
        }

        // Compute total for exchange mode: amount + network fee + withdrawal fee
        val totalFormatted: CharSequence?
        val totalFiatFormatted: CharSequence?
        if (params.exchangeData != null) {
            val networkFeeAmount = when (fee) {
                is SendFee.Gasless -> fee.amount.value
                else -> Coins.ZERO
            }

            val total = displayAmount + networkFeeAmount
            totalFormatted = CurrencyFormatter.formatFull(token.symbol, total, token.balance.token.decimals)
            val totalFiat = rates.convert(token.address, total)
            totalFiatFormatted = CurrencyFormatter.formatFiat(currency.code, totalFiat)
        } else {
            totalFormatted = null
            totalFiatFormatted = null
        }

        setState {
            ConfirmState.Ready(
                token = token.balance.token,
                walletName = wallet.label.name,
                recipientDisplay = recipientDisplay,
                recipientAddress = recipientAddress,
                amountFormatted = amountFormatted,
                amountFiatFormatted = amountFiatFormatted,
                feeFormatted = feeDisplay.first,
                feeFiatFormatted = feeDisplay.second,
                comment = params.comment,
                encryptedComment = params.encryptedComment,
                selectedFee = fee,
                feeOptions = feeOptions,
                hasMultipleFeeOptions = feeOptions.size > 1,
                isMax = params.isMaxAmount,
                displayCurrency = params.exchangeData?.currency,
                estimatedDurationSeconds = params.exchangeData?.estimatedDurationSeconds,
                withdrawalFeeFormatted = params.exchangeData?.withdrawalFee?.let { fee ->
                    val feeCoins = runCatching { Coins.of(fee) }.getOrNull() ?: return@let null
                    "≈ ${CurrencyFormatter.format(token.symbol, feeCoins, replaceSymbol = false)}"
                },
                withdrawalFeeFiatFormatted = params.exchangeData?.withdrawalFee?.let { fee ->
                    val feeCoins = runCatching { Coins.of(fee) }.getOrNull() ?: return@let null
                    val fiatAmount = rates.convert(token.address, feeCoins)
                    "≈ ${CurrencyFormatter.formatFiat(currency.code, fiatAmount)}"
                },
                totalFormatted = totalFormatted,
                totalFiatFormatted = totalFiatFormatted,
                exchangeData = params.exchangeData,
            )
        }
    }

    // endregion

    // region Fee Selection

    private suspend fun handleSelectFee(fee: SendFee) = withContext(Dispatchers.IO) {
        if (fee is SendFee.TronTrx && !fee.enoughBalance) return@withContext
        if (fee is SendFee.TronTon && !fee.enoughBalance) return@withContext
        if (fee is SendFee.Battery && !fee.enoughCharges) return@withContext

        // Persist preferred fee method
        if (params.selectedToken.isTrc20) {
            when (fee) {
                is SendFee.Battery -> settingsRepository.setPreferredTronFeeMethod(
                    wallet.id,
                    PreferredTronFeeMethod.BATTERY
                )

                is SendFee.TronTon -> settingsRepository.setPreferredTronFeeMethod(
                    wallet.id,
                    PreferredTronFeeMethod.TON
                )

                is SendFee.TronTrx -> settingsRepository.setPreferredTronFeeMethod(
                    wallet.id,
                    PreferredTronFeeMethod.TRX
                )

                else -> {}
            }
        } else {
            when (fee) {
                is SendFee.Battery -> settingsRepository.setPreferredFeeMethod(
                    wallet.id,
                    PreferredFeeMethod.BATTERY
                )

                is SendFee.Gasless -> settingsRepository.setPreferredFeeMethod(
                    wallet.id,
                    PreferredFeeMethod.GASLESS
                )

                is SendFee.Ton -> settingsRepository.setPreferredFeeMethod(
                    wallet.id,
                    PreferredFeeMethod.TON
                )

                else -> {}
            }
        }

        // Update confirm state with new fee
        val feeDisplay = formatFeeDisplay(fee)

        // Recompute total for exchange mode
        val updatedTotal = if (params.exchangeData != null) {
            val token = params.selectedToken
            val rates = ratesRepository.getRates(wallet.network, currency, token.address)
            val networkFeeAmount = (fee as? SendFee.Gasless)?.amount?.value ?: Coins.ZERO
            val total = params.tokenAmount + networkFeeAmount
            val totalFormatted = CurrencyFormatter.formatFull(token.symbol, total, token.balance.token.decimals)
            val totalFiat = rates.convert(token.address, total)
            val totalFiatFormatted = CurrencyFormatter.formatFiat(currency.code, totalFiat)
            totalFormatted to totalFiatFormatted
        } else null

        setState<ConfirmState.Ready> {
            copy(
                selectedFee = fee,
                feeFormatted = feeDisplay.first,
                feeFiatFormatted = feeDisplay.second,
                totalFormatted = updatedTotal?.first ?: totalFormatted,
                totalFiatFormatted = updatedTotal?.second ?: totalFiatFormatted,
            )
        }
    }

    // endregion

    // region Signing

    private suspend fun handleSign(context: Context) {
        setState<ConfirmState.Ready> { copy(signingState = SigningState.Loading) }

        val analyticsFrom = params.analyticsFrom
        val token = params.selectedToken.balance.token
        val amount = params.tokenAmount.value.toDouble()
        val readyState = obtainSpecificState<ConfirmState.Ready>()
        val feePaidIn = getFeePaidIn(readyState?.selectedFee)

        AnalyticsHelper.Default.events.sendNative.sendConfirm(
            from = analyticsFrom,
            assetNetwork = token.blockchain.id,
            tokenSymbol = token.symbol,
            amount = amount,
            feePaidIn = feePaidIn,
            appId = null,
        )

        if (params.exchangeData != null) {
            AnalyticsHelper.Default.events.withdrawFlow.withdrawSendConfirm(
                sellAsset = getSellAsset(token),
                assetNetwork = token.blockchain.id,
                tokenSymbol = token.symbol,
                amount = amount,
                feePaidIn = getWithdrawFeePaidIn(readyState?.selectedFee),
            )
        }

        val operationId = generateUuid()
        val startedAtMs = currentTimeMillis()
        AnalyticsHelper.Default.events.redOperations.opAttempt(
            operationId = operationId,
            flow = Events.RedOperations.RedOperationsFlow.Transfer,
            operation = Events.RedOperations.RedOperationsOperation.Send,
            attemptSource = null,
            startedAtMs = currentTimeSecondsInt(),
            otherMetadata = null,
        )

        try {
            val state = obtainSpecificState<ConfirmState.Ready>()

            if (
                state?.selectedFee is SendFee.TronTrx
                || state?.selectedFee is SendFee.TronTon
                || state?.selectedFee is SendFee.Battery
                && tronTransfer != null
            ) {
                signTron(context)
            } else {
                signTon(context)
            }

            val finishedAtMs = currentTimeMillis()
            AnalyticsHelper.Default.events.redOperations.opTerminal(
                operationId = operationId,
                flow = Events.RedOperations.RedOperationsFlow.Transfer,
                operation = Events.RedOperations.RedOperationsOperation.Send,
                durationMs = (finishedAtMs - startedAtMs).toDouble(),
                finishedAtMs = currentTimeSecondsInt(),
                error = null,
            )

            AnalyticsHelper.Default.events.sendNative.sendSuccess(
                from = analyticsFrom,
                assetNetwork = token.blockchain.id,
                tokenSymbol = token.symbol,
                amount = amount,
                feePaidIn = feePaidIn,
                appId = null,
            )

            if (params.exchangeData != null) {
                AnalyticsHelper.Default.events.withdrawFlow.withdrawSendSuccess(
                    sellAsset = getSellAsset(token),
                    assetNetwork = token.blockchain.id,
                    tokenSymbol = token.symbol,
                    amount = amount,
                    feePaidIn = getWithdrawFeePaidIn(readyState?.selectedFee),
                )
            }

            setState<ConfirmState.Ready> { copy(signingState = SigningState.Success) }
        } catch (e: Throwable) {
            L.e("ConfirmFeature", "Sign error", e)
            val finishedAtMs = currentTimeMillis()
            AnalyticsHelper.Default.events.redOperations.opTerminal(
                operationId = operationId,
                flow = Events.RedOperations.RedOperationsFlow.Transfer,
                operation = Events.RedOperations.RedOperationsOperation.Send,
                durationMs = (finishedAtMs - startedAtMs).toDouble(),
                finishedAtMs = currentTimeSecondsInt(),
                error = e,
            )
            if (e is CancellationException) {
                setState<ConfirmState.Ready> { copy(signingState = SigningState.Idle) }
            } else {

                AnalyticsHelper.Default.events.sendNative.sendFailed(
                    from = analyticsFrom,
                    assetNetwork = token.blockchain.id,
                    tokenSymbol = token.symbol,
                    amount = amount,
                    feePaidIn = feePaidIn,
                    errorCode = 0,
                    errorMessage = e.message ?: "unknown",
                    appId = null,
                )

                val error = when (e) {
                    is SendException.InsufficientBalance -> ConfirmationError.InsufficientBalance
                    is SendBlockchainException -> e.getUserMessage(context)
                        ?.let { ConfirmationError.Message(it) } ?: ConfirmationError.Unknown

                    else -> ConfirmationError.Unknown
                }

                setState<ConfirmState.Ready> {
                    copy(signingState = SigningState.Failed, error = error)
                }
            }
        }
    }

    private suspend fun signTon(context: Context) {
        val transfer = tonTransfer ?: throw IllegalStateException("Ton transfer is null")
        val confirmState = obtainSpecificState<ConfirmState.Ready>()
        val fee = confirmState?.selectedFee ?: throw IllegalStateException("Fee is null")

        // Battery charge validation
        if (fee is SendFee.Battery) {
            val batteryCharges = getBatteryCharges()
            val batteryConfig = batteryRepository.getConfig(wallet.network)
            val txCharges = BatteryMapper.calculateChargesAmount(
                Coins.of(abs(fee.extra)).value,
                batteryConfig.chargeCost,
            )
            if (txCharges > batteryCharges) {
                throw SendException.InsufficientBalance()
            }
        }

        val excessesAddress = if (fee is SendFee.RelayerFee) fee.excessesAddress else null
        val additionalGifts = if (fee is SendFee.Gasless) {
            listOf(
                transfer.gaslessInternalGift(
                    jettonAmount = fee.amount.value,
                    batteryAddress = fee.excessesAddress
                )
            )
        } else emptyList()

        val privateKey = if (transfer.commentEncrypted) {
            accountRepository.getPrivateKey(wallet.id)
        } else null

        val internalMessage = excessesAddress != null

        val jettonTransferAmount = when (fee) {
            is SendFee.Gasless -> TransferEntity.BASE_FORWARD_AMOUNT
            is SendFee.Extra -> {
                val extra = Coins.of(fee.extra)
                when {
                    transfer.token.isRequestMinting || transfer.token.customPayloadApiUri != null -> TransferEntity.POINT_ONE_TON
                    extra.isPositive -> TransferEntity.BASE_FORWARD_AMOUNT
                    extra.isZero -> TransferEntity.POINT_ONE_TON
                    else -> Coins.of(abs(fee.extra)) + TransferEntity.BASE_FORWARD_AMOUNT
                }
            }

            is SendFee.Ton -> when {
                transfer.token.isRequestMinting || transfer.token.customPayloadApiUri != null -> TransferEntity.POINT_ONE_TON
                fee.amount.isRefund -> TransferEntity.BASE_FORWARD_AMOUNT
                else -> fee.amount.value + TransferEntity.BASE_FORWARD_AMOUNT
            }

            else -> TransferEntity.POINT_ONE_TON
        }

        val boc = signUseCase(
            context = context,
            wallet = wallet,
            unsignedBody = transfer.getUnsignedBody(
                privateKey = privateKey,
                internalMessage = internalMessage,
                additionalGifts = additionalGifts,
                excessesAddress = excessesAddress,
                jettonAmount = if (transfer.max && fee is SendFee.Gasless) {
                    transfer.amount - fee.amount.value
                } else null,
                jettonTransferAmount = jettonTransferAmount,
            ),
            seqNo = transfer.seqno,
            ledgerTransaction = transfer.getLedgerTransaction(jettonTransferAmount),
        )

        send(boc, internalMessage)
    }

    private suspend fun signTron(context: Context) {
        val confirmState = obtainSpecificState<ConfirmState.Ready>()
        val fee = confirmState?.selectedFee ?: throw IllegalStateException("Fee is null")
        val transfer = tronTransfer ?: throw IllegalStateException("Tron transfer is null")
        val transaction = api.tron.buildSmartContractTransaction(transfer).extendExpiration()
        val resources = tronResources ?: throw IllegalStateException("Tron resources is null")
        val privateKey = accountRepository.getPrivateKey(wallet.id)
            ?: throw IllegalStateException("Private key is null")

        val signedTransaction = signUseCase(
            context = context,
            wallet = wallet,
            transaction = transaction,
        )

        val tonProofToken = accountRepository.requestTonProofToken(wallet)
            ?: throw IllegalStateException("TonProofToken is null")

        when (fee) {
            is SendFee.Battery -> {
                api.tron.sendWithBattery(
                    transaction = signedTransaction,
                    resources = resources,
                    tronAddress = transfer.from,
                    tonProofToken = tonProofToken,
                )
            }

            is SendFee.TronTon -> {
                val tonToken = tokenRepository.getTON(currency, wallet.accountId, wallet.network)
                    ?: throw IllegalStateException("TON token not found")
                val tonProofToken = accountRepository.requestTonProofToken(wallet)
                    ?: throw IllegalStateException("TonProofToken is null")
                val sendMetadata = getSendParams()
                val instantFeeTx = TransferEntity.Builder(wallet)
                    .setToken(tonToken.balance)
                    .setSeqno(sendMetadata.seqno)
                    .setValidUntil(sendMetadata.validUntil)
                    .setAmount(fee.amount.value)
                    .setComment("Tron gas fee", false)
                    .setDestination(AddrStd(fee.sendToAddress), EmptyPrivateKeyEd25519.publicKey())
                    .build()
                    .sign(
                        privateKey = privateKey,
                        jettonTransferAmount = TransferEntity.BASE_FORWARD_AMOUNT
                    )

                api.tron.sendWithTon(
                    transaction = signedTransaction,
                    instantFeeTx = instantFeeTx,
                    resources = resources,
                    tronAddress = transfer.from,
                    userPublicKey = wallet.publicKey.base64(),
                    batteryAuthToken = tonProofToken,
                )
            }

            is SendFee.TronTrx -> {
                api.tron.sendWithTrx(
                    transaction = signedTransaction,
                    resources = resources,
                    tronAddress = transfer.from,
                )
            }

            else -> throw IllegalStateException("Invalid fee type for tron transfer")
        }
    }

    private suspend fun send(message: Cell, withBattery: Boolean) {
        transactionManager.send(
            wallet = wallet,
            boc = message,
            withBattery = withBattery,
            source = "",
            confirmationTime = 0.0,
        )
    }

    // endregion

    // region Transfer Building

    private suspend fun buildTonTransfer(): TransferEntity = withContext(Dispatchers.IO) {
        val tokenAmount = params.tokenAmount
        if (tokenAmount.isZero && !isNft) {
            throw IllegalStateException("Amount is empty")
        }

        val comment = params.comment?.ifBlank { null }
        val destination = params.destination as? SendDestination.TonAccount
            ?: throw IllegalStateException("Destination is not TonAccount")

        val token = params.selectedToken
        val customPayload = getTokenCustomPayload(token.balance.token)
        val sendMetadata = getSendParams()

        val builder = TransferEntity.Builder(wallet)
        if (!customPayload.isEmpty) {
            builder.setTokenPayload(customPayload)
        }
        builder.setToken(token.balance)
        builder.setDestination(destination.address, destination.publicKey)
        builder.setSeqno(sendMetadata.seqno)
        builder.setQueryId(queryId)
        comment?.let { builder.setComment(it, params.encryptedComment) }
        builder.setValidUntil(sendMetadata.validUntil)

        if (isNft) {
            builder.setNftAddress(params.nftAddress)
            builder.setBounceable(true)
            builder.setAmount(Coins.ZERO)
            builder.setMax(false)
        } else if (!token.isTon) {
            val isMax = tokenAmount == token.balance.uiBalance
            builder.setMax(isMax)
            builder.setBounceable(true)
            builder.setAmount(
                if (isMax) token.balance.value else token.balance.fromUIBalance(
                    tokenAmount
                )
            )
        } else {
            val tonBalance = getTONBalance()
            builder.setMax(tokenAmount == tonBalance)
            builder.setAmount(tokenAmount)
            builder.setBounceable(destination.isBounce)
        }

        builder.build()
    }

    private suspend fun buildTronTransfer(): TronTransfer = withContext(Dispatchers.IO) {
        val tokenAmount = params.tokenAmount
        val destination = params.destination as? SendDestination.TronAccount
            ?: throw IllegalStateException("Destination is not TronAccount")
        val tronAddress = accountRepository.getTronAddress(wallet.id)
            ?: throw IllegalStateException("Tron address not found")

        TronTransfer(
            from = tronAddress,
            to = destination.address,
            amount = tokenAmount.toBigInteger(),
            contractAddress = params.selectedToken.address,
        )
    }

    // endregion

    // region Fee Calculation

    private fun resetFees() {
        tonFee = null
        gaslessFee = null
        batteryFee = null
        tronTonFee = null
        tronTrxFee = null
    }

    private suspend fun calculateFee(transfer: TransferEntity): SendFee =
        withContext(Dispatchers.IO) {
            resetFees()
            val withRelayer = shouldAttemptWithRelayer(transfer)
            val tonProofToken = accountRepository.requestTonProofToken(wallet)
            val batteryConfig = batteryRepository.getConfig(wallet.network)
            val tokenAddress = transfer.token.token.address
            val excessesAddress = batteryConfig.excessesAddress
            val isGaslessToken = !transfer.token.isTon && batteryConfig.rechargeMethods.any {
                it.supportGasless && it.jettonMaster == tokenAddress
            }
            val isSupportsGasless = wallet.isSupportedFeature(WalletFeature.GASLESS) &&
                    tonProofToken != null && excessesAddress != null && isGaslessToken

            coroutineScope {
                val tonDeferred = async { calculateFeeDefault(transfer) }
                val gaslessDeferred = async {
                    if (isSupportsGasless) {
                        calculateFeeGasless(
                            transfer,
                            excessesAddress!!,
                            tonProofToken!!,
                            tokenAddress
                        )
                    } else null
                }
                val batteryDeferred = async {
                    if (withRelayer && tonProofToken != null && excessesAddress != null) {
                        calculateFeeBattery(transfer, excessesAddress, tonProofToken)
                    } else null
                }

                val tonFeeResult = tonDeferred.await()
                gaslessFee = gaslessDeferred.await()
                batteryFee = batteryDeferred.await()

                tonFee = if (tonFeeResult.error is InsufficientBalanceError) null else tonFeeResult

                // Preferred fee method
                val preferredFeeMethod = settingsRepository.getPreferredFeeMethod(wallet.id)
                if (preferredFeeMethod == PreferredFeeMethod.BATTERY && batteryFee != null) return@coroutineScope batteryFee!!
                if (preferredFeeMethod == PreferredFeeMethod.GASLESS && gaslessFee != null) return@coroutineScope gaslessFee!!
                if (preferredFeeMethod == PreferredFeeMethod.TON && tonFee != null) return@coroutineScope tonFee!!

                // Default selection
                if (batteryFee != null) return@coroutineScope batteryFee!!
                if (gaslessFee != null && tonFee == null) return@coroutineScope gaslessFee!!

                return@coroutineScope tonFeeResult
            }
        }

    private suspend fun calculateFeeDefault(transfer: TransferEntity): SendFee.Ton {
        val jettonTransferAmount = getJettonTransferAmount(transfer)
        val emulated = emulationUseCase(
            message = transfer.getEmulationBody(jettonTransferAmount),
            params = true,
            checkTonBalance = !transfer.isTon || !transfer.max,
        )
        val fee = Fee(emulated.extra.value, emulated.extra.isRefund)
        return SendFee.Ton(
            amount = fee,
            fiatAmount = emulated.extra.fiat,
            fiatCurrency = currency,
            error = emulated.error,
        )
    }

    private suspend fun calculateFeeBattery(
        transfer: TransferEntity,
        excessesAddress: AddrStd,
        tonProofToken: String,
    ): SendFee.Battery? {
        if (api.getConfig(wallet.network).batterySendDisabled) {
            return null
        }

        val message = transfer.signForEstimation(
            internalMessage = true,
            excessesAddress = excessesAddress,
            jettonTransferAmount = TransferEntity.BASE_FORWARD_AMOUNT,
        )

        try {
            val result = batteryRepository.emulate(
                tonProofToken = tonProofToken,
                publicKey = wallet.publicKey,
                network = wallet.network,
                boc = message,
                safeModeEnabled = settingsRepository.isSafeModeEnabled(wallet.network),
            ) ?: return null

            if (!result.withBattery) return null

            val extra = result.consequences.event.extra
            val tonAmount = Coins.of(abs(extra))
            val chargesBalance = getBatteryCharges()
            val batteryConfig = batteryRepository.getConfig(wallet.network)
            val charges =
                BatteryMapper.calculateChargesAmount(tonAmount.value, batteryConfig.chargeCost)

            if (charges > chargesBalance) return null

            val excess = result.excess
            val excessCharges = when {
                excess.isPositive() -> BatteryMapper.calculateChargesAmount(
                    Coins.of(excess).value,
                    batteryConfig.chargeCost
                ).toLong()

                else -> null
            }

            return SendFee.Battery(
                charges = charges,
                chargesBalance = chargesBalance,
                extra = extra,
                excessesAddress = excessesAddress,
                fiatAmount = ratesRepository.getRates(
                    wallet.network,
                    currency,
                    TokenEntity.TON.address
                )
                    .convert(TokenEntity.TON.address, tonAmount),
                fiatCurrency = currency,
                excessCharges = excessCharges,
            )
        } catch (_: Exception) {
            return null
        }
    }

    private suspend fun calculateFeeGasless(
        transfer: TransferEntity,
        excessesAddress: AddrStd,
        tonProofToken: String,
        tokenAddress: String,
    ): SendFee.Gasless? {
        try {
            if (api.getConfig(wallet.network).flags.disableGasless) return null

            val message = transfer.signForEstimation(
                internalMessage = true,
                jettonAmount = if (transfer.max) Coins.of(1, transfer.token.decimals) else null,
                additionalGifts = listOf(
                    transfer.gaslessInternalGift(
                        jettonAmount = Coins.of(1, transfer.token.decimals),
                        batteryAddress = excessesAddress,
                    )
                ),
                excessesAddress = excessesAddress,
                jettonTransferAmount = TransferEntity.BASE_FORWARD_AMOUNT,
            )

            val commission = api.estimateGaslessCost(
                tonProofToken = tonProofToken,
                jettonMaster = tokenAddress,
                cell = message,
                network = wallet.network,
            ) ?: return null

            val gaslessFeeAmount = Coins.ofNano(commission, transfer.token.decimals)

            if (transfer.max && gaslessFeeAmount > transfer.token.value) return null
            if (!transfer.max && gaslessFeeAmount + transfer.amount > transfer.token.value) return null

            val fee = Fee(value = gaslessFeeAmount, isRefund = false, token = transfer.token.token)
            val rates = ratesRepository.getRates(wallet.network, currency, fee.token.address)
            val converted = rates.convert(fee.token.address, fee.value)

            return SendFee.Gasless(
                amount = fee,
                fiatAmount = converted,
                fiatCurrency = currency,
                excessesAddress = excessesAddress,
            )
        } catch (e: Exception) {
            L.d("ConfirmFeature", "Gasless fee failed: ${e.message}")
            return null
        }
    }

    // region Tron Fee

    private suspend fun getTronBatteryFee(
        transfer: TronTransfer,
        resources: TronResourcesEntity,
    ): SendFee.Battery? {
        try {
            val batteryCharges = getBatteryCharges()
            if (isBatteryDisabled && batteryCharges == 0) return null

            val batteryEstimation = api.tron.estimateBatteryCharges(transfer, resources)
            val batteryConfig = batteryRepository.getConfig(wallet.network)
            val tonAmount = BatteryMapper.convertFromCharges(
                batteryEstimation.charges,
                batteryConfig.chargeCost
            )

            val fee = SendFee.Battery(
                charges = batteryEstimation.charges,
                chargesBalance = batteryCharges,
                fiatAmount = ratesRepository.getRates(
                    wallet.network,
                    currency,
                    TokenEntity.TON.address
                )
                    .convert(TokenEntity.TON.address, tonAmount),
                fiatCurrency = currency,
                excessesAddress = AddrStd(wallet.address),
                extra = 0L,
                estimatedTron = batteryEstimation.estimated,
            )

            if (isBatteryDisabled && fee.charges > fee.chargesBalance) return null
            return fee
        } catch (_: Exception) {
            return null
        }
    }

    private suspend fun getTronTonFee(
        batteryEstimated: EstimatedTronTx?,
    ): SendFee.TronTon? {
        if (isBatteryDisabled || batteryEstimated == null) return null

        try {
            val tonEstimation = api.tron.estimateTonFee(batteryEstimated)
            return SendFee.TronTon(
                amount = Fee(value = tonEstimation.fee, isRefund = false),
                balance = getTONBalance(),
                fiatAmount = ratesRepository.getRates(
                    wallet.network,
                    currency,
                    TokenEntity.TON.address
                )
                    .convert(TokenEntity.TON.address, tonEstimation.fee),
                fiatCurrency = currency,
                sendToAddress = tonEstimation.sendToAddress,
            )
        } catch (_: Exception) {
            return null
        }
    }

    private suspend fun getTronTrxFee(resources: TronResourcesEntity): SendFee.TronTrx {
        val trxEstimation = api.tron.estimateTrxFee(resources)
        val trxBalance = getTrxBalance()
        return SendFee.TronTrx(
            amount = Fee(value = trxEstimation.fee, isRefund = false, token = TokenEntity.TRX),
            fiatAmount = ratesRepository.getRates(wallet.network, currency, TokenEntity.TRX.address)
                .convert(TokenEntity.TRX.address, trxEstimation.fee),
            fiatCurrency = currency,
            balance = trxBalance,
        )
    }

    // endregion

    // endregion

    // region Display Helpers

    private suspend fun formatFeeDisplay(fee: SendFee): Pair<CharSequence, CharSequence> {
        return when (fee) {
            is SendFee.TokenFee -> {
                val formatted = CurrencyFormatter.format(fee.amount.token.symbol, fee.amount.value)
                val rates =
                    ratesRepository.getRates(wallet.network, currency, fee.amount.token.address)
                val fiatConverted = rates.convert(fee.amount.token.address, fee.amount.value)
                val fiatFormatted = CurrencyFormatter.format(currency.code, fiatConverted)
                "≈ $formatted" to "≈ $fiatFormatted"
            }

            is SendFee.Battery -> {
                val formatted = app.resources.getQuantityString(
                    Plurals.battery_charges,
                    fee.charges,
                    CurrencyFormatter.format(value = fee.charges.toBigDecimal())
                )
                val excessCharges = fee.excessCharges
                val description = if (excessCharges.isPositive()) {
                    "≈ ${
                        app.getString(
                            Localization.battery_excess, CurrencyFormatter.format(
                                value = excessCharges.toBigDecimal()
                            )
                        )
                    }"
                } else {
                    app.getString(
                        Localization.out_of_available_charges,
                        CurrencyFormatter.format(value = fee.chargesBalance.toBigDecimal())
                    )
                }
                "≈ $formatted" to description
            }

            else -> "" to ""
        }
    }

    private fun checkInsufficientBalance(
        fee: SendFee,
        transfer: TransferEntity,
    ): ConfirmEvent.ShowInsufficientBalance? {
        if (fee is SendFee.Ton && fee.error is InsufficientBalanceError) {
            return ConfirmEvent.ShowInsufficientBalance(
                type = InsufficientBalanceType.InsufficientTONBalance,
                balance = Amount(value = fee.error.accountBalance),
                required = Amount(value = fee.error.totalAmount),
                withRechargeBattery = false,
                singleWallet = false,
            )
        }
        if (fee is SendFee.Gasless && !transfer.max && fee.amount.value + transfer.amount > transfer.token.value) {
            return ConfirmEvent.ShowInsufficientBalance(
                type = InsufficientBalanceType.InsufficientGaslessBalance,
                balance = Amount(value = transfer.token.value, token = transfer.token.token),
                required = Amount(
                    value = fee.amount.value + transfer.amount,
                    token = transfer.token.token
                ),
                withRechargeBattery = false,
                singleWallet = false,
            )
        }
        if (!transfer.isTon && !transfer.isNft && transfer.amount > transfer.token.value) {
            return ConfirmEvent.ShowInsufficientBalance(
                type = InsufficientBalanceType.InsufficientJettonBalance,
                balance = Amount(value = transfer.token.value, token = transfer.token.token),
                required = Amount(value = transfer.amount, token = transfer.token.token),
                withRechargeBattery = false,
                singleWallet = false,
            )
        }
        return null
    }

    // endregion

    // region Helpers

    private suspend fun getSendParams(): SendMetadataEntity = withContext(Dispatchers.IO) {
        coroutineScope {
            val seqnoDeferred = async { accountRepository.getSeqno(wallet) }
            val validUntilDeferred = async { accountRepository.getValidUntil(wallet.network) }
            SendMetadataEntity(
                seqno = seqnoDeferred.await(),
                validUntil = validUntilDeferred.await(),
            )
        }
    }

    private suspend fun getBatteryCharges(): Int = withContext(Dispatchers.IO) {
        accountRepository.requestTonProofToken(wallet)?.let {
            batteryRepository.getCharges(it, wallet.publicKey, wallet.network, true)
        } ?: 0
    }

    private fun getTONBalance(): Coins {
        if (params.selectedToken.isTon) {
            return params.selectedToken.balance.value
        }
        return params.tokens.find { it.isTon }?.balance?.value ?: Coins.ZERO
    }

    private fun getTrxBalance(): Coins {
        return params.tokens.find { it.isTrx }?.balance?.value ?: Coins.ZERO
    }

    private suspend fun getWalletCount(): Int = withContext(Dispatchers.IO) {
        accountRepository.getWallets().size
    }

    private fun shouldAttemptWithRelayer(transfer: TransferEntity): Boolean {
        if ((transfer.isTon && !transfer.isNft) || transfer.wallet.isExternal) return false
        val txType = if (transfer.isNft) BatteryTransaction.NFT else BatteryTransaction.JETTON
        return settingsRepository.batteryIsEnabledTx(transfer.wallet.accountId, txType)
    }

    private suspend fun getJettonTransferAmount(transfer: TransferEntity): Coins {
        try {
            if (transfer.token.isRequestMinting || transfer.token.customPayloadApiUri != null) {
                return TransferEntity.POINT_ONE_TON
            }

            val message = transfer.signForEstimation(
                internalMessage = false, jettonTransferAmount = TransferEntity.ONE_TON
            )
            val emulated = api.emulate(
                cell = message,
                network = transfer.wallet.network,
                address = transfer.wallet.accountId,
                balance = (Coins.ONE + Coins.ONE).toLong(),
                safeModeEnabled = settingsRepository.isSafeModeEnabled(transfer.wallet.network),
            )
            val fee = Fee(emulated?.event?.extra ?: 0L)

            return if (fee.isRefund) {
                TransferEntity.BASE_FORWARD_AMOUNT
            } else {
                fee.value + TransferEntity.BASE_FORWARD_AMOUNT
            }
        } catch (_: Throwable) {
            return TransferEntity.POINT_ONE_TON
        }
    }

    private fun getTokenCustomPayload(token: TokenEntity): TokenEntity.TransferPayload {
        if (token.isTon) {
            return TokenEntity.TransferPayload.empty("TON")
        }

        if (!token.isRequestMinting) {
            return TokenEntity.TransferPayload.empty(token.address)
        }

        tokenCustomPayload?.let {
            if (it.tokenAddress == token.address) return it
        }

        return try {
            val payload = api.getJettonCustomPayload(
                accountId = wallet.accountId,
                jettonId = token.address,
                network = wallet.network,
            ) ?: TokenEntity.TransferPayload.empty(token.address)
            tokenCustomPayload = payload
            payload
        } catch (_: Throwable) {
            TokenEntity.TransferPayload.empty(token.address)
        }
    }

    // endregion
}
