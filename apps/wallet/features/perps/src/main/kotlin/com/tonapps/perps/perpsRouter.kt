package com.tonapps.perps

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.bus.generated.Events.Perps.PerpsSourceScreen
import com.tonapps.perps.screens.details.PerpsAssetDetailsAction
import com.tonapps.perps.screens.details.PerpsAssetDetailsFeature
import com.tonapps.perps.screens.details.PerpsAssetDetailsScreen
import com.tonapps.perps.screens.details.PerpsAssetDetailsTarget
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.core.LocalIsTopFragment
import com.tonapps.core.helper.navigationDelegate
import com.tonapps.core.loyalty.LOYALTY_DEEPLINK
import com.tonapps.deposit.multicoin.screens.confirm.ConfirmScreen
import com.tonapps.perps.data.PerpsAutoCloseDraft
import com.tonapps.perps.data.PerpsMarketFilter
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.PerpsSizeDirection
import com.tonapps.perps.data.toOrderSide
import com.tonapps.perps.region.PerpsRegionAccess
import com.tonapps.perps.region.PerpsRegionRestrictedSheet
import com.tonapps.perps.region.isBlockedInRestrictedRegion
import com.tonapps.perps.screens.deposit.PerpsDepositFeature
import com.tonapps.perps.screens.deposit.PerpsDepositScreen
import com.tonapps.perps.screens.markets.PerpsMarketsFeature
import com.tonapps.perps.screens.order.PerpsCreateOrderAction
import com.tonapps.perps.screens.order.PerpsCreateOrderFeature
import com.tonapps.perps.screens.order.PerpsCreateOrderScreen
import com.tonapps.perps.screens.order.PerpsLimitAutoClose
import com.tonapps.perps.screens.order.PerpsLimitPriceFeature
import com.tonapps.perps.screens.order.PerpsLimitPriceScreen
import com.tonapps.perps.screens.markets.PerpsMarketsScreen
import com.tonapps.perps.screens.portfolio.PerpsPortfolioAction
import com.tonapps.perps.screens.portfolio.PerpsPortfolioFeature
import com.tonapps.perps.screens.portfolio.PerpsPortfolioScreen
import com.tonapps.perps.screens.margin.PerpsMarginChangeFeature
import com.tonapps.perps.screens.margin.PerpsMarginChangeScreen
import com.tonapps.perps.screens.review.PerpsAutoCloseFix
import com.tonapps.perps.screens.sizechange.PerpsSizeChangeAction
import com.tonapps.perps.screens.sizechange.PerpsSizeChangeFeature
import com.tonapps.perps.screens.sizechange.PerpsSizeChangeScreen
import com.tonapps.perps.screens.review.PerpsReviewAction
import com.tonapps.perps.screens.review.PerpsReviewArgs
import com.tonapps.perps.screens.review.PerpsReviewEvent
import com.tonapps.perps.screens.review.PerpsReviewFeature
import com.tonapps.perps.screens.review.PerpsReviewScreen
import com.tonapps.perps.screens.withdraw.PerpsWithdrawFeature
import com.tonapps.perps.screens.withdraw.PerpsWithdrawScreen
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import ui.moon.MoonNav
import ui.moon.MoonToastHost
import ui.moon.rememberMoonToastHostState
import ui.moon.rememberNestedNavBackStack
import java.math.BigDecimal

@Serializable
sealed interface PerpsRoutes : NavKey {

    @Serializable
    data object Portfolio : PerpsRoutes

    @Serializable
    data class Markets(
        val filter: PerpsMarketFilter = PerpsMarketFilter.ALL,
    ) : PerpsRoutes

    @Serializable
    data class Details(
        val marketIndex: Int,
        val symbol: String? = null,
        val presentAutoClose: Boolean = false,
    ) : PerpsRoutes


    @Serializable
    data class Order(
        val marketIndex: Int,
        val symbol: String,
        val side: PerpsOrderSide,
        val iconUrl: String? = null,
    ) : PerpsRoutes

    @Serializable
    data class MarginChange(
        val marketIndex: Int,
        val symbol: String,
        val side: PerpsOrderSide,
        val add: Boolean,
        val iconUrl: String? = null,
    ) : PerpsRoutes

    @Serializable
    data class SizeChange(
        val marketIndex: Int,
        val symbol: String,
        val side: PerpsOrderSide,
        val direction: PerpsSizeDirection,
        val iconUrl: String? = null,
    ) : PerpsRoutes

    @Serializable
    data class LimitPrice(
        val marketIndex: Int,
        val symbol: String,
        val side: PerpsOrderSide,
        val initialPrice: String? = null,
        val orderIndex: Long? = null,
        val takeProfit: String? = null,
        val stopLoss: String? = null,
        val remainingBase: String? = null,
    ) : PerpsRoutes

    @Serializable
    data class Review(val args: PerpsReviewArgs) : PerpsRoutes

    @Serializable
    data class Deposit(val sourceScreen: PerpsSourceScreen) : PerpsRoutes


    @Serializable
    data object Withdraw : PerpsRoutes

    /** Deposit and withdraw confirmation, on the shared confirm screen. */
    @Serializable
    data class Confirm(val request: ConfirmRequest) : PerpsRoutes
}

private data class LimitPriceResult(val orderIndex: Long?, val price: BigDecimal, val seenBase: BigDecimal?)

private fun PerpsRoutes.LimitPrice.limitAutoClose(): PerpsLimitAutoClose? {
    if (takeProfit == null && stopLoss == null) {
        return null
    }

    return PerpsLimitAutoClose(
        takeProfit = takeProfit?.toBigDecimal(),
        stopLoss = stopLoss?.toBigDecimal(),
        removesPassedLeg = orderIndex != null,
    )
}

private data class ReviewAutoCloseResult(val target: PerpsAutoCloseDraft?, val replace: Boolean)

@Composable
fun PerpsRouter(
    initial: List<PerpsRoutes> = listOf(PerpsRoutes.Portfolio),
    onTrade: (symbol: String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenMarketHistory: (marketIndex: Int) -> Unit,
    onOpenFundingHistory: () -> Unit,
    onClose: () -> Unit,
) {
    val backStack = rememberNestedNavBackStack(initial, onClose)
    val regionAccess = koinInject<PerpsRegionAccess>()
    var regionSheet by rememberSaveable { mutableStateOf(false) }
    var autoCloseFix by remember { mutableStateOf<PerpsAutoCloseFix?>(null) }
    var orderRefusal by remember { mutableStateOf<Int?>(null) }
    var limitPriceResult by remember { mutableStateOf<LimitPriceResult?>(null) }
    var reviewAutoClose by remember { mutableStateOf<ReviewAutoCloseResult?>(null) }
    var depositSent by remember { mutableStateOf(false) }
    val toastHost = rememberMoonToastHostState()
    val scope = rememberCoroutineScope()
    val comingSoonText = stringResource(Localization.perps_coming_soon)
    val inProgressText = stringResource(Localization.perps_operation_in_progress)
    val doneText = stringResource(Localization.done)
    val depositInProgressText = stringResource(Localization.perps_deposit_in_progress)
    val showToast: (String) -> Unit = { text ->
        if (toastHost.currentData == null) {
            scope.launch { toastHost.showToast(text) }
        }
    }
    val closeOrderFlow: () -> Unit = {
        reviewAutoClose = null
        backStack.removeAll {
            it is PerpsRoutes.Review ||
                it is PerpsRoutes.Order ||
                it is PerpsRoutes.LimitPrice ||
                it is PerpsRoutes.SizeChange ||
                it is PerpsRoutes.MarginChange
        }
    }
    val openDeposit: (PerpsSourceScreen) -> Unit = { source ->
        if (regionAccess.isRestricted) {
            regionSheet = true
        } else {
            backStack.add(PerpsRoutes.Deposit(source))
        }
    }

    val screenTracker = koinInject<PerpsScreenTracker>()
    val isTopFragment = LocalIsTopFragment.current
    val topRoute = backStack.lastOrNull() as? PerpsRoutes
    LifecycleResumeEffect(topRoute, isTopFragment) {
        val shown = topRoute?.takeIf { isTopFragment }
        shown?.let(screenTracker::show)
        onPauseOrDispose {
            shown?.let(screenTracker::hide)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MoonNav(backStack = backStack) { key ->
            when (key) {
                is PerpsRoutes.Portfolio -> NavEntry(key) {
                    val feature = koinViewModel<PerpsPortfolioFeature>()
                    LaunchedEffect(depositSent) {
                        if (depositSent) {
                            depositSent = false
                            feature.sendAction(PerpsPortfolioAction.DepositSent)
                        }
                    }
                    PerpsPortfolioScreen(
                        feature = feature,
                        onOpenMarket = { marketIndex, symbol ->
                            backStack.add(PerpsRoutes.Details(marketIndex, symbol))
                        },
                        onOpenExplore = { backStack.add(PerpsRoutes.Markets()) },
                        onDeposit = { openDeposit(PerpsSourceScreen.PerpsHome) },
                        onOpenHistory = onOpenHistory,
                        onOpenFundingHistory = onOpenFundingHistory,
                        onWithdraw = { backStack.add(PerpsRoutes.Withdraw) },
                        onRegionInfo = { regionSheet = true },
                        onBack = { backStack.safeRemoveLastOrNull(key) },
                    )
                }

                is PerpsRoutes.Withdraw -> NavEntry(key) {
                    val feature = koinViewModel<PerpsWithdrawFeature>()
                    PerpsWithdrawScreen(
                        feature = feature,
                        onClose = { backStack.safeRemoveLastOrNull(key) },
                        onReview = { request -> backStack.add(PerpsRoutes.Confirm(request)) },
                    )
                }

                is PerpsRoutes.Deposit -> NavEntry(key) {
                    val feature = koinViewModel<PerpsDepositFeature>(
                        parameters = { parametersOf(key.sourceScreen) },
                    )
                    val context = LocalContext.current
                    PerpsDepositScreen(
                        feature = feature,
                        isOnTop = backStack.lastOrNull() == key,
                        onClose = { backStack.safeRemoveLastOrNull(key) },
                        onReview = { request -> backStack.add(PerpsRoutes.Confirm(request)) },
                        onRegionRestricted = {
                            backStack.removeAll { it is PerpsRoutes.Deposit }
                            regionSheet = true
                        },
                        onAddFunds = { context.navigationDelegate?.onOpenDeposit(DepositFlowFrom.Perps, feature.walletId.value) },
                        onChooseAnotherWallet = { context.navigationDelegate?.onOpenWalletsList(perpsWalletsOnly = true) },
                    )
                }

                is PerpsRoutes.Confirm -> NavEntry(key) {
                    val context = LocalContext.current
                    // The confirm screen and the amount screen before it leave together.
                    val leave = {
                        backStack.removeAll {
                            it is PerpsRoutes.Confirm || it is PerpsRoutes.Deposit || it is PerpsRoutes.Withdraw
                        }
                    }
                    ConfirmScreen(
                        request = key.request,
                        onClose = { leave() },
                        onBack = { backStack.safeRemoveLastOrNull(key) },
                        onSendSuccess = {
                            leave()
                            if (key.request.type !is ConfirmType.PerpetualWithdraw) {
                                if (backStack.lastOrNull() is PerpsRoutes.Portfolio) {
                                    depositSent = true
                                } else {
                                    showToast(depositInProgressText)
                                }
                            }
                        },
                        onTopUp = {},
                        onOpenBattery = { walletId, from ->
                            context.navigationDelegate?.onOpenBattery(walletId = walletId, from = from)
                        },
                    )
                }

                is PerpsRoutes.Markets -> NavEntry(key) {
                    val feature = koinViewModel<PerpsMarketsFeature>(
                        parameters = { parametersOf(key.filter) },
                    )
                    PerpsMarketsScreen(
                        feature = feature,
                        onOpenMarket = { marketIndex, symbol ->
                            backStack.add(PerpsRoutes.Details(marketIndex, symbol))
                        },
                        onBack = { backStack.safeRemoveLastOrNull(key) },
                    )
                }

                is PerpsRoutes.Details -> NavEntry(key) {
                    val feature = koinViewModel<PerpsAssetDetailsFeature>(
                        parameters = { parametersOf(key.marketIndex, key.symbol) },
                    )

                    LaunchedEffect(limitPriceResult) {
                        val result = limitPriceResult ?: return@LaunchedEffect
                        val orderIndex = result.orderIndex ?: return@LaunchedEffect
                        limitPriceResult = null
                        feature.sendAction(PerpsAssetDetailsAction.ModifyLimitOrder(orderIndex, result.price, result.seenBase))
                    }

                    LaunchedEffect(feature) {
                        feature.proceedEvents.collect { target ->
                            when (target) {
                                is PerpsAssetDetailsTarget.Order -> backStack.add(
                                    PerpsRoutes.Order(
                                        marketIndex = key.marketIndex,
                                        symbol = feature.symbol,
                                        side = target.side,
                                        iconUrl = feature.market.value?.iconUrl,
                                    )
                                )

                                is PerpsAssetDetailsTarget.CashOut -> backStack.add(
                                    PerpsRoutes.Review(
                                        PerpsReviewArgs.Close(
                                            marketIndex = key.marketIndex,
                                            symbol = feature.symbol,
                                            side = target.side,
                                            iconUrl = feature.market.value?.iconUrl,
                                        )
                                    )
                                )

                                is PerpsAssetDetailsTarget.SizeChange -> backStack.add(
                                    PerpsRoutes.SizeChange(
                                        marketIndex = key.marketIndex,
                                        symbol = feature.symbol,
                                        side = target.side,
                                        direction = target.direction,
                                        iconUrl = feature.market.value?.iconUrl,
                                    )
                                )

                                is PerpsAssetDetailsTarget.MarginChange -> backStack.add(
                                    PerpsRoutes.MarginChange(
                                        marketIndex = key.marketIndex,
                                        symbol = feature.symbol,
                                        side = target.side,
                                        add = target.add,
                                        iconUrl = feature.market.value?.iconUrl,
                                    )
                                )
                            }
                        }
                    }

                    val proceed: (PerpsAssetDetailsTarget) -> Unit = { target ->
                        if (target.isBlockedInRestrictedRegion && regionAccess.isRestricted) {
                            regionSheet = true
                        } else {
                            feature.sendAction(PerpsAssetDetailsAction.Proceed(target))
                        }
                    }

                    PerpsAssetDetailsScreen(
                        feature = feature,
                        presentAutoClose = key.presentAutoClose,
                        onOpenOrder = { side -> proceed(PerpsAssetDetailsTarget.Order(side)) },
                        onCashOut = { side -> proceed(PerpsAssetDetailsTarget.CashOut(side)) },
                        onOpenSizeChange = { side, direction ->
                            proceed(PerpsAssetDetailsTarget.SizeChange(side, direction))
                        },
                        onOpenMarginChange = { side, add ->
                            proceed(PerpsAssetDetailsTarget.MarginChange(side, add))
                        },
                        onLimitOrderEdit = { order ->
                            if (regionAccess.isRestricted) {
                                regionSheet = true
                            } else {
                                backStack.add(
                                    PerpsRoutes.LimitPrice(
                                        marketIndex = key.marketIndex,
                                        symbol = feature.symbol,
                                        side = order.side.toOrderSide(),
                                        initialPrice = order.limitPrice?.toPlainString(),
                                        orderIndex = order.orderIndex,
                                        takeProfit = order.takeProfit?.triggerPrice?.toPlainString(),
                                        stopLoss = order.stopLoss?.triggerPrice?.toPlainString(),
                                        remainingBase = order.remainingBase?.toPlainString(),
                                    )
                                )
                            }
                        },
                        onOpenHistory = { onOpenMarketHistory(key.marketIndex) },
                        onBack = { backStack.safeRemoveLastOrNull(key) },
                    )
                }

                is PerpsRoutes.Order -> NavEntry(key) {
                    val feature = koinViewModel<PerpsCreateOrderFeature>(
                        parameters = { parametersOf(key.marketIndex, key.symbol, key.side) },
                    )

                    LaunchedEffect(limitPriceResult) {
                        val result = limitPriceResult?.takeIf { it.orderIndex == null } ?: return@LaunchedEffect
                        limitPriceResult = null
                        feature.sendAction(PerpsCreateOrderAction.SetLimitPrice(result.price))
                    }

                    LaunchedEffect(reviewAutoClose) {
                        val result = reviewAutoClose ?: return@LaunchedEffect
                        reviewAutoClose = null
                        feature.sendAction(PerpsCreateOrderAction.SetAutoClose(result.target))
                    }

                    PerpsCreateOrderScreen(
                        feature = feature,
                        autoCloseFix = autoCloseFix,
                        onAutoCloseFixConsumed = { autoCloseFix = null },
                        refusal = orderRefusal,
                        onRefusalConsumed = { orderRefusal = null },
                        onClose = { backStack.safeRemoveLastOrNull(key) },
                        onReview = { autoClose ->
                            backStack.add(
                                PerpsRoutes.Review(
                                    PerpsReviewArgs.Open(
                                        marketIndex = key.marketIndex,
                                        symbol = key.symbol,
                                        side = key.side,
                                        marginQuote = feature.marginQuote.value.toPlainString(),
                                        leverage = feature.leverage.value,
                                        takeProfit = autoClose?.takeProfit?.toPlainString(),
                                        stopLoss = autoClose?.stopLoss?.toPlainString(),
                                        takeProfitRoi = autoClose?.takeProfitRoi?.toPlainString(),
                                        stopLossRoi = autoClose?.stopLossRoi?.toPlainString(),
                                        limitPrice = feature.limitPrice.value?.toPlainString(),
                                        autoCloseEditable = feature.autoCloseAvailable.value,
                                        iconUrl = key.iconUrl,
                                    )
                                )
                            )
                        },
                        onDeposit = { openDeposit(PerpsSourceScreen.OpenPosition) },
                        onSetLimitPrice = {
                            val autoClose = feature.autoClose.value
                            backStack.add(
                                PerpsRoutes.LimitPrice(
                                    marketIndex = key.marketIndex,
                                    symbol = key.symbol,
                                    side = key.side,
                                    initialPrice = feature.limitPrice.value?.toPlainString(),
                                    takeProfit = autoClose?.takeProfit
                                        ?.takeIf { autoClose.takeProfitRoi == null }
                                        ?.toPlainString(),
                                    stopLoss = autoClose?.stopLoss
                                        ?.takeIf { autoClose.stopLossRoi == null }
                                        ?.toPlainString(),
                                )
                            )
                        },
                    )
                }

                is PerpsRoutes.LimitPrice -> NavEntry(key) {
                    val feature = koinViewModel<PerpsLimitPriceFeature>(
                        parameters = {
                            parametersOf(
                                key.marketIndex,
                                key.symbol,
                                key.side,
                                key.initialPrice?.toBigDecimal(),
                                key.limitAutoClose(),
                            )
                        },
                    )
                    PerpsLimitPriceScreen(
                        feature = feature,
                        onClose = { backStack.safeRemoveLastOrNull(key) },
                        onBack = { backStack.safeRemoveLastOrNull(key) },
                        onSet = { price ->
                            if (key.orderIndex != null && key.initialPrice?.toBigDecimal()?.compareTo(price) == 0) {
                                showToast(doneText)
                            } else {
                                limitPriceResult = LimitPriceResult(key.orderIndex, price, key.remainingBase?.toBigDecimal())
                            }
                            backStack.safeRemoveLastOrNull(key)
                        },
                    )
                }

                is PerpsRoutes.SizeChange -> NavEntry(key) {
                    val feature = koinViewModel<PerpsSizeChangeFeature>(
                        parameters = {
                            parametersOf(key.marketIndex, key.symbol, key.side, key.direction)
                        },
                    )

                    LaunchedEffect(reviewAutoClose) {
                        val result = reviewAutoClose ?: return@LaunchedEffect
                        reviewAutoClose = null
                        feature.sendAction(
                            if (result.replace) {
                                PerpsSizeChangeAction.ReplaceAutoClose(result.target)
                            } else {
                                PerpsSizeChangeAction.SetAutoClose(result.target)
                            }
                        )
                    }

                    PerpsSizeChangeScreen(
                        feature = feature,
                        refusal = orderRefusal,
                        onRefusalConsumed = { orderRefusal = null },
                        autoCloseFix = autoCloseFix,
                        onAutoCloseFixConsumed = { autoCloseFix = null },
                        onReview = { args ->
                            backStack.add(PerpsRoutes.Review(args.copy(iconUrl = key.iconUrl)))
                        },
                        onDeposit = { openDeposit(PerpsSourceScreen.AddToPosition) },
                        onClose = { backStack.safeRemoveLastOrNull(key) },
                    )
                }

                is PerpsRoutes.MarginChange -> NavEntry(key) {
                    val feature = koinViewModel<PerpsMarginChangeFeature>(
                        parameters = {
                            parametersOf(key.marketIndex, key.symbol, key.side, key.add)
                        },
                    )
                    PerpsMarginChangeScreen(
                        feature = feature,
                        refusal = orderRefusal,
                        onRefusalConsumed = { orderRefusal = null },
                        onReview = { args ->
                            backStack.add(PerpsRoutes.Review(args.copy(iconUrl = key.iconUrl)))
                        },
                        onDeposit = { openDeposit(PerpsSourceScreen.AddMargin) },
                        onClose = { backStack.safeRemoveLastOrNull(key) },
                    )
                }

                is PerpsRoutes.Review -> NavEntry(key) {
                    val feature = koinViewModel<PerpsReviewFeature>(
                        parameters = { parametersOf(key.args) },
                    )
                    val context = LocalContext.current

                    LaunchedEffect(feature) {
                        feature.sendAction(PerpsReviewAction.Attach)
                    }
                    LaunchedEffect(feature) {
                        feature.events.collect { event ->
                            when (event) {
                                PerpsReviewEvent.Submitted -> closeOrderFlow()

                                PerpsReviewEvent.Cancelled -> backStack.safeRemoveLastOrNull(key)

                                PerpsReviewEvent.ComingSoon -> {
                                    showToast(comingSoonText)
                                    backStack.safeRemoveLastOrNull(key)
                                }

                                PerpsReviewEvent.InProgress -> {
                                    showToast(inProgressText)
                                    backStack.safeRemoveLastOrNull(key)
                                }

                                is PerpsReviewEvent.UpdateAutoClose -> {
                                    autoCloseFix = event.fix
                                    backStack.safeRemoveLastOrNull(key)
                                }

                                is PerpsReviewEvent.PlanRefused -> {
                                    if (key.args.hasForm) {
                                        orderRefusal = event.text
                                    } else {
                                        showToast(context.getString(event.text))
                                    }
                                    backStack.safeRemoveLastOrNull(key)
                                }

                                is PerpsReviewEvent.SubmitFailed -> {
                                    showToast(context.getString(event.text))
                                    closeOrderFlow()
                                }
                            }
                        }
                    }

                    PerpsReviewScreen(
                        feature = feature,
                        onBack = { backStack.safeRemoveLastOrNull(key) },
                        onClose = closeOrderFlow,
                        onAutoCloseChanged = { reviewAutoClose = ReviewAutoCloseResult(it, replace = false) },
                        onAutoCloseDropped = { reviewAutoClose = ReviewAutoCloseResult(it, replace = true) },
                        onOpenLoyalty = { context.navigationDelegate?.onOpenLink(LOYALTY_DEEPLINK) },
                    )
                }

                else -> throw IllegalStateException("Unknown key: $key")
            }
        }
        MoonToastHost(toastHost)
        if (regionSheet) {
            PerpsRegionRestrictedSheet(onDismiss = { regionSheet = false })
        }
    }
}
