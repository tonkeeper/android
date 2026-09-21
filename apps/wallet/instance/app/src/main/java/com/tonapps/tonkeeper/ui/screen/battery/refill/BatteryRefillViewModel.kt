package com.tonapps.tonkeeper.ui.screen.battery.refill

import android.app.Activity
import android.app.Application
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.billing.BillingManager
import com.tonapps.tonkeeper.billing.priceFormatted
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeSize
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeType
import com.tonapps.log.L
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.battery.refill.entity.PromoState
import com.tonapps.tonkeeper.ui.screen.battery.refill.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.AuthorizationProvider
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.api.entity.IAPPackageId
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.wallet.data.battery.BatteryMapper
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.battery.entity.BatteryBalanceEntity
import com.tonapps.wallet.data.battery.entity.BatteryConfigEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodType
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.BatteryTransaction
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.localization.Localization
import io.batteryapi.models.AndroidBatteryPurchaseRequest
import io.batteryapi.models.AndroidBatteryPurchaseRequestPurchasesInner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.net.URLEncoder
import kotlin.math.absoluteValue

private enum class IapAvailability {
    UNKNOWN,
    ALLOWED,
    DISABLED_BY_REFUNDS,
}

class BatteryRefillViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val from: BatteryNativeFrom,
    private val api: API,
    private val accountRepository: AccountRepository,
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val authorizationProvider: AuthorizationProvider,
    private val batteryRepository: BatteryRepository,
    private val tokenRepository: TokenRepository,
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
    private val billingManager: BillingManager,
    private val environment: Environment,
    private val analytics: AnalyticsHelper,
) : BaseWalletVM(app) {

    private val config: ConfigEntity
        get() = api.getConfig(wallet.network)

    private val isBatteryDisabled: Boolean
        get() = config.flags.disableBattery

    private val isCryptoDisabled: Boolean
        get() = config.disableBatteryCryptoRechargeModule

    private val _promoFlow = MutableStateFlow<String?>(null)
    private val promoFlow = _promoFlow.asStateFlow()

    private val purchasesFlow = billingManager.purchasesUpdatedFlow
        .map { it.purchases }
        .filter { it.isNotEmpty() }

    private val _promoStateFlow = MutableStateFlow<PromoState>(PromoState.Default)
    private val promoStateFlow = _promoStateFlow.asStateFlow()

    private val purchaseInProgress = MutableStateFlow(false)

    private val iapAvailabilityFlow = MutableStateFlow(IapAvailability.UNKNOWN)

    private val iapStateFlow = combine(purchaseInProgress, iapAvailabilityFlow) { isProcessing, availability ->
        isProcessing to availability
    }

    private val settingsUpdateFlow = combine(
        settingsRepository.walletPrefsChangedFlow,
        batteryRepository.balanceUpdatedFlow,
    ) { _, _ -> }

    val uiItemsFlow = combine(
        promoStateFlow,
        billingManager.productsFlow,
        iapStateFlow,
        settingsUpdateFlow,
        promoFlow,
    ) { promoState, iapProducts, (isProcessing, iapAvailability), _, promoCode ->
        val tonWallet = resolveWallet()
        val isMultichain = tonWallet.type == WalletType.Multichain
        val batteryBalance = getBatteryBalance(tonWallet)
        val batteryConfig = getBatteryConfig(tonWallet)

        val uiItems = mutableListOf<Item>()
        uiItems.add(uiItemBattery(batteryBalance, batteryConfig))
        uiItems.add(Item.Space)

        if (!config.batteryPromoDisable && !isBatteryDisabled) {
            uiItems.add(Item.Promo(promoState, promoCode))
            uiItems.add(Item.Space)
        }

        if (!batteryBalance.balance.isZero) {
            uiItems.add(Item.Settings(settingsRepository.getBatteryTxEnabled(tonWallet.accountId)))
            uiItems.add(Item.Space)
        }

        if (environment.isGooglePlayServicesAvailable && !config.disableBatteryIapModule && !isBatteryDisabled && iapAvailability != IapAvailability.DISABLED_BY_REFUNDS) {
            val tonPriceInUsd =
                ratesRepository.getTONRates(tonWallet.network, WalletCurrency.USD).getRate(TokenEntity.TON.address)

            if (tonPriceInUsd > Coins.ZERO) {
                uiItemsPackages(
                    tonPriceInUsd = tonPriceInUsd,
                    batteryBalance = batteryBalance,
                    config = config,
                    products = iapProducts,
                    isEnabled = !isProcessing && iapAvailability == IapAvailability.ALLOWED,
                    batteryConfig = batteryConfig,
                ).let {
                    if (it.isNotEmpty()) {
                        uiItems.addAll(it)
                        uiItems.add(Item.Space)
                    }
                }
            }
        }

        val rechargeMethodsItems = uiItemsRechargeMethods(tonWallet)

        if (!isBatteryDisabled && !isCryptoDisabled && rechargeMethodsItems.isNotEmpty()) {
            uiItems.addAll(rechargeMethodsItems)
            uiItems.add(Item.Space)
        }

        val tonProofToken = accountRepository.requestTonProofToken(tonWallet) ?: ""

        uiItems.add(
            Item.Refund(
                wallet = tonWallet, refundUrl = "${config.batteryRefundEndpoint}?token=${URLEncoder.encode(tonProofToken, "UTF-8")}&testnet=${tonWallet.testnet}"
            )
        )

        uiItems.add(Item.Space)

        uiItems.add(Item.RestoreIAP(chargeEnabled = !isBatteryDisabled))

        uiItems.toList()
    }.flowOn(Dispatchers.IO)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (environment.isGooglePlayServicesAvailable) {
                billingManager.loadProducts(config.iapPackages.map { it.productId })
                loadIapAvailability()
            } else {
                billingManager.setEmptyProducts()
            }

            val appliedPromo = batteryRepository.getAppliedPromo(wallet.network)

            if (appliedPromo.isNullOrBlank()) {
                _promoStateFlow.value = PromoState.Default
            } else {
                _promoStateFlow.value = PromoState.Applied(appliedPromo)
            }
        }

        purchasesFlow.collectFlow(::handlePurchases)
    }

    private suspend fun loadIapAvailability() {
        iapAvailabilityFlow.value = try {
            if (batteryRepository.isIapDisabledByRefunds(resolveWallet())) {
                IapAvailability.DISABLED_BY_REFUNDS
            } else {
                IapAvailability.ALLOWED
            }
        } catch (e: Exception) {
            L.e(e)
            IapAvailability.ALLOWED
        }
    }

    private fun uiItemsPackages(
        tonPriceInUsd: Coins,
        batteryBalance: BatteryBalanceEntity,
        config: ConfigEntity,
        products: List<ProductDetails>,
        isEnabled: Boolean,
        batteryConfig: BatteryConfigEntity,
    ): List<Item.IAPPack> {
        val isBatteryEmpty = batteryBalance.reservedBalance.isZero && batteryBalance.balance.isZero
        val reservedAmount = if (isBatteryEmpty) {
            batteryConfig.reservedAmount.toBigDecimal()
        } else {
            BigDecimal.ZERO
        }

        val uiItems = mutableListOf<Item.IAPPack>()

        config.iapPackages.forEachIndexed { index, iapPackage ->
            val position = ListCell.getPosition(config.iapPackages.size, index)
            val product = products.find { it.productId == iapPackage.productId }
            val charges = batteryConfig.packages.firstOrNull {
                it.name.equals(iapPackage.id.id, ignoreCase = true)
            }?.charges ?: BatteryMapper.calculateIapCharges(
                userProceed = iapPackage.userProceed,
                tonPriceInUsd = tonPriceInUsd,
                reservedAmount = reservedAmount,
                meanFees = batteryConfig.chargeCost.toBigDecimal()
            )

            if (charges == 0) {
                return@forEachIndexed
            }

            val transactions = mapOf(
                BatteryTransaction.SWAP to charges / batteryConfig.meanPrices.batteryMeanPriceSwap,
                BatteryTransaction.JETTON to charges / batteryConfig.meanPrices.batteryMeanPriceJetton,
                BatteryTransaction.NFT to charges / batteryConfig.meanPrices.batteryMeanPriceNft,
            )

            val formattedPrice = product?.priceFormatted ?: context.getString(Localization.loading)
            uiItems.add(
                Item.IAPPack(
                    position = position,
                    packType = iapPackage.id,
                    productId = iapPackage.productId,
                    isEnabled = product != null && isEnabled,
                    charges = charges,
                    formattedPrice = formattedPrice,
                    transactions = transactions,
                )
            )
        }

        return uiItems.toList()
    }

    private fun uiItemBattery(
        balance: BatteryBalanceEntity,
        batteryConfig: BatteryConfigEntity,
    ): Item.Battery {
        val charges = BatteryMapper.convertToSignedCharges(balance.balance, batteryConfig.chargeCost)
        val formattedAbsCharges = CurrencyFormatter.format(value = charges.absoluteValue.toBigDecimal())
        val formattedChanges = if (charges < 0) {
            "-$formattedAbsCharges"
        } else {
            formattedAbsCharges
        }

        return Item.Battery(
            balance = balance.balance.value.toFloat(),
            beta = false, // config.batteryBeta,
            changes = charges,
            formattedChanges = formattedChanges,
            isNegative = balance.balance.isNegative,
        )
    }

    private suspend fun uiItemsRechargeMethods(
        wallet: WalletEntity,
    ): List<Item> {
        val batteryConfig = getBatteryConfig(wallet)
        val supportedTokens = getSupportedTokens(wallet, batteryConfig.rechargeMethods)
        val giftEnabled = wallet.type != WalletType.Multichain
        val itemsCount = if (giftEnabled) {
            supportedTokens.size + 1
        } else {
            supportedTokens.size
        }

        val uiItems = mutableListOf<Item>()
        for ((index, supportToken) in supportedTokens.withIndex()) {
            val position = ListCell.getPosition(itemsCount, index)
            uiItems.add(
                Item.RechargeMethod(
                    wallet = wallet, position = position, token = supportToken, from = from
                )
            )
        }
        if (uiItems.isNotEmpty() && giftEnabled) {
            uiItems.add(Item.Gift(wallet, position = ListCell.Position.LAST, from = from))
        }
        return uiItems.toList()
    }

    private suspend fun getBatteryConfig(
        wallet: WalletEntity
    ): BatteryConfigEntity {
        return batteryRepository.getConfig(wallet.network)
    }

    private suspend fun resolveWallet(): WalletEntity {
        return unifiedAccountRepository.getTonWalletById(wallet.id) ?: wallet
    }

    private suspend fun getBatteryBalance(
        wallet: WalletEntity
    ): BatteryBalanceEntity {
        return batteryRepository.getBalance(wallet)
    }

    private suspend fun getTokens(wallet: WalletEntity): List<AccountTokenEntity> {
        return tokenRepository.get(
            currency = settingsRepository.currency,
            accountId = wallet.accountId,
            network = wallet.network
        ) ?: emptyList()
    }

    private suspend fun getSupportedTokens(
        wallet: WalletEntity, rechargeMethods: List<RechargeMethodEntity>
    ): List<AccountTokenEntity> {
        val tokens = getTokens(wallet)
        val supportTokenAddress = rechargeMethods.filter { it.supportRecharge }.mapNotNull {
            if (it.type == RechargeMethodType.TON) {
                TokenEntity.TON.address
            } else {
                it.jettonMaster
            }
        }

        return tokens.filter { token ->
            supportTokenAddress.contains(token.address)
        }.sortedWith(compareByDescending<AccountTokenEntity> { token ->
            token.isUsdt // Place USDT at the top
        }.thenBy { token ->
            token.isTon // Place TON at the end
        }.thenByDescending { token ->
            token.fiat // Sort by fiat value
        })
    }

    fun applyPromo(promo: String) {
        _promoFlow.value = promo
        submitPromo(promo)
    }

    fun submitPromo(promo: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (promo.isEmpty()) {
                batteryRepository.setAppliedPromo(wallet.network, null)
                _promoStateFlow.value = PromoState.Default
            } else {
                _promoStateFlow.value = PromoState.Loading
                try {
                    if (api.batteryVerifyPurchasePromo(wallet.network, promo)) {
                        batteryRepository.setAppliedPromo(wallet.network, promo)
                        _promoStateFlow.value = PromoState.Applied(promo)
                    } else {
                        throw IllegalStateException("promo code is invalid")
                    }
                } catch (_: Exception) {
                    batteryRepository.setAppliedPromo(wallet.network, null)
                    _promoStateFlow.value = PromoState.Error
                }
            }
        }
    }

    private suspend fun handlePurchases(purchases: List<Purchase>) = withContext(Dispatchers.IO) {
        try {
            purchaseInProgress.tryEmit(true)
            val tonWallet = resolveWallet()
            for (purchase in purchases) {
                val request = AndroidBatteryPurchaseRequest(
                    purchases = listOf(
                        AndroidBatteryPurchaseRequestPurchasesInner(
                            productId = purchase.products.first(),
                            token = purchase.purchaseToken,
                            promo = batteryRepository.getAppliedPromo(wallet.network)
                        )
                    )
                )

                val purchaseStatus = api.batteryPurchase(
                    auth = authorizationProvider.getAuthBy(tonWallet.id),
                    network = tonWallet.network,
                    request = request,
                )

                val firstTransaction = purchaseStatus.purchases.firstOrNull { it.productId == purchase.products.first() }

                if (firstTransaction != null && firstTransaction.success) {
                    batteryRepository.getBalance(
                        wallet = tonWallet,
                        ignoreCache = true,
                    )
                    withContext(Dispatchers.Main) {
                        analytics.events.batteryNative.batterySuccess(
                            from = from,
                            type = BatteryNativeType.Fiat,
                            size = batterySize(purchase.products.first()),
                            promo = (_promoStateFlow.value as? PromoState.Applied)?.appliedPromo,
                            jetton = null
                        )
                    }
                    billingManager.consumeProduct(purchase.purchaseToken)
                    toast(Localization.battery_refilled)
                } else {
                    toast(Localization.error)
                }
            }
        } catch (e: Exception) {
            L.e(e)
            toast(Localization.error)
        } finally {
            purchaseInProgress.tryEmit(false)
        }
    }

    private fun batterySize(productId: String): BatteryNativeSize {
        return when (config.iapPackages.firstOrNull { it.productId == productId }?.id) {
            IAPPackageId.LARGE -> BatteryNativeSize.Large
            IAPPackageId.MEDIUM -> BatteryNativeSize.Medium
            IAPPackageId.SMALL -> BatteryNativeSize.Small
            null -> BatteryNativeSize.Custom
        }
    }

    fun makePurchase(productId: String, activity: Activity) {
        if (iapAvailabilityFlow.value != IapAvailability.ALLOWED) {
            return
        }
        promoStateFlow.take(1).collectFlow { promoState ->
            analytics.events.batteryNative.batterySelect(
                from = from,
                type = BatteryNativeType.Fiat,
                size = batterySize(productId),
                promo = (promoState as? PromoState.Applied)?.appliedPromo,
                jetton = null
            )
        }

        billingManager.productFlow(productId).collectFlow { product ->
            billingManager.requestPurchase(activity, wallet, product)
        }
    }

    fun restorePurchases() {
        viewModelScope.launch {
            try {
                val pendingPurchases = billingManager.restorePurchases()
                if (pendingPurchases.isNotEmpty()) {
                    handlePurchases(pendingPurchases)
                } else {
                    toast(Localization.nothing_to_restore)
                }
            } catch (_: Exception) {
                toast(Localization.error)
            }
        }
    }
}