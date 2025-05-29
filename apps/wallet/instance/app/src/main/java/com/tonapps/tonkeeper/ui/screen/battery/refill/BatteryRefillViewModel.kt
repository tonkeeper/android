package com.tonapps.tonkeeper.ui.screen.battery.refill

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.billing.BillingManager
import com.tonapps.tonkeeper.billing.priceFormatted
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.koin.installId
import com.tonapps.tonkeeper.koin.remoteConfig
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.battery.refill.entity.PromoState
import com.tonapps.tonkeeper.ui.screen.battery.refill.list.Item
import com.tonapps.tonkeeperx.BuildConfig
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.battery.BatteryMapper
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.battery.entity.BatteryBalanceEntity
import com.tonapps.wallet.data.battery.entity.BatteryConfigEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodType
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.BatteryTransaction
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.localization.Localization
import io.batteryapi.models.AndroidBatteryPurchaseRequest
import io.batteryapi.models.AndroidBatteryPurchaseRequestPurchasesInner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

class BatteryRefillViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val api: API,
    private val accountRepository: AccountRepository,
    private val batteryRepository: BatteryRepository,
    private val tokenRepository: TokenRepository,
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
    private val billingManager: BillingManager,
    private val environment: Environment,
) : BaseWalletVM(app) {

    private val _promoFlow = MutableStateFlow<String?>(null)
    private val promoFlow = _promoFlow.asStateFlow()

    private val purchasesFlow = billingManager.purchasesUpdatedFlow
        .map { it.purchases }
        .filter { it.isNotEmpty() }

    private val _promoStateFlow = MutableStateFlow<PromoState>(PromoState.Default)
    private val promoStateFlow = _promoStateFlow.asStateFlow()

    private val purchaseInProgress = MutableStateFlow(false)

    private val settingsUpdateFlow = combine(
        settingsRepository.walletPrefsChangedFlow,
        batteryRepository.balanceUpdatedFlow,
    ) { _, _ -> }

    val uiItemsFlow = combine(
        promoStateFlow,
        billingManager.productsFlow,
        purchaseInProgress,
        settingsUpdateFlow,
        promoFlow,
    ) { promoState, iapProducts, isProcessing, _, promoCode ->
        val batteryBalance = getBatteryBalance(wallet)

        val uiItems = mutableListOf<Item>()
        uiItems.add(uiItemBattery(batteryBalance, api.config))
        uiItems.add(Item.Space)

        if (BuildConfig.DEBUG || !api.config.batteryPromoDisable) {
            uiItems.add(Item.Promo(promoState, promoCode))
            uiItems.add(Item.Space)
        }

        if (batteryBalance.balance.isPositive) {
            uiItems.add(Item.Settings(settingsRepository.getBatteryTxEnabled(wallet.accountId)))
            uiItems.add(Item.Space)
        }

        if (environment.isGooglePlayServicesAvailable && !api.config.disableBatteryIapModule) {
            val tonPriceInUsd =
                ratesRepository.getTONRates(WalletCurrency.USD).getRate(TokenEntity.TON.address)

            if (tonPriceInUsd > Coins.ZERO) {
                uiItems.addAll(
                    uiItemsPackages(
                        tonPriceInUsd = tonPriceInUsd,
                        batteryBalance = batteryBalance,
                        config = api.config,
                        products = iapProducts,
                        isProcessing = isProcessing,
                    )
                )
                uiItems.add(Item.Space)
            }
        }

        val rechargeMethodsItems = uiItemsRechargeMethods(wallet)

        if (context.remoteConfig?.isBatteryCryptoRechargeDisable == false && rechargeMethodsItems.isNotEmpty()) {
            uiItems.addAll(uiItemsRechargeMethods(wallet))
            uiItems.add(Item.Space)
        }

        val tonProofToken = accountRepository.requestTonProofToken(wallet) ?: ""

        uiItems.add(
            Item.Refund(
                wallet = wallet, refundUrl = "${api.config.batteryRefundEndpoint}?token=${
                    URLEncoder.encode(
                        tonProofToken, "UTF-8"
                    )
                }&testnet=${wallet.testnet}"
            )
        )

        uiItems.add(Item.Space)
        uiItems.add(Item.RestoreIAP)

        uiItems.toList()
    }.flowOn(Dispatchers.IO)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (environment.isGooglePlayServicesAvailable) {
                billingManager.loadProducts(api.config.iapPackages.map { it.productId })
            } else {
                billingManager.setEmptyProducts()
            }

            val appliedPromo = batteryRepository.getAppliedPromo(wallet.testnet)

            if (appliedPromo.isNullOrBlank()) {
                _promoStateFlow.value = PromoState.Default
            } else {
                _promoStateFlow.value = PromoState.Applied(appliedPromo)
            }
        }

        purchasesFlow.collectFlow(::handlePurchases)
    }

    fun setPromo(promo: String) {

    }

    private fun uiItemsPackages(
        tonPriceInUsd: Coins,
        batteryBalance: BatteryBalanceEntity,
        config: ConfigEntity,
        products: List<ProductDetails>,
        isProcessing: Boolean,
    ): List<Item.IAPPack> {
        val isBatteryEmpty = batteryBalance.reservedBalance.isZero && batteryBalance.balance.isZero
        val reservedAmount =
            if (isBatteryEmpty) config.batteryReservedAmount.toBigDecimal() else BigDecimal.ZERO

        val uiItems = mutableListOf<Item.IAPPack>()

        config.iapPackages.forEachIndexed { index, iapPackage ->
            val position = ListCell.getPosition(config.iapPackages.size, index)
            val product = products.find { it.productId == iapPackage.productId }
            val charges = BatteryMapper.calculateIapCharges(
                userProceed = iapPackage.userProceed,
                tonPriceInUsd = tonPriceInUsd,
                reservedAmount = reservedAmount,
                meanFees = config.batteryMeanFees.toBigDecimal()
            )

            val transactions = mapOf(
                BatteryTransaction.SWAP to charges / BatteryMapper.calculateChargesAmount(
                    config.batteryMeanPriceSwap, config.batteryMeanFees
                ), BatteryTransaction.NFT to charges / BatteryMapper.calculateChargesAmount(
                    config.batteryMeanPriceNft, config.batteryMeanFees
                ), BatteryTransaction.JETTON to charges / BatteryMapper.calculateChargesAmount(
                    config.batteryMeanPriceJetton, config.batteryMeanFees
                )
            )

            val formattedPrice = product?.priceFormatted ?: context.getString(Localization.loading)
            uiItems.add(
                Item.IAPPack(
                    position = position,
                    packType = iapPackage.id,
                    productId = iapPackage.productId,
                    isEnabled = product != null && !isProcessing,
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
        config: ConfigEntity,
    ): Item.Battery {
        val charges = BatteryMapper.convertToCharges(balance.balance, api.config.batteryMeanFees)
        val formattedChanges = CurrencyFormatter.format(value = charges.toBigDecimal())

        return Item.Battery(
            balance = balance.balance.value.toFloat(),
            beta = false, // config.batteryBeta,
            changes = charges,
            formattedChanges = formattedChanges
        )
    }

    private suspend fun uiItemsRechargeMethods(
        wallet: WalletEntity,
    ): List<Item> {
        val batteryConfig = getBatteryConfig(wallet)
        val supportedTokens = getSupportedTokens(wallet, batteryConfig.rechargeMethods)

        val uiItems = mutableListOf<Item>()
        for ((index, supportToken) in supportedTokens.withIndex()) {
            val position = ListCell.getPosition(supportedTokens.size + 1, index)
            uiItems.add(
                Item.RechargeMethod(
                    wallet = wallet, position = position, token = supportToken
                )
            )
        }
        if (uiItems.isNotEmpty()) {
            uiItems.add(Item.Gift(wallet, position = ListCell.Position.LAST))
        }
        return uiItems.toList()
    }

    private suspend fun getBatteryConfig(
        wallet: WalletEntity
    ): BatteryConfigEntity {
        return batteryRepository.getConfig(wallet.testnet)
    }

    private suspend fun getBatteryBalance(
        wallet: WalletEntity
    ): BatteryBalanceEntity {
        val tonProofToken =
            accountRepository.requestTonProofToken(wallet) ?: return BatteryBalanceEntity.Empty
        return batteryRepository.getBalance(
            tonProofToken = tonProofToken, publicKey = wallet.publicKey, testnet = wallet.testnet
        )
    }

    private suspend fun getTokens(wallet: WalletEntity): List<AccountTokenEntity> {
        return tokenRepository.get(
            currency = settingsRepository.currency,
            accountId = wallet.accountId,
            testnet = wallet.testnet
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
            supportTokenAddress.contains(token.address) && token.balance.value.isPositive
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
                batteryRepository.setAppliedPromo(wallet.testnet, null)
                _promoStateFlow.value = PromoState.Default
            } else {
                _promoStateFlow.value = PromoState.Loading
                try {
                    if (api.batteryVerifyPurchasePromo(wallet.testnet, promo)) {
                        batteryRepository.setAppliedPromo(wallet.testnet, promo)
                        _promoStateFlow.value = PromoState.Applied(promo)
                    } else {
                        throw IllegalStateException("promo code is invalid")
                    }
                } catch (_: Exception) {
                    batteryRepository.setAppliedPromo(wallet.testnet, null)
                    _promoStateFlow.value = PromoState.Error
                }
            }
        }
    }

    private suspend fun handlePurchases(purchases: List<Purchase>) = withContext(Dispatchers.IO) {
        try {
            purchaseInProgress.tryEmit(true)
            val tonProofToken = accountRepository.requestTonProofToken(wallet)
                ?: throw IllegalStateException("proof token is null")
            for (purchase in purchases) {
                val request = AndroidBatteryPurchaseRequest(
                    purchases = listOf(
                        AndroidBatteryPurchaseRequestPurchasesInner(
                            productId = purchase.products.first(),
                            token = purchase.purchaseToken,
                            promo = batteryRepository.getAppliedPromo(wallet.testnet)
                        )
                    )
                )
                api.battery(wallet.testnet).androidBatteryPurchase(tonProofToken, request)
                batteryRepository.getBalance(
                    tonProofToken, wallet.publicKey, wallet.testnet, ignoreCache = true
                )
                val promoCode = (_promoStateFlow.value as? PromoState.Applied)?.appliedPromo ?: "null"
                withContext(Dispatchers.Main) {
                    AnalyticsHelper.batterySuccess(settingsRepository.installId, "fiat", promoCode, "")
                }
                billingManager.consumeProduct(purchase.purchaseToken)
            }
            toast(Localization.battery_refilled)
        } catch (e: Exception) {
            toast(Localization.error)
        } finally {
            purchaseInProgress.tryEmit(false)
        }
    }

    fun makePurchase(productId: String, activity: Activity) {
        promoStateFlow.take(1).collectFlow { promoState ->
            val promoCode = (promoState as? PromoState.Applied)?.appliedPromo ?: "null"
            AnalyticsHelper.simpleTrackEvent("battery_select", settingsRepository.installId, hashMapOf(
                "size" to productId,
                "promo" to promoCode,
                "type" to "fiat"
            ))
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