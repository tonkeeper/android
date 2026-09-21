package com.tonapps.migration.screens.confirm

import android.content.Context
import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.cellFromBase64
import com.tonapps.bus.generated.Events.Migration.MigrationChain
import com.tonapps.bus.generated.Events.Migration.MigrationFailedPart
import com.tonapps.core.extensions.fiatRate
import com.tonapps.core.extensions.formatFiat
import com.tonapps.core.extensions.formatWithSymbol
import com.tonapps.deposit.usecase.sign.SignUseCase
import com.tonapps.extensions.getUserMessage
import com.tonapps.icu.Coins
import com.tonapps.log.L
import com.tonapps.migration.analytics.MigrationAnalytics
import com.tonapps.migration.data.MigrationEmulationMapper
import com.tonapps.migration.data.MigrationEmulationMapper.Companion.asContinuousBundle
import com.tonapps.migration.data.MigrationFeeShortage
import com.tonapps.migration.data.MigrationPrepareResult
import com.tonapps.migration.data.MigrationRepository
import com.tonapps.migration.data.MigrationTonFee
import com.tonapps.migration.data.MigrationTronFee
import com.tonapps.migration.data.resolveFeeShortage
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.tx.TransactionManager
import com.tonapps.wallet.features.events.components.legacy.UiEvent
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.CancellationException

enum class MigrationFeeType(val value: String) {
    Native("native"),
    Token("token"),
    Battery("battery"),
}

enum class MigrationSigningState {
    Idle,
    Sending,
    Success,
    Failed,
}

enum class MigrationFeePickerKind {
    Ton,
    Tron,
}

enum class MigrationFeeOptionIcon {
    Ton,
    Trx,
    Battery,
}

data class MigrationFeeOptionUi(
    val id: String,
    val titleRes: Int? = null,
    val title: String? = null,
    val subtitle: CharSequence? = null,
    val batteryCharges: Int? = null,
    val icon: MigrationFeeOptionIcon,
    val enough: Boolean,
)

data class MigrationConfirmData(
    val prepareResult: MigrationPrepareResult,
    val emulationItems: List<UiEvent.Item>,
    val tonFee: MigrationFeeUi?,
    val canSwitchTonFee: Boolean,
    val tonFeeOptions: List<MigrationFeeOptionUi>,
    val selectedTonFeeId: String?,
    val tronFee: MigrationFeeUi?,
    val canSwitchTronFee: Boolean,
    val tronFeeOptions: List<MigrationFeeOptionUi>,
    val selectedTronFeeId: String?,
    val feeShortage: MigrationFeeShortage?,
    val skipTonTransactions: Boolean,
    val canConfirm: Boolean,
)

sealed interface MigrationFeeUi {
    data class Native(
        val fiatFormatted: CharSequence,
        val symbol: String,
    ) : MigrationFeeUi

    data class Battery(
        val charges: Int,
    ) : MigrationFeeUi
}

sealed interface MigrationConfirmUiState {
    data object Loading : MigrationConfirmUiState
    data object Error : MigrationConfirmUiState
    data class Content(val data: MigrationConfirmData) : MigrationConfirmUiState
}

private val MigrationConfirmUiState.dataOrNull: MigrationConfirmData?
    get() = (this as? MigrationConfirmUiState.Content)?.data

class MigrationConfirmFeature(
    private val walletId: String,
    private val migrationRepository: MigrationRepository,
    private val migrationEmulationMapper: MigrationEmulationMapper,
    private val transactionManager: TransactionManager,
    private val signUseCase: SignUseCase,
    private val settingsRepository: SettingsRepository,
    private val ratesRepository: RatesRepository,
    private val batteryRepository: BatteryRepository,
) : AsyncViewModel() {

    val currency: WalletCurrency = settingsRepository.currency
    val hiddenBalances: Boolean = settingsRepository.hiddenBalances

    private val preparedResult = MutableStateFlow<MigrationPrepareResult?>(null)
    private val loadError = MutableStateFlow(false)

    val uiState: StateFlow<MigrationConfirmUiState> =
        combine(preparedResult, loadError) { result, error -> result to error }
            .mapLatest { (result, error) ->
                when {
                    error -> MigrationConfirmUiState.Error
                    result == null -> MigrationConfirmUiState.Loading
                    else -> try {
                        MigrationConfirmUiState.Content(buildData(result))
                    } catch (throwable: Throwable) {
                        verifyError(throwable)
                        MigrationAnalytics.prepareError(
                            chain = MigrationChain.Multichain,
                            isBlocking = true,
                            error = throwable,
                        )
                        MigrationConfirmUiState.Error
                    }
                }
            }
            .flowOn(bgDispatcher)
            .cacheState(initialValue = MigrationConfirmUiState.Loading)

    private val feeShortageDismissed = MutableStateFlow(false)
    val showFeeShortageDialog: StateFlow<Boolean> =
        combine(uiState, feeShortageDismissed) { state, dismissed ->
            state.dataOrNull?.feeShortage != null && !dismissed
        }.cacheState(initialValue = false)

    val feePickerKind: StateFlow<MigrationFeePickerKind?> field = MutableStateFlow(null)

    val signingState: StateFlow<MigrationSigningState> field = MutableStateFlow(MigrationSigningState.Idle)
    val sentTransactions: StateFlow<Int> field = MutableStateFlow(0)
    val totalTransactions: StateFlow<Int> field = MutableStateFlow(0)
    val errorMessage: StateFlow<String?> field = MutableStateFlow(null)

    private var batteryChargesPollJob: Job? = null

    init {
        bgScope.launch { loadPrepared() }
    }

    fun onBatteryRechargeOpened() {
        val result = preparedResult.value ?: return
        batteryChargesPollJob?.cancel()
        batteryChargesPollJob = bgScope.launch {
            batteryRepository.waitForChargesChanged(
                wallet = result.wallet.wallet,
                fromCharges = result.tonFeeOptions.availableCharges,
            ) ?: return@launch
            reloadPrepared()
        }
    }

    private suspend fun reloadPrepared() {
        if (signingState.value != MigrationSigningState.Idle) {
            return
        }
        val result = preparedResult.value ?: return
        val updated = migrationRepository.prepare(result.wallet) ?: return
        preparedResult.tryEmit(updated)
        feeShortageDismissed.tryEmit(false)
    }

    fun confirm(context: Context) {
        batteryChargesPollJob?.cancel()
        bgScope.launch { sendMigration(context) }
    }

    fun retry(context: Context) {
        bgScope.launch { sendMigration(context) }
    }

    fun dismissFeeShortage() {
        feeShortageDismissed.tryEmit(true)
    }

    fun showFeeShortage() {
        if (uiState.value.dataOrNull?.feeShortage == null) {
            return
        }
        feeShortageDismissed.tryEmit(false)
    }

    fun openTonFeePicker() {
        val result = preparedResult.value ?: return
        if (!result.tonFeeOptions.canSwitchFee) {
            return
        }
        feePickerKind.tryEmit(MigrationFeePickerKind.Ton)
    }

    fun openTronFeePicker() {
        val result = preparedResult.value ?: return
        if (!result.tronPrepare.feeOptions.canSwitchFee) {
            return
        }
        feePickerKind.tryEmit(MigrationFeePickerKind.Tron)
    }

    fun dismissFeePicker() {
        feePickerKind.tryEmit(null)
    }

    fun selectTonFeeOption(optionId: String) {
        val result = preparedResult.value ?: return
        val fee = result.tonFeeOptions.methods.firstOrNull { it.optionId() == optionId } ?: return
        if (!result.canSelectTonFee(fee)) {
            return
        }
        bgScope.launch {
            preparedResult.tryEmit(result.withSelectedTonFee(fee))
            feeShortageDismissed.tryEmit(false)
            feePickerKind.tryEmit(null)
        }
    }

    fun selectTronFeeOption(optionId: String) {
        val result = preparedResult.value ?: return
        val fee = result.tronPrepare.feeOptions.methods.firstOrNull { it.optionId() == optionId }
            ?: return
        if (!result.canSelectTronFee(fee)) {
            return
        }
        bgScope.launch {
            preparedResult.tryEmit(result.withSelectedTronFee(fee))
            feeShortageDismissed.tryEmit(false)
            feePickerKind.tryEmit(null)
        }
    }

    private suspend fun MigrationPrepareResult.withSelectedTonFee(
        fee: MigrationTonFee,
    ): MigrationPrepareResult {
        var updated = withTonFee(fee)
        val tronFee = updated.tronPrepare.fee
        if (fee is MigrationTonFee.Battery &&
            tronFee is MigrationTronFee.Battery &&
            !updated.isTronFeePayable(tronFee)
        ) {
            val trx = updated.tronPrepare.feeOptions.trx
                ?.takeIf { updated.tronPrepare.feeOptions.trxEnough }
                ?: return this
            val trxFiatOf = trxFiatConverter()
            updated = updated.withTronFee(trx, trxFiatOf)
        }
        return updated
    }

    private suspend fun MigrationPrepareResult.withSelectedTronFee(
        fee: MigrationTronFee,
    ): MigrationPrepareResult {
        val trxFiatOf = trxFiatConverter()
        var updated = withTronFee(fee, trxFiatOf)
        val tonFee = updated.tonFee
        if (fee is MigrationTronFee.Battery &&
            tonFee is MigrationTonFee.Battery &&
            !updated.isTonFeePayable(tonFee)
        ) {
            val ton = updated.tonFeeOptions.ton
                ?.takeIf { updated.tonFeeOptions.tonEnough }
                ?: return this
            updated = updated.withTonFee(ton)
        }
        return updated
    }

    private suspend fun trxFiatConverter(): (Coins) -> Coins {
        val rates = ratesRepository.getRates(
            TonNetwork.MAINNET,
            currency,
            listOf(TokenEntity.TRX.address),
        )
        return { amount -> rates.convert(TokenEntity.TRX.address, amount) }
    }

    private suspend fun loadPrepared() {
        val result = migrationRepository.prepared(walletId)
        if (result == null) {
            loadError.tryEmit(true)
            return
        }
        preparedResult.tryEmit(result)
        MigrationAnalytics.confirmationView(MigrationAnalytics.feeAsset(result))
    }

    private suspend fun buildData(result: MigrationPrepareResult): MigrationConfirmData {
        val tonItems = runCatching {
            migrationEmulationMapper.mapTransactions(
                sourceWallet = result.wallet.wallet,
                transactions = result.transactions,
                currency = currency,
            )
        }.onFailure { error ->
            L.e("MigrationConfirmFeature", "Failed to map TON migration emulation", error)
        }.getOrDefault(emptyList())
        val tronItems = runCatching {
            migrationEmulationMapper.mapTronPrepare(
                sourceWallet = result.wallet.wallet,
                tronPrepare = result.tronPrepare,
                currency = currency,
            )
        }.onFailure { error ->
            L.e("MigrationConfirmFeature", "Failed to map TRON migration emulation", error)
        }.getOrDefault(emptyList())
        val shortage = result.resolveFeeShortage()
        val canSendTon = when (shortage) {
            null -> result.transactions.isNotEmpty()
            is MigrationFeeShortage.Ton -> false
            is MigrationFeeShortage.Trx -> shortage.canContinueWithTon
            is MigrationFeeShortage.Battery -> shortage.canContinueWithTon
            is MigrationFeeShortage.Both -> false
        }
        val canSendTron = when (shortage) {
            null -> result.tronPrepare.hasTransfers
            is MigrationFeeShortage.Ton -> shortage.canContinueWithTron
            is MigrationFeeShortage.Trx -> false
            is MigrationFeeShortage.Battery -> shortage.canContinueWithTron
            is MigrationFeeShortage.Both -> false
        }
        val previewItems = when {
            canSendTon && !canSendTron -> tonItems
            !canSendTon && canSendTron -> tronItems
            else -> tonItems + tronItems
        }

        return MigrationConfirmData(
            prepareResult = result,
            emulationItems = previewItems.asContinuousBundle(),
            tonFee = formatTonFee(result, currency).takeIf { canSendTon || !canSendTron },
            canSwitchTonFee = result.tonFeeOptions.canSwitchFee,
            tonFeeOptions = buildTonFeeOptions(result),
            selectedTonFeeId = result.tonFee.optionId().takeIf { result.tonFee !is MigrationTonFee.None },
            tronFee = formatTronFee(result, currency).takeIf { canSendTron || !canSendTon },
            canSwitchTronFee = result.tronPrepare.feeOptions.canSwitchFee,
            tronFeeOptions = buildTronFeeOptions(result),
            selectedTronFeeId = result.tronPrepare.fee.optionId()
                .takeIf { result.tronPrepare.fee !is MigrationTronFee.None },
            feeShortage = shortage,
            skipTonTransactions = !canSendTon,
            canConfirm = canSendTon || canSendTron,
        )
    }

    private suspend fun buildTonFeeOptions(
        result: MigrationPrepareResult,
    ): List<MigrationFeeOptionUi> {
        return result.tonFeeOptions.methods.map { fee ->
            when (fee) {
                is MigrationTonFee.Ton -> MigrationFeeOptionUi(
                    id = fee.optionId(),
                    title = TokenEntity.TON.symbol,
                    subtitle = formatQuotedTonAmount(fee.tonAmount, result.wallet.wallet.network),
                    icon = MigrationFeeOptionIcon.Ton,
                    enough = result.canSelectTonFee(fee),
                )
                is MigrationTonFee.Battery -> MigrationFeeOptionUi(
                    id = fee.optionId(),
                    titleRes = Localization.battery_refill_title,
                    batteryCharges = fee.charges,
                    icon = MigrationFeeOptionIcon.Battery,
                    enough = result.canSelectTonFee(fee),
                )
                MigrationTonFee.None -> error("unreachable")
            }
        }
    }

    private fun buildTronFeeOptions(
        result: MigrationPrepareResult,
    ): List<MigrationFeeOptionUi> {
        return result.tronPrepare.feeOptions.methods.map { fee ->
            when (fee) {
                is MigrationTronFee.Trx -> MigrationFeeOptionUi(
                    id = fee.optionId(),
                    title = TokenEntity.TRX.symbol,
                    subtitle = formatQuotedAmount(
                        fiat = fee.fiatAmount,
                        amount = fee.amount,
                        symbol = TokenEntity.TRX.symbol,
                    ),
                    icon = MigrationFeeOptionIcon.Trx,
                    enough = result.canSelectTronFee(fee),
                )
                is MigrationTronFee.Battery -> MigrationFeeOptionUi(
                    id = fee.optionId(),
                    titleRes = Localization.battery_refill_title,
                    batteryCharges = fee.charges,
                    icon = MigrationFeeOptionIcon.Battery,
                    enough = result.canSelectTronFee(fee),
                )
                is MigrationTronFee.Ton -> MigrationFeeOptionUi(
                    id = fee.optionId(),
                    title = TokenEntity.TON.symbol,
                    subtitle = formatQuotedAmount(
                        fiat = fee.fiatAmount,
                        amount = fee.amount,
                        symbol = TokenEntity.TON.symbol,
                    ),
                    icon = MigrationFeeOptionIcon.Ton,
                    enough = result.canSelectTronFee(fee),
                )
                MigrationTronFee.None -> error("unreachable")
            }
        }
    }

    private suspend fun sendMigration(context: Context) {
        val current = uiState.value.dataOrNull ?: return
        if (!current.canConfirm) {
            return
        }
        val result = current.prepareResult

        Async.globalScope().launch {
            migrationRepository.completeRaffleMigration(result.destinationWallet.id)
        }

        signingState.tryEmit(MigrationSigningState.Sending)
        sentTransactions.tryEmit(0)
        errorMessage.tryEmit(null)

        val wallet = result.wallet.wallet
        val transactions = if (current.skipTonTransactions) {
            emptyList()
        } else {
            result.transactions
        }
        val canSendTron = when (val shortage = current.feeShortage) {
            null -> result.tronPrepare.hasTransfers
            is MigrationFeeShortage.Ton -> shortage.canContinueWithTron
            is MigrationFeeShortage.Battery -> shortage.canContinueWithTron
            is MigrationFeeShortage.Trx,
            is MigrationFeeShortage.Both,
            -> false
        }
        val tronSteps = if (canSendTron) {
            result.tronPrepare.stepCount
        } else {
            0
        }
        val totalSteps = transactions.size + tronSteps
        val feeAsset = MigrationAnalytics.feeAsset(result)
        var failedPart = MigrationFailedPart.Setup
        val sender = transactionManager.createCustomSender()
        val tonHeaders = migrationHeaders(
            if (result.paysTonFeeWithBattery) {
                MigrationFeeType.Battery
            } else {
                MigrationFeeType.Native
            }
        )

        try {
            totalTransactions.tryEmit(totalSteps)

            signUseCase.unlockAndSign(context = context, wallet = wallet) {
                if (transactions.isNotEmpty()) {
                    failedPart = MigrationFailedPart.Ton
                }
                if (result.paysTonFeeWithBattery &&
                    transactions.none { it.sponsored == true }
                ) {
                    error("Battery migration requires sponsored transactions")
                }
                var sentTonWithBattery = false
                transactions.forEachIndexed { index, transaction ->
                    val boc = signTransfer(
                        unsignedBody = transaction.boc.cellFromBase64(),
                        seqNo = transaction.seqno,
                    )
                    val withBattery = result.paysTonFeeWithBattery &&
                        transaction.sponsored == true
                    sender.send(
                        wallet = wallet,
                        boc = boc,
                        withBattery = withBattery,
                        source = SOURCE,
                        confirmationTime = 0.0,
                        headers = tonHeaders,
                    )
                    if (withBattery) {
                        sentTonWithBattery = true
                    }
                    val isLast = index == transactions.lastIndex
                    if (!isLast) {
                        transactionManager.waitUntilSeqno(
                            wallet = wallet,
                            minimum = transaction.seqno + 1,
                        )
                        if (withBattery) {
                            transactionManager.waitUntilBatteryIdle(wallet)
                        }
                    }
                    sentTransactions.tryEmit(sentTransactions.value + 1)
                }

                if (canSendTron) {
                    if (sentTonWithBattery &&
                        result.tronPrepare.fee is MigrationTronFee.Battery
                    ) {
                        transactionManager.waitUntilBatteryIdle(wallet)
                    }
                    failedPart = if (result.tronPrepare.willSendUsdt) {
                        MigrationFailedPart.TronUsdt
                    } else {
                        MigrationFailedPart.TronTrx
                    }
                    migrationRepository.sendTronMigration(
                        wallet = wallet,
                        prepare = result.tronPrepare,
                        sign = { transaction -> signTron(transaction) },
                        onStepCompleted = {
                            val sent = sentTransactions.value + 1
                            sentTransactions.tryEmit(sent)
                            totalTransactions.tryEmit(maxOf(totalTransactions.value, sent))
                            failedPart = MigrationFailedPart.TronTrx
                        },
                    )
                }
            }

            migrationRepository.invalidateWalletsCount()
            signingState.tryEmit(MigrationSigningState.Success)
            MigrationAnalytics.transactionSuccess(feeAsset)
        } catch (error: CancellationException) {
            signingState.tryEmit(MigrationSigningState.Idle)
        } catch (error: Throwable) {
            L.e("MigrationConfirmFeature", "Migration send failed", error)
            signingState.tryEmit(MigrationSigningState.Failed)
            errorMessage.tryEmit(error.getUserMessage(context) ?: error.message)
            MigrationAnalytics.transactionError(
                feeAsset = feeAsset,
                failedPart = failedPart,
                isPartial = sentTransactions.value > 0,
                error = error,
            )
        }
    }

    private suspend fun formatQuotedTonAmount(
        amount: Coins,
        network: TonNetwork,
    ): CharSequence {
        val fiat = formatTonFiat(amount, network, currency)
        return "≈ $fiat · ${amount.formatWithSymbol(TokenEntity.TON.symbol)}"
    }

    private fun formatQuotedAmount(
        fiat: Coins,
        amount: Coins,
        symbol: String,
    ): CharSequence {
        return "≈ ${fiat.formatFiat(currency.code)} · ${amount.formatWithSymbol(symbol)}"
    }

    private suspend fun formatTonFee(
        prepareResult: MigrationPrepareResult,
        currency: WalletCurrency,
    ): MigrationFeeUi? {
        if (prepareResult.transactions.isEmpty() && prepareResult.tonFee !is MigrationTonFee.Battery) {
            return null
        }
        return when (val fee = prepareResult.tonFee) {
            MigrationTonFee.None -> null
            is MigrationTonFee.Battery -> MigrationFeeUi.Battery(charges = fee.charges)
            is MigrationTonFee.Ton -> MigrationFeeUi.Native(
                fiatFormatted = formatTonFiat(
                    amount = fee.tonAmount,
                    network = prepareResult.wallet.wallet.network,
                    currency = currency,
                ),
                symbol = TokenEntity.TON.symbol,
            )
        }
    }

    private suspend fun formatTonFiat(
        amount: Coins,
        network: TonNetwork,
        currency: WalletCurrency,
    ): CharSequence {
        val rates = ratesRepository.getTONRates(network, currency)
        val rate = rates.rateValue(TokenEntity.TON.address)
        if (!rate.isPositive) {
            return amount.formatFiat(currency.code)
        }
        return amount.formatFiat(fiatRate(currency.code, rate))
    }

    private fun migrationHeaders(feeType: MigrationFeeType): Map<String, String> {
        return mapOf(
            HEADER_MIGRATION_ID to UUID.randomUUID().toString(),
            HEADER_MIGRATION_FEE to feeType.value,
        )
    }

    private fun formatTronFee(
        prepareResult: MigrationPrepareResult,
        currency: WalletCurrency,
    ): MigrationFeeUi? {
        if (!prepareResult.tronPrepare.hasUsdtFee) {
            return null
        }
        return when (val fee = prepareResult.tronPrepare.fee) {
            MigrationTronFee.None -> null
            is MigrationTronFee.Battery -> MigrationFeeUi.Battery(charges = fee.charges)
            is MigrationTronFee.Ton -> MigrationFeeUi.Native(
                fiatFormatted = fee.fiatAmount.formatFiat(currency.code),
                symbol = TokenEntity.TON.symbol,
            )
            is MigrationTronFee.Trx -> MigrationFeeUi.Native(
                fiatFormatted = fee.fiatAmount.formatFiat(currency.code),
                symbol = TokenEntity.TRX.symbol,
            )
        }
    }

    private companion object {
        const val SOURCE = "migration"
        const val HEADER_MIGRATION_ID = "X-Migration-ID"
        const val HEADER_MIGRATION_FEE = "X-Migration-Fee"
    }
}

private fun MigrationTonFee.optionId(): String = when (this) {
    is MigrationTonFee.Ton -> "ton"
    is MigrationTonFee.Battery -> "battery"
    MigrationTonFee.None -> "none"
}

private fun MigrationTronFee.optionId(): String = when (this) {
    is MigrationTronFee.Trx -> "trx"
    is MigrationTronFee.Battery -> "battery"
    is MigrationTronFee.Ton -> "ton"
    MigrationTronFee.None -> "none"
}
