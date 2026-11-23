package com.tonapps.tonkeeper.ui.screen.battery.refill

import android.app.Activity
import android.app.Application
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeSize
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeType
import com.tonapps.extensions.bestMessage
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.log.L
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.billing.BillingManager
import com.tonapps.tonkeeper.billing.PurchasesUpdated
import com.tonapps.tonkeeper.billing.isSuccess
import com.tonapps.tonkeeper.billing.priceFormatted
import com.tonapps.tonkeeper.koin.dappScreenFactory
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.battery.recharge.BatteryRechargeScreen
import com.tonapps.tonkeeper.ui.screen.battery.recharge.BatteryTonRecharge
import com.tonapps.tonkeeper.ui.screen.battery.recharge.asset.BatteryAssetRechargeScreen
import com.tonapps.tonkeeper.ui.screen.battery.recharge.asset.BatteryRechargeAssets
import com.tonapps.tonkeeper.ui.screen.battery.recharge.batterySize
import com.tonapps.tonkeeper.ui.screen.battery.recharge.entity.BatteryRechargeEvent
import com.tonapps.tonkeeper.ui.screen.battery.refill.entity.PromoState
import com.tonapps.tonkeeper.ui.screen.battery.web.BatteryWebScreen
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.AuthorizationProvider
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.api.entity.IAPPackageId
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.battery.BatteryMapper
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.battery.entity.BatteryBalanceEntity
import com.tonapps.wallet.data.battery.entity.BatteryConfigEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodEntity
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.Plurals
import io.batteryapi.models.AndroidBatteryPurchaseRequest
import io.batteryapi.models.AndroidBatteryPurchaseRequestPurchasesInner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.absoluteValue

private const val MAX_ACCOUNT_PAGES = 10
private const val PURCHASE_LAUNCH_TIMEOUT_MILLIS = 10_000L
private const val PURCHASE_RESULT_GRACE_MILLIS = 3_000L

private enum class IapAvailability {
    UNKNOWN,
    ALLOWED,
    DISABLED_BY_REFUNDS,
}

private data class StorePack(
    val productId: String,
    val charges: Int,
    val product: ProductDetails?,
)

private data class PackRow(
    val id: String,
    val title: String,
    val caption: String,
    val level: Float,
    val charges: Int,
    val enabled: Boolean,
)

private data class ScreenInputs(
    val pickedPacks: Map<String, String>,
    val hiddenBalances: Boolean,
    val promoCode: String?,
    val pickedMethod: String?,
)

class BatteryRefillViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val from: BatteryNativeFrom,
    private val utm: UtmParameters,
    jetton: String,
    private val api: API,
    private val accountRepository: AccountRepository,
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val mcAccountRepository: McAccountRepository,
    private val authorizationProvider: AuthorizationProvider,
    private val batteryRepository: BatteryRepository,
    private val tokenRepository: TokenRepository,
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
    private val billingManager: BillingManager,
    private val environment: Environment,
    private val analytics: AnalyticsHelper,
    savedStateHandle: SavedStateHandle,
    private val tonRecharge: BatteryTonRecharge,
) : BaseWalletVM(app) {

    private val config: ConfigEntity
        get() = api.getConfig(wallet.network)

    private val isBatteryDisabled: Boolean
        get() = config.flags.disableBattery

    private val isCryptoDisabled: Boolean
        get() = config.disableBatteryCryptoRechargeModule

    private val _promoFlow = MutableStateFlow<String?>(null)
    private val promoFlow = _promoFlow.asStateFlow()

    private val _promoStateFlow = MutableStateFlow<PromoState>(PromoState.Default)
    private val promoStateFlow = _promoStateFlow.asStateFlow()

    private val purchaseInProgress = MutableStateFlow(false)

    private val purchaseLaunchFlow = MutableStateFlow(false)

    private var promoJob: Job? = null

    private var launchFallbackJob: Job? = null

    private var playFlowOpened = false

    private val restoreInProgress = MutableStateFlow(false)

    private val historyInProgress = MutableStateFlow(false)

    private val purchasesMutex = Mutex()

    private val activePurchaseHandlers = AtomicInteger(0)

    private val paymentInFlight: Boolean
        get() = signingFlow.value ||
            purchaseLaunchFlow.value ||
            purchaseInProgress.value ||
            restoreInProgress.value ||
            historyInProgress.value

    private val iapAvailabilityFlow = MutableStateFlow(IapAvailability.UNKNOWN)

    private val signingFlow = MutableStateFlow(false)

    private val busyFlow = combine(
        purchaseInProgress,
        purchaseLaunchFlow,
        restoreInProgress,
        signingFlow,
        historyInProgress,
    ) { flags -> flags.any { it } }

    private val iapStateFlow = combine(busyFlow, iapAvailabilityFlow) { busy, availability ->
        busy to availability
    }

    // Null while the methods are loading.
    private val rechargeMethodsFlow = MutableStateFlow<List<RechargeMethodEntity>?>(null)

    // A multichain wallet's positions to pay with: the wallet screen's cached page at once, then every one.
    private val payAccountsFlow = MutableStateFlow<List<AccountWithDetails>>(emptyList())

    private val settingsUpdateFlow = combine(
        settingsRepository.walletPrefsChangedFlow,
        batteryRepository.balanceUpdatedFlow,
        rechargeMethodsFlow,
        payAccountsFlow,
    ) { _, _, rechargeMethods, _ -> rechargeMethods }

    private val pickedPacksFlow = MutableStateFlow<Map<String, String>>(emptyMap())

    private val pickedMethodFlow = MutableStateFlow(settingsRepository.getBatteryPayMethod(wallet.id))

    private val screenInputsFlow = combine(
        pickedPacksFlow,
        settingsRepository.hiddenBalancesFlow,
        promoFlow,
        pickedMethodFlow,
    ) { pickedPacks, hiddenBalances, promoCode, pickedMethod ->
        ScreenInputs(pickedPacks, hiddenBalances, promoCode, pickedMethod)
    }

    val uiStateFlow: StateFlow<BatteryRefillUiState?> = combine(
        promoStateFlow,
        billingManager.productsFlow,
        iapStateFlow,
        settingsUpdateFlow,
        screenInputsFlow,
    ) { promoState, products, (busy, iapAvailability), rechargeMethods, inputs ->
        buildState(promoState, products, busy, iapAvailability, rechargeMethods, inputs)
    }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_SUBSCRIPTION_TIMEOUT_MILLIS), null)

    init {
        if (savedStateHandle.get<Boolean>(KEY_INITIAL_ROUTE_OPENED) != true) {
            savedStateHandle[KEY_INITIAL_ROUTE_OPENED] = true
            if (jetton.isNotEmpty()) {
                openRecharge(jetton)
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            loadRechargeMethods()
        }

        if (environment.isGooglePlayServicesAvailable) {
            viewModelScope.launch(Dispatchers.IO) {
                billingManager.loadProducts(config.iapPackages.map { it.productId })
            }
            viewModelScope.launch(Dispatchers.IO) {
                loadIapAvailability()
            }
        } else {
            billingManager.setEmptyProducts()
        }

        promoJob = viewModelScope.launch(Dispatchers.IO) {
            val appliedPromo = batteryRepository.getAppliedPromo(wallet.network)

            if (appliedPromo.isNullOrBlank()) {
                setPromoState(PromoState.Default)
            } else {
                setPromoState(PromoState.Applied(appliedPromo))
            }
        }

        billingManager.purchaseResultsFlow.collectFlow(::onPurchaseResult)
    }

    private suspend fun buildState(
        promoState: PromoState,
        products: List<ProductDetails>?,
        busy: Boolean,
        iapAvailability: IapAvailability,
        rechargeMethods: List<RechargeMethodEntity>?,
        inputs: ScreenInputs,
    ): BatteryRefillUiState {
        val tonWallet = resolveWallet()
        val batteryBalance = getBatteryBalance(tonWallet)
        val batteryConfig = getBatteryConfig(tonWallet)
        val isMultichain = tonWallet.type == WalletType.Multichain

        val iapAllowed = environment.isGooglePlayServicesAvailable &&
            !config.disableBatteryIapModule &&
            !isBatteryDisabled &&
            iapAvailability != IapAvailability.DISABLED_BY_REFUNDS
        val tonPriceInUsd = if (iapAllowed) {
            ratesRepository.getTONRates(tonWallet.network, WalletCurrency.USD).getRate(TokenEntity.TON.address)
        } else {
            Coins.ZERO
        }
        val staticAllowed = iapAllowed && tonPriceInUsd > Coins.ZERO
        val storePacks = if (staticAllowed) {
            uiItemsPackages(tonPriceInUsd, batteryBalance, config, products.orEmpty(), batteryConfig)
        } else {
            emptyList()
        }
        val storeAvailable = storeAvailability(
            staticAllowed = staticAllowed,
            iapKnown = iapAvailability != IapAvailability.UNKNOWN,
            productsLoaded = products != null,
            hasPricedPack = storePacks.any { it.product != null },
        )

        val cryptoAllowed = !isBatteryDisabled && !isCryptoDisabled
        val tokens = if (cryptoAllowed && rechargeMethods != null) {
            getTokens(tonWallet)
        } else {
            emptyList()
        }
        val supportedTokens = when {
            !cryptoAllowed -> emptyList()
            rechargeMethods == null -> null
            else -> supportedTokens(tokens, rechargeMethods.filter { it.type.isTon })
        }
        val payTokens = supportedTokens?.filter { (token, _) -> token.balance.value > Coins.ZERO }
        val assetsAllowed = isMultichain && !tonWallet.isMultichainLedger && !isBatteryDisabled && !isCryptoDisabled
        val supportedAssets = if (assetsAllowed && rechargeMethods != null) {
            getSupportedAssets(rechargeMethods.filterNot { it.type.isTon })
        } else {
            emptyList()
        }

        val methodChoice = resolveMethod(
            stored = inputs.pickedMethod,
            tokens = payTokens?.map { (token, _) -> token.address },
            storeAvailable = storeAvailable,
        )
        val chosenToken = (methodChoice as? MethodChoice.Token)?.let { choice ->
            payTokens?.firstOrNull { (token, _) -> token.address == choice.address }
        }

        val tokenRows = payTokens.orEmpty().map { (token, method) ->
            PayMethodRowUi(
                id = token.address,
                title = token.symbol,
                caption = getString(
                    Localization.battery_pay_balance,
                    if (inputs.hiddenBalances) {
                        HIDDEN_BALANCE
                    } else {
                        CurrencyFormatter.format(currency = token.symbol, value = token.balance.value)
                    },
                ),
                imageUrl = token.imageUri.toString().takeIf { it.isNotBlank() } ?: method.image.orEmpty(),
                chainBadge = BatteryRechargeAssets.badgeChain(method).takeIf { isMultichain },
                kind = PayMethodKind.Token,
                selected = chosenToken?.first?.address == token.address,
            )
        }
        val assetRows = supportedAssets.map { (account, method) ->
            val asset = account.asset
            PayMethodRowUi(
                id = asset.id,
                title = asset.symbol,
                caption = asset.value.chain.name,
                imageUrl = method.image?.takeIf { it.isNotBlank() } ?: asset.imageUrl,
                chainBadge = BatteryRechargeAssets.badgeChain(method),
                kind = PayMethodKind.Asset,
                selected = false,
            )
        }
        val storeRow = PayMethodRowUi(
            id = STORE_PAY_METHOD,
            title = getString(Localization.battery_pay_google_play),
            caption = getString(Localization.battery_pay_in_app),
            imageUrl = null,
            chainBadge = null,
            kind = PayMethodKind.Store,
            selected = methodChoice == MethodChoice.Store,
        )
        val methodRows = if (storeAvailable == false) {
            tokenRows + assetRows
        } else {
            tokenRows + assetRows + storeRow
        }

        val method = methodRows.firstOrNull { it.selected }

        val packRows = when {
            methodChoice == MethodChoice.Store -> {
                val maxCharges = storePacks.maxOfOrNull { it.charges } ?: 0
                storePacks.map { pack ->
                    PackRow(
                        id = pack.productId,
                        title = pack.product?.priceFormatted?.toString() ?: getString(Localization.loading),
                        caption = chargesCaption(pack.charges),
                        level = pack.charges.toFloat() / maxCharges,
                        charges = pack.charges,
                        enabled = pack.product != null && iapAvailability == IapAvailability.ALLOWED,
                    )
                }
            }
            chosenToken != null -> try {
                tokens.find { it.isTon }?.let { ton ->
                    tonRecharge.packs(
                        wallet = tonWallet,
                        token = chosenToken.first,
                        batteryConfig = batteryConfig,
                        willBePaidManually = tonRecharge.willBePaidManually(ton, batteryBalance),
                    )
                }.orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                L.e(e)
                emptyList()
            }.sortedByDescending { it.charges }.map { pack ->
                PackRow(
                    id = pack.type,
                    title = pack.formattedAmount.toString(),
                    caption = chargesCaption(pack.charges),
                    level = pack.batteryLevel,
                    charges = pack.charges,
                    enabled = pack.isEnabled,
                )
            }
            else -> emptyList()
        }

        val choices = packRows.map { PackChoice(it.id, it.charges, it.enabled) }
        val selectedId = method?.let { selectPack(inputs.pickedPacks[it.id], choices) }
        val bestValue = bestValueId(choices)
        val selectedRow = packRows.firstOrNull { it.id == selectedId }
        val packs = packRows.map { row ->
            PackUi(
                id = row.id,
                title = row.title,
                caption = row.caption,
                level = row.level,
                enabled = row.enabled && !busy,
                selected = row.id == selectedId,
                bestValue = row.id == bestValue,
                isOther = false,
            )
        }
        val otherPack = PackUi(
            id = OTHER_PACK_ID,
            title = getString(Localization.battery_other_title),
            caption = getString(Localization.battery_other_subtitle),
            level = 0f,
            enabled = !busy,
            selected = false,
            bestValue = false,
            isOther = true,
        )
        val button = buttonState(
            hasPacks = packRows.isNotEmpty(),
            selectedTitle = selectedRow?.title,
            selectedEnabled = selectedRow?.enabled ?: false,
            busy = busy || promoState is PromoState.Loading,
        )?.let { choice ->
            ButtonUi(
                text = choice.title?.let { getString(Localization.battery_top_up_for, it) }
                    ?: getString(Localization.swap_top_up_battery),
                enabled = choice.enabled,
            )
        }

        return BatteryRefillUiState(
            header = uiItemBattery(batteryBalance, batteryConfig, tonWallet),
            promo = if (config.batteryPromoDisable || isBatteryDisabled) {
                null
            } else {
                PromoUi(promoState, inputs.promoCode)
            },
            methodLoading = methodChoice == MethodChoice.Loading,
            packs = if (chosenToken != null) {
                packs + otherPack
            } else {
                packs
            },
            button = button,
            methods = methodRows,
            giftVisible = !isMultichain && !supportedTokens.isNullOrEmpty(),
            chargingEnabled = !isBatteryDisabled,
            busy = busy,
        )
    }

    private fun chargesCaption(charges: Int): String {
        val formatted = CurrencyFormatter.format(value = charges.toBigDecimal())
        return context.resources.getQuantityString(Plurals.battery_charges, charges, formatted)
    }

    private fun openRecharge(jetton: String) {
        viewModelScope.launch {
            val rechargeToken =
                batteryRepository.getRechargeMethodByJetton(wallet.network, jetton)?.jettonMaster
                    ?: "TON"
            val tokens =
                tokenRepository.get(settingsRepository.currency, wallet.accountId, wallet.network)
                    ?: return@launch
            val token =
                tokens.firstOrNull { it.address.equalsAddress(rechargeToken) } ?: return@launch
            openScreen(BatteryRechargeScreen.newInstance(wallet, from, token, utm = utm))
        }
    }

    fun setBatteryViewed() {
        if (!settingsRepository.batteryViewed) {
            viewModelScope.launch {
                settingsRepository.batteryViewed = true
                batteryRepository.getBalance(
                    wallet = wallet,
                    ignoreCache = true
                )
            }
        }
    }

    fun selectPack(id: String) {
        val methodId = uiStateFlow.value?.method?.id ?: return
        pickedPacksFlow.update { it + (methodId to id) }
    }

    fun selectMethod(id: String) {
        if (paymentInFlight) {
            return
        }
        pickedMethodFlow.value = id
        settingsRepository.setBatteryPayMethod(wallet.id, id)
    }

    fun topUp(activity: Activity) {
        val state = uiStateFlow.value ?: return
        val method = state.method ?: return
        val pack = state.packs.firstOrNull { it.selected } ?: return
        val promoLoading = promoJob?.isActive == true || _promoStateFlow.value is PromoState.Loading
        if (paymentInFlight || promoLoading || state.button?.enabled != true) {
            return
        }
        val inFlight = if (method.kind == PayMethodKind.Store) {
            purchaseLaunchFlow
        } else {
            signingFlow
        }
        if (!inFlight.compareAndSet(expect = false, update = true)) {
            return
        }
        pickedPacksFlow.update { it + (method.id to pack.id) }
        if (method.kind == PayMethodKind.Store) {
            launchFallbackJob?.cancel()
            playFlowOpened = false
            makePurchase(pack.id, activity)
        } else {
            topUpWithToken(method.id, pack.id)
        }
    }

    private fun topUpWithToken(tokenAddress: String, packType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tonWallet = resolveWallet()
                val token = getTokens(tonWallet).firstOrNull { it.address == tokenAddress } ?: return@launch
                val promo = (_promoStateFlow.value as? PromoState.Applied)?.appliedPromo
                analytics.eventsTaggedWith(utm).batteryNative.batterySelect(
                    from = from,
                    type = BatteryNativeType.Crypto,
                    size = packType.batterySize(),
                    promo = promo,
                    jetton = token.symbol,
                )
                when (val event = tonRecharge.request(tonWallet, token, packType, customAmount = null, recipient = null)) {
                    is BatteryRechargeEvent.Sign -> {
                        tonRecharge.sign(context, tonWallet, event.request, event.forceRelayer, utm, isGift = false)
                        analytics.eventsTaggedWith(utm).batteryNative.batterySuccess(
                            from = from,
                            type = BatteryNativeType.Crypto,
                            size = packType.batterySize(),
                            promo = promo,
                            jetton = token.symbol,
                        )
                        toast(Localization.battery_please_wait)
                        withContext(Dispatchers.Main) {
                            navigation?.openURL("tonkeeper://activity?from=battery")
                        }
                        finish()
                    }
                    is BatteryRechargeEvent.MaxAmountError -> failureToast(
                        getString(
                            Localization.battery_max_input_amount,
                            CurrencyFormatter.format(currency = event.currency, value = event.maxAmount),
                        )
                    )
                    BatteryRechargeEvent.Error -> failureToast(Localization.sending_error)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failureToast(e.bestMessage)
            } finally {
                signingFlow.value = false
            }
        }
    }

    fun openOther() {
        val tokenAddress = uiStateFlow.value?.method?.takeIf { it.kind != PayMethodKind.Store }?.id ?: return
        if (paymentInFlight) {
            return
        }
        viewModelScope.launch {
            val tonWallet = resolveWallet()
            val token = getTokens(tonWallet).firstOrNull { it.address == tokenAddress } ?: return@launch
            openScreen(BatteryRechargeScreen.newInstance(tonWallet, from, token, utm = utm, selectsCustomAmount = true))
        }
    }

    fun openAsset(assetId: String) {
        if (paymentInFlight) {
            return
        }
        viewModelScope.launch {
            openScreen(BatteryAssetRechargeScreen.newInstance(resolveWallet(), from, assetId, utm))
        }
    }

    fun openGift() {
        if (paymentInFlight) {
            return
        }
        viewModelScope.launch {
            openScreen(BatteryRechargeScreen.newInstance(resolveWallet(), from, isGift = true, utm = utm))
        }
    }

    fun openHistory() {
        if (paymentInFlight || !historyInProgress.compareAndSet(expect = false, update = true)) {
            return
        }
        viewModelScope.launch {
            try {
                val tonWallet = resolveWallet()
                val tonProofToken = withContext(Dispatchers.IO) {
                    accountRepository.requestTonProofToken(tonWallet)
                } ?: ""
                val refundUrl = "${config.batteryRefundEndpoint}?token=${URLEncoder.encode(tonProofToken, "UTF-8")}&testnet=${tonWallet.testnet}"
                val screen = if (tonWallet.isMultichain) {
                    BatteryWebScreen.newInstance(
                        wallet = tonWallet,
                        url = refundUrl.toUri(),
                    )
                } else {
                    context.dappScreenFactory.create(
                        wallet = tonWallet,
                        title = "Battery",
                        url = refundUrl.toUri(),
                        iconUrl = "",
                        source = "battery_refund",
                    )
                }
                val navigation = navigation
                finish()
                navigation?.add(screen)
            } finally {
                historyInProgress.value = false
            }
        }
    }

    /**
     * The methods stored before, then the battery's: a multichain wallet's list is asked for on every opening,
     * since nothing else refreshes it. A TON wallet's comes with the battery config, which a launch refreshes.
     */
    private suspend fun loadRechargeMethods() {
        val tonWallet = resolveWallet()
        val isMultichain = tonWallet.type == WalletType.Multichain

        batteryRepository.getCachedRechargeMethods(tonWallet.network, isMultichain)?.let {
            rechargeMethodsFlow.value = it
        }

        // The positions to pay with load alongside the methods: neither waits for the other.
        if (isMultichain && !tonWallet.isMultichainLedger) {
            viewModelScope.launch { loadPayAccounts(tonWallet) }
        }

        rechargeMethodsFlow.value = try {
            batteryRepository.getRechargeMethods(tonWallet.network, isMultichain, ignoreCache = isMultichain)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e)
            rechargeMethodsFlow.value.orEmpty()
        }
    }

    /**
     * Every position the wallet holds, not only what the wallet screen shows: a hidden asset, dust or one past
     * its first page still pays. The screen's cached page shows meanwhile, and stays when the fetch fails.
     */
    private suspend fun loadPayAccounts(wallet: WalletEntity) {
        val currency = settingsRepository.currency.code
        cachedPayAccounts(wallet, currency)?.let { payAccountsFlow.value = it }

        try {
            payAccountsFlow.value = fetchPayAccounts(wallet, currency)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e)
        }
    }

    private suspend fun cachedPayAccounts(wallet: WalletEntity, currency: String): List<AccountWithDetails>? {
        return try {
            mcAccountRepository.getCachedAccounts(walletId = wallet.id, currency = currency)?.accounts
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e)
            null
        }
    }

    private suspend fun fetchPayAccounts(wallet: WalletEntity, currency: String): List<AccountWithDetails> {
        val accounts = ArrayList<AccountWithDetails>()
        var cursor: String? = null
        repeat(MAX_ACCOUNT_PAGES) {
            val page = mcAccountRepository.fetchAccounts(
                walletId = wallet.id,
                currency = currency,
                availableOnly = true,
                showHidden = true,
                showAll = true,
                cursor = cursor,
                verifiedOnly = false,
                hideDust = false,
            )
            accounts += page.accounts
            cursor = page.nextCursor ?: return accounts
        }
        return accounts
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
        batteryConfig: BatteryConfigEntity,
    ): List<StorePack> {
        val isBatteryEmpty = batteryBalance.reservedBalance.isZero && batteryBalance.balance.isZero
        val reservedAmount = if (isBatteryEmpty) {
            batteryConfig.reservedAmount.toBigDecimal()
        } else {
            BigDecimal.ZERO
        }

        return config.iapPackages.mapNotNull { iapPackage ->
            val product = products.find { it.productId == iapPackage.productId }
            val charges = BatteryMapper.calculateIapCharges(
                userProceed = iapPackage.userProceed,
                tonPriceInUsd = tonPriceInUsd,
                reservedAmount = reservedAmount,
                meanFees = batteryConfig.chargeCost.toBigDecimal()
            )

            if (charges == 0) {
                null
            } else {
                StorePack(
                    productId = iapPackage.productId,
                    charges = charges,
                    product = product,
                )
            }
        }
    }

    private fun uiItemBattery(
        balance: BatteryBalanceEntity,
        batteryConfig: BatteryConfigEntity,
        wallet: WalletEntity,
    ): HeaderUi {
        val charges = BatteryMapper.convertToSignedCharges(balance.balance, batteryConfig.chargeCost)
        val formattedAbsCharges = CurrencyFormatter.format(value = charges.absoluteValue.toBigDecimal())
        val formattedChanges = if (charges < 0) {
            "-$formattedAbsCharges"
        } else {
            formattedAbsCharges.toString()
        }

        return HeaderUi(
            level = BatteryMapper.convertToBatteryLevel(balance.balance),
            charges = charges,
            formattedCharges = formattedChanges,
            isNegative = balance.balance.isNegative,
            captionRes = when {
                wallet.isMultichainLedger -> Localization.battery_empty_caption_ledger
                wallet.type == WalletType.Multichain -> Localization.battery_empty_caption_multichain
                else -> Localization.battery_refill_subtitle
            },
        )
    }

    // The wallet's positions on the other chains that a method takes and that hold something to pay with.
    private fun getSupportedAssets(
        rechargeMethods: List<RechargeMethodEntity>,
    ): List<Pair<AccountWithDetails, RechargeMethodEntity>> {
        if (rechargeMethods.isEmpty()) {
            return emptyList()
        }

        val accounts = payAccountsFlow.value
        return BatteryRechargeAssets.payable(rechargeMethods, accounts).mapNotNull { account ->
            BatteryRechargeAssets.method(rechargeMethods, account.asset.id)?.let { account to it }
        }
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

    private fun supportedTokens(
        tokens: List<AccountTokenEntity>, rechargeMethods: List<RechargeMethodEntity>
    ): List<Pair<AccountTokenEntity, RechargeMethodEntity>> {
        val recharging = rechargeMethods.filter { it.supportRecharge }

        return tokens.mapNotNull { token ->
            recharging.firstOrNull { tonRecharge.pays(it, token) }?.let { token to it }
        }.sortedWith(compareByDescending<Pair<AccountTokenEntity, RechargeMethodEntity>> { (token, _) ->
            token.isUsdt // Place USDT at the top
        }.thenBy { (token, _) ->
            token.isTon // Place TON at the end
        }.thenByDescending { (token, _) ->
            token.fiat // Sort by fiat value
        })
    }

    fun applyPromo(promo: String) {
        _promoFlow.value = promo
        submitPromo(promo)
    }

    fun submitPromo(promo: String) {
        val previousJob = promoJob
        previousJob?.cancel()
        if (promo.isNotEmpty()) {
            _promoStateFlow.value = PromoState.Loading
        }
        promoJob = viewModelScope.launch(Dispatchers.IO) {
            previousJob?.join()
            if (promo.isEmpty()) {
                batteryRepository.setAppliedPromo(wallet.network, null)
                setPromoState(PromoState.Default)
            } else {
                setPromoState(PromoState.Loading)
                try {
                    val isValid = api.batteryVerifyPurchasePromo(wallet.network, promo)
                    ensureActive()
                    if (isValid) {
                        batteryRepository.setAppliedPromo(wallet.network, promo)
                        setPromoState(PromoState.Applied(promo))
                    } else {
                        throw IllegalStateException("promo code is invalid")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    batteryRepository.setAppliedPromo(wallet.network, null)
                    setPromoState(PromoState.Error)
                }
            }
        }
    }

    private suspend fun setPromoState(state: PromoState) = withContext(Dispatchers.Main) {
        ensureActive()
        _promoStateFlow.value = state
    }

    fun onScreenResumed() {
        if (playFlowOpened && purchaseLaunchFlow.value) {
            launchFallbackJob?.cancel()
            launchFallbackJob = viewModelScope.launch {
                delay(PURCHASE_RESULT_GRACE_MILLIS)
                purchaseLaunchFlow.compareAndSet(expect = true, update = false)
            }
        }
    }

    private suspend fun onPurchaseResult(update: PurchasesUpdated) {
        launchFallbackJob?.cancel()
        val responseCode = update.result.responseCode
        if (update.result.isSuccess && update.purchases.isNotEmpty()) {
            purchaseInProgress.value = true
            purchaseLaunchFlow.value = false
            handlePurchases(update.purchases)
            return
        }
        val wasLaunching = purchaseLaunchFlow.compareAndSet(expect = true, update = false)
        val isFailure = !update.result.isSuccess && responseCode != BillingClient.BillingResponseCode.USER_CANCELED
        if (wasLaunching && isFailure) {
            failureToast(Localization.error)
        }
    }

    private suspend fun handlePurchases(purchases: List<Purchase>) {
        activePurchaseHandlers.incrementAndGet()
        purchaseInProgress.value = true
        try {
            purchasesMutex.withLock {
                verifyPurchases(purchases)
            }
        } finally {
            if (activePurchaseHandlers.decrementAndGet() == 0) {
                purchaseInProgress.value = false
            }
        }
    }

    private suspend fun verifyPurchases(purchases: List<Purchase>) = withContext(Dispatchers.IO) {
        try {
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
                        analytics.eventsTaggedWith(utm).batteryNative.batterySuccess(
                            from = from,
                            type = BatteryNativeType.Fiat,
                            size = batterySize(purchase.products.first()),
                            promo = (_promoStateFlow.value as? PromoState.Applied)?.appliedPromo,
                            jetton = null
                        )
                    }
                    toast(Localization.battery_topped_up)
                    try {
                        billingManager.consumeProduct(purchase.purchaseToken)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        L.e(e)
                    }
                } else {
                    failureToast(Localization.error)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e)
            failureToast(Localization.error)
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

    private fun makePurchase(productId: String, activity: Activity) {
        val product = billingManager.productsFlow.value?.firstOrNull { it.productId == productId }
        if (product == null || iapAvailabilityFlow.value != IapAvailability.ALLOWED) {
            purchaseLaunchFlow.value = false
            return
        }
        analytics.eventsTaggedWith(utm).batteryNative.batterySelect(
            from = from,
            type = BatteryNativeType.Fiat,
            size = batterySize(productId),
            promo = (_promoStateFlow.value as? PromoState.Applied)?.appliedPromo,
            jetton = null
        )

        viewModelScope.launch {
            var launched = false
            try {
                launched = withTimeoutOrNull(PURCHASE_LAUNCH_TIMEOUT_MILLIS) {
                    billingManager.requestPurchase(activity, product)
                } ?: false
                playFlowOpened = launched
                if (!launched && purchaseLaunchFlow.compareAndSet(expect = true, update = false)) {
                    failureToast(Localization.error)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                L.e(e)
                failureToast(Localization.error)
            } finally {
                if (!launched) {
                    purchaseLaunchFlow.value = false
                }
            }
        }
    }

    fun restorePurchases() {
        if (paymentInFlight || !restoreInProgress.compareAndSet(expect = false, update = true)) {
            return
        }
        viewModelScope.launch {
            try {
                val pendingPurchases = withTimeoutOrNull(PURCHASE_LAUNCH_TIMEOUT_MILLIS) {
                    billingManager.restorePurchases()
                }
                when {
                    pendingPurchases == null -> failureToast(Localization.error)
                    pendingPurchases.isNotEmpty() -> handlePurchases(pendingPurchases)
                    else -> toast(Localization.nothing_to_restore)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failureToast(Localization.error)
            } finally {
                restoreInProgress.value = false
            }
        }
    }

    private companion object {
        private const val KEY_INITIAL_ROUTE_OPENED = "initial_route_opened"
        private const val OTHER_PACK_ID = "other"
        private const val STATE_SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
