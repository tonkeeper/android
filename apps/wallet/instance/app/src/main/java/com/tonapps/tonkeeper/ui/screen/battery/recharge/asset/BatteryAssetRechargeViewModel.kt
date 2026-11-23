package com.tonapps.tonkeeper.ui.screen.battery.recharge.asset

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.async.Async
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeSize
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeType
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.log.L
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.battery.recharge.list.Item
import com.tonapps.tonkeeper.ui.screen.battery.refill.entity.PromoState
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.battery.BatteryPurchaseEstimate
import com.tonapps.wallet.data.battery.BatteryRechargeMath
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.battery.entity.BatteryConfigEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodEntity
import com.tonapps.wallet.data.battery.purchaseEstimates
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.settings.BatteryTransaction
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger as IonBigInteger
import java.math.BigInteger
import java.math.RoundingMode

/** What the asset recharge screen tells its screen to do. */
sealed interface BatteryAssetRechargeEvent {
    data class Confirm(val request: ConfirmRequest) : BatteryAssetRechargeEvent
    data class MaxAmountError(val maxAmount: Coins, val currency: String) : BatteryAssetRechargeEvent
    data object Error : BatteryAssetRechargeEvent
}

/**
 * Recharges a multichain wallet's battery with an asset on a chain other than TON: a pack or an amount of
 * [BatteryAssetRechargeArgs.assetId], told in charges from the method's rate at once and from
 * `/purchase-battery/estimate` once the amount stands. The purchase itself is quoted and sent on the
 * confirm screen.
 */
class BatteryAssetRechargeViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val args: BatteryAssetRechargeArgs,
    private val batteryRepository: BatteryRepository,
    private val mcAccountRepository: McAccountRepository,
    private val settingsRepository: SettingsRepository,
    private val api: API,
    private val analytics: AnalyticsHelper,
) : BaseWalletVM(app) {

    /** What the screen is built of, read once it opens. */
    class Source(
        val method: RechargeMethodEntity,
        val account: AccountWithDetails,
        val config: BatteryConfigEntity,
    ) {
        val symbol: String
            get() = method.symbol

        val decimals: Int
            get() = method.decimals

        /** The charges one whole unit of the asset buys. */
        val chargesRate: BigDecimal? = method.chargesPerUnit(config.chargeCost)

        /** The balance in whole units of the asset. */
        val balance: BigDecimal
            get() = BigDecimal(account.displayBalance.value.toStringExpanded())
    }

    private val sourceFlow = MutableStateFlow<Source?>(null)

    // The Continue in flight: another tap before it emits would open a second confirm sheet.
    private var continueJob: Job? = null
    val accountFlow = sourceFlow.filterNotNull().map { it.account }

    private val promoStateFlow = MutableStateFlow<PromoState>(PromoState.Default)
    private val selectedPackFlow = MutableStateFlow<String?>(null)
    private val customAmountFlow = MutableStateFlow(false)
    private val amountFlow = MutableStateFlow(0.0)

    private val _eventFlow = MutableEffectFlow<BatteryAssetRechargeEvent>()
    val eventFlow = _eventFlow.asSharedFlow().filterNotNull()

    // The charges before the purchase, for the balance refresh after it.
    private var chargesBeforePurchase: Int? = null

    private val estimateFlow = combine(sourceFlow, customAmountFlow, amountFlow, promoStateFlow) { source, custom, amount, promo ->
        source?.takeIf { custom }?.let {
            EstimateInput(it, BatteryRechargeMath.toBaseUnits(amount.toBigDecimal(), it.decimals), appliedPromo(promo))
        }
    }
        .purchaseEstimates(ESTIMATE_DEBOUNCE_MS, EstimateInput::amount) { input ->
            batteryRepository.estimatePurchase(
                walletId = wallet.id,
                chainId = input.source.method.chain,
                assetId = input.source.method.assetId,
                senderAddress = input.source.account.address.display,
                amount = input.amount,
                promo = input.promo,
            )
        }
        .flowOn(Async.Io)
        .stateIn(viewModelScope, SharingStarted.Eagerly, BatteryPurchaseEstimate.None)

    private data class EstimateInput(
        val source: Source,
        val amount: BigInteger,
        val promo: String?,
    )

    val uiItemsFlow = combine(
        sourceFlow.filterNotNull(),
        combine(selectedPackFlow, customAmountFlow, ::Pair),
        amountFlow,
        promoStateFlow,
        estimateFlow,
    ) { source, (selectedPack, customAmount), amount, promoState, estimate ->
        buildItems(source, selectedPack, customAmount, amount, promoState, estimate)
    }
        .onStart { emit(listOf(Item.Loading)) }
        .flowOn(Async.Io)

    init {
        viewModelScope.launch(Async.Io) {
            val appliedPromo = batteryRepository.getAppliedPromo(wallet.network)
            promoStateFlow.value = if (appliedPromo.isNullOrBlank()) {
                PromoState.Default
            } else {
                PromoState.Applied(appliedPromo)
            }

            val source = loadSource()
            if (source == null) {
                _eventFlow.tryEmit(BatteryAssetRechargeEvent.Error)
                return@launch
            }
            sourceFlow.value = source
        }
    }

    private suspend fun loadSource(): Source? {
        return try {
            val methods = batteryRepository.getRechargeMethods(wallet.network, multichain = true)
            val method = BatteryRechargeAssets.method(methods, args.assetId) ?: return null
            val account = mcAccountRepository.findAccount(wallet.id, args.assetId, currencyOverride = settingsRepository.currency.code)
                ?: return null
            Source(method, account, batteryRepository.getConfig(wallet.network))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e)
            null
        }
    }

    private fun buildItems(
        source: Source,
        selectedPack: String?,
        customAmount: Boolean,
        amount: Double,
        promoState: PromoState,
        estimate: BatteryPurchaseEstimate,
    ): List<Item> {
        val uiItems = mutableListOf<Item>()
        val packs = packs(source)
        val maxCharges = packs.maxOfOrNull { it.charges } ?: 0

        for ((index, pack) in packs.withIndex()) {
            uiItems.add(
                Item.RechargePack(
                    position = ListCell.getPosition(packs.size + 1, index),
                    packType = pack.type,
                    charges = pack.charges,
                    formattedAmount = CurrencyFormatter.formatFiat(currency = source.symbol, value = pack.amount),
                    formattedFiatAmount = formatFiat(source, pack.amount),
                    batteryLevel = if (maxCharges > 0) {
                        pack.charges.toFloat() / maxCharges
                    } else {
                        0f
                    },
                    isEnabled = pack.isEnabled,
                    selected = pack.type == selectedPack,
                    transactions = transactions(source.config, pack.charges),
                )
            )
        }
        uiItems.add(
            Item.CustomAmount(
                position = if (packs.isNotEmpty()) {
                    ListCell.Position.LAST
                } else {
                    ListCell.Position.SINGLE
                },
                selected = customAmount,
            )
        )

        if (!api.getConfig(wallet.network).batteryPromoDisable) {
            uiItems.add(Item.Space)
            uiItems.add(Item.Promo(promoState))
        }

        val typed = amount.toBigDecimal()
        val remaining = source.balance - typed
        val isLessThanMin = typed.signum() > 0 && !BatteryRechargeMath.isEnough(typed, source.method.minBootstrapValue)
        val localCharges = BatteryRechargeMath.charges(typed, source.chargesRate)

        if (customAmount) {
            val minAmount = source.method.minBootstrapValue?.toBigDecimalOrNull() ?: BigDecimal.ZERO
            uiItems.add(Item.Space)
            uiItems.add(
                Item.Amount(
                    symbol = source.symbol,
                    decimals = source.decimals,
                    formattedRemaining = CurrencyFormatter.format(currency = source.symbol, value = remaining.max(BigDecimal.ZERO)),
                    // The separators of the "Remaining" this hint takes the place of.
                    formattedMinAmount = formatMinAmount(
                        symbol = CurrencyFormatter.symbol(source.symbol),
                        min = minAmount,
                        decimals = source.decimals,
                        options = Formatter.defaultOptions.copy(
                            decimalSeparator = CurrencyFormatter.decimalSeparator,
                            groupingSeparator = CurrencyFormatter.groupingSeparator,
                        ),
                    ),
                    isInsufficientBalance = remaining.signum() < 0,
                    isLessThanMin = isLessThanMin,
                    formattedCharges = chargesText(estimate, BatteryRechargeMath.toBaseUnits(typed, source.decimals), localCharges),
                    value = amount,
                )
            )
        }

        // The battery's own answer outranks the rate's: a network fee that eats the whole purchase adds nothing.
        val units = BatteryRechargeMath.toBaseUnits(typed, source.decimals)
        val addsNothing = estimate is BatteryPurchaseEstimate.Ready && estimate.amount == units && estimate.quote.charges <= 0
        val isValid = if (customAmount) {
            localCharges > 0 && !isLessThanMin && remaining.signum() >= 0 && !addsNothing
        } else {
            packs.any { it.type == selectedPack && it.isEnabled }
        }

        uiItems.add(Item.Space)
        uiItems.add(Item.Button(isValid))
        return uiItems.toList()
    }

    // The exact charges once the battery answered for this very amount; until then the rate's estimate.
    private fun chargesText(estimate: BatteryPurchaseEstimate, units: BigInteger, localCharges: Long): String {
        if (estimate is BatteryPurchaseEstimate.Ready && estimate.amount == units) {
            return estimate.quote.charges.toString()
        }
        return context.getString(Localization.battery_purchase_approximately, localCharges.toString())
    }

    private fun packs(source: Source): List<BatteryRechargePack> {
        return batteryRechargePacks(
            packages = source.config.packages,
            chargesRate = source.chargesRate,
            decimals = source.decimals,
            minBootstrapValue = source.method.minBootstrapValue,
            balance = source.balance,
        )
    }

    private fun formatFiat(source: Source, amount: BigDecimal): CharSequence {
        val price = source.account.rate?.price?.toBigDecimalOrNull() ?: return ""
        return CurrencyFormatter.formatFiat(currency = settingsRepository.currency.code, value = amount.multiply(price))
    }

    private fun transactions(config: BatteryConfigEntity, charges: Int): Map<BatteryTransaction, Int> {
        val prices = config.meanPrices
        return mapOf(
            BatteryTransaction.SWAP to charges / prices.batteryMeanPriceSwap.coerceAtLeast(1),
            BatteryTransaction.NFT to charges / prices.batteryMeanPriceNft.coerceAtLeast(1),
            BatteryTransaction.JETTON to charges / prices.batteryMeanPriceJetton.coerceAtLeast(1),
        )
    }

    fun updateAmount(amount: Double) {
        amountFlow.value = amount
    }

    fun setSelectedPack(packType: String) {
        selectedPackFlow.value = packType
        customAmountFlow.value = false
        trackSelect()
    }

    fun onCustomAmountSelect() {
        customAmountFlow.value = true
        selectedPackFlow.value = null
        trackSelect()
    }

    fun applyPromo(promo: String) {
        viewModelScope.launch(Async.Io) {
            if (promo.isEmpty()) {
                batteryRepository.setAppliedPromo(wallet.network, null)
                promoStateFlow.value = PromoState.Default
                return@launch
            }

            promoStateFlow.value = PromoState.Loading
            val valid = try {
                api.batteryVerifyPurchasePromo(wallet.network, promo)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            batteryRepository.setAppliedPromo(wallet.network, promo.takeIf { valid })
            promoStateFlow.value = if (valid) {
                PromoState.Applied(promo)
            } else {
                PromoState.Error
            }
        }
    }

    /** Opens the confirm screen for the pack or the amount chosen. */
    fun onContinue() {
        if (continueJob?.isActive == true) {
            return
        }

        continueJob = viewModelScope.launch(Async.Io) {
            val source = sourceFlow.value ?: return@launch
            val amount = selectedAmount(source) ?: return@launch

            // The battery takes no more than this much TON's worth in one purchase.
            val maxInputTon = api.getConfig(wallet.network).batteryMaxInputAmount.toBigDecimalOrNull()
            val rate = source.method.rate.toBigDecimalOrNull()
            if (maxInputTon != null && rate != null && rate.signum() > 0 && amount.multiply(rate) > maxInputTon) {
                val maxAmount = maxInputTon.divide(rate, source.decimals, RoundingMode.DOWN)
                _eventFlow.tryEmit(BatteryAssetRechargeEvent.MaxAmountError(Coins.of(maxAmount, source.decimals), source.symbol))
                return@launch
            }

            chargesBeforePurchase = batteryRepository.getChargesBeforeRecharge(wallet)
            _eventFlow.tryEmit(BatteryAssetRechargeEvent.Confirm(confirmRequest(source, amount)))
        }
    }

    private fun selectedAmount(source: Source): BigDecimal? {
        val packType = selectedPackFlow.value
        if (packType != null) {
            return packs(source).firstOrNull { it.type == packType && it.isEnabled }?.amount
        }
        return amountFlow.value.toBigDecimal().takeIf { it.signum() > 0 }
    }

    private fun confirmRequest(source: Source, amount: BigDecimal): ConfirmRequest {
        val units = BatteryRechargeMath.toBaseUnits(amount, source.decimals)
        return ConfirmRequest(
            data = CommonTransactionData(
                assetId = source.account.asset.id,
                walletId = wallet.id,
            ),
            type = ConfirmType.BatteryPurchase(
                chainId = source.method.chain,
                batteryAssetId = source.method.assetId,
                senderAddress = source.account.address.display,
                amount = IonBigInteger.parseString(units.toString()),
                symbol = source.symbol,
                decimals = source.decimals,
                estimatedCharges = BatteryRechargeMath.charges(amount, source.chargesRate),
                promo = appliedPromo(promoStateFlow.value),
            ),
            utm = args.utm,
        )
    }

    /** The confirm screen sent the purchase: the balance grows once the transfer lands. */
    fun onPurchaseSent() {
        analytics.eventsTaggedWith(args.utm).batteryNative.batterySuccess(
            from = args.from,
            type = BatteryNativeType.Crypto,
            size = batterySize(),
            promo = appliedPromo(promoStateFlow.value),
            jetton = sourceFlow.value?.symbol,
        )

        val before = chargesBeforePurchase ?: return
        Async.globalScope().launch {
            batteryRepository.refreshBalanceUntilRecharged(wallet, before)
        }
    }

    private fun trackSelect() {
        analytics.eventsTaggedWith(args.utm).batteryNative.batterySelect(
            from = args.from,
            type = BatteryNativeType.Crypto,
            size = batterySize(),
            promo = appliedPromo(promoStateFlow.value),
            jetton = sourceFlow.value?.symbol,
        )
    }

    private fun batterySize(): BatteryNativeSize {
        if (customAmountFlow.value) {
            return BatteryNativeSize.Custom
        }
        val packType = selectedPackFlow.value ?: return BatteryNativeSize.Custom
        return BatteryNativeSize.entries.firstOrNull { it.key == packType } ?: BatteryNativeSize.Custom
    }

    private fun appliedPromo(state: PromoState): String? = (state as? PromoState.Applied)?.appliedPromo

    private companion object {
        const val ESTIMATE_DEBOUNCE_MS = 500L
    }
}
