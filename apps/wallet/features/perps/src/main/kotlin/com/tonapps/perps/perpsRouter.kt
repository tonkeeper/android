package com.tonapps.perps

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.perps.screens.details.PerpsAssetDetailsAction
import com.tonapps.perps.screens.details.PerpsAssetDetailsFeature
import com.tonapps.perps.screens.details.PerpsAssetDetailsScreen
import com.tonapps.perps.screens.details.PerpsAssetDetailsTarget
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.core.LocalIsTopFragment
import com.tonapps.core.helper.navigationDelegate
import com.tonapps.deposit.multicoin.screens.confirm.ConfirmScreen
import com.tonapps.perps.data.PerpsMarketFilter
import com.tonapps.perps.funding.PerpsFundingAssetId
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.PerpsSizeDirection
import com.tonapps.perps.data.toOrderSide
import com.tonapps.perps.screens.deposit.PerpsDepositFeature
import com.tonapps.perps.screens.deposit.PerpsDepositScreen
import com.tonapps.perps.screens.markets.PerpsMarketsFeature
import com.tonapps.perps.screens.order.PerpsCreateOrderAction
import com.tonapps.perps.screens.order.PerpsCreateOrderFeature
import com.tonapps.perps.screens.order.PerpsCreateOrderScreen
import com.tonapps.perps.screens.order.PerpsLimitPriceFeature
import com.tonapps.perps.screens.order.PerpsLimitPriceScreen
import com.tonapps.perps.screens.markets.PerpsMarketsScreen
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
import com.tonapps.perps.trade.PerpsAutoCloseEdit
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
    ) : PerpsRoutes

    @Serializable
    data class Review(val args: PerpsReviewArgs) : PerpsRoutes

    @Serializable
    data object Deposit : PerpsRoutes


    @Serializable
    data object Withdraw : PerpsRoutes

    /** Deposit and withdraw confirmation, on the shared confirm screen. */
    @Serializable
    data class Confirm(val request: ConfirmRequest) : PerpsRoutes
}

private data class LimitPriceResult(val orderIndex: Long?, val price: BigDecimal)

@Composable
fun PerpsRouter(
    initial: List<PerpsRoutes> = listOf(PerpsRoutes.Portfolio),
    onTrade: (symbol: String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenMarketHistory: (marketIndex: Int) -> Unit,
    onCloseToHistory: (awaitsFundingActivity: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val backStack = rememberNestedNavBackStack(initial, onClose)
    var autoCloseFix by remember { mutableStateOf<PerpsAutoCloseFix?>(null) }
    var orderRefusal by remember { mutableStateOf<Int?>(null) }
    var limitPriceResult by remember { mutableStateOf<LimitPriceResult?>(null) }
    var reviewAutoClose by remember { mutableStateOf<PerpsAutoCloseEdit.Set?>(null) }
    val toastHost = rememberMoonToastHostState()
    val scope = rememberCoroutineScope()
    val comingSoonText = stringResource(Localization.perps_coming_soon)
    val inProgressText = stringResource(Localization.perps_operation_in_progress)
    val doneText = stringResource(Localization.done)
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
                    PerpsPortfolioScreen(
                        feature = feature,
                        onOpenMarket = { marketIndex, symbol ->
                            backStack.add(PerpsRoutes.Details(marketIndex, symbol))
                        },
                        onOpenExplore = { backStack.add(PerpsRoutes.Markets()) },
                        onDeposit = { backStack.add(PerpsRoutes.Deposit) },
                        onOpenHistory = onOpenHistory,
                        onWithdraw = { backStack.add(PerpsRoutes.Withdraw) },
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
                    val feature = koinViewModel<PerpsDepositFeature>()
                    val context = LocalContext.current
                    PerpsDepositScreen(
                        feature = feature,
                        onClose = { backStack.safeRemoveLastOrNull(key) },
                        onReview = { request -> backStack.add(PerpsRoutes.Confirm(request)) },
                        onAddFunds = { context.navigationDelegate?.onOpenDeposit(DepositFlowFrom.Perps) },
                    )
                }

                is PerpsRoutes.Confirm -> NavEntry(key) {
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
                            if (key.request.type is ConfirmType.PerpetualWithdraw) {
                                onCloseToHistory(key.request.data.assetId.equals(PerpsFundingAssetId.USDC_ETHEREUM, ignoreCase = true))
                            } else {
                                leave()
                            }
                        },
                        onTopUp = {},
                        onOpenBattery = { _, _ -> },
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
                    val context = LocalContext.current

                    LaunchedEffect(limitPriceResult) {
                        val result = limitPriceResult ?: return@LaunchedEffect
                        val orderIndex = result.orderIndex ?: return@LaunchedEffect
                        limitPriceResult = null
                        feature.sendAction(PerpsAssetDetailsAction.ModifyLimitOrder(context, orderIndex, result.price))
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

                    PerpsAssetDetailsScreen(
                        feature = feature,
                        presentAutoClose = key.presentAutoClose,
                        onOpenOrder = { side ->
                            feature.sendAction(PerpsAssetDetailsAction.Proceed(context, PerpsAssetDetailsTarget.Order(side)))
                        },
                        onCashOut = { side ->
                            feature.sendAction(PerpsAssetDetailsAction.Proceed(context, PerpsAssetDetailsTarget.CashOut(side)))
                        },
                        onOpenSizeChange = { side, direction ->
                            feature.sendAction(
                                PerpsAssetDetailsAction.Proceed(context, PerpsAssetDetailsTarget.SizeChange(side, direction))
                            )
                        },
                        onOpenMarginChange = { side, add ->
                            feature.sendAction(
                                PerpsAssetDetailsAction.Proceed(context, PerpsAssetDetailsTarget.MarginChange(side, add))
                            )
                        },
                        onLimitOrderEdit = { order ->
                            backStack.add(
                                PerpsRoutes.LimitPrice(
                                    marketIndex = key.marketIndex,
                                    symbol = feature.symbol,
                                    side = order.side.toOrderSide(),
                                    initialPrice = order.limitPrice?.toPlainString(),
                                    orderIndex = order.orderIndex,
                                )
                            )
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
                        onReview = {
                            backStack.add(
                                PerpsRoutes.Review(
                                    PerpsReviewArgs.Open(
                                        marketIndex = key.marketIndex,
                                        symbol = key.symbol,
                                        side = key.side,
                                        marginQuote = feature.marginQuote.value.toPlainString(),
                                        leverage = feature.leverage.value,
                                        takeProfit = feature.autoClose.value?.takeProfit?.toPlainString(),
                                        stopLoss = feature.autoClose.value?.stopLoss?.toPlainString(),
                                        takeProfitRoi = feature.autoClose.value?.takeProfitRoi?.toPlainString(),
                                        stopLossRoi = feature.autoClose.value?.stopLossRoi?.toPlainString(),
                                        limitPrice = feature.limitPrice.value?.toPlainString(),
                                        autoCloseEditable = feature.autoCloseAvailable.value,
                                        iconUrl = key.iconUrl,
                                    )
                                )
                            )
                        },
                        onDeposit = { backStack.add(PerpsRoutes.Deposit) },
                        onSetLimitPrice = {
                            backStack.add(
                                PerpsRoutes.LimitPrice(
                                    marketIndex = key.marketIndex,
                                    symbol = key.symbol,
                                    side = key.side,
                                    initialPrice = feature.limitPrice.value?.toPlainString(),
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
                                limitPriceResult = LimitPriceResult(key.orderIndex, price)
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
                        feature.sendAction(PerpsSizeChangeAction.SetAutoClose(result.target))
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
                        onDeposit = { backStack.add(PerpsRoutes.Deposit) },
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
                        onDeposit = { backStack.add(PerpsRoutes.Deposit) },
                        onClose = { backStack.safeRemoveLastOrNull(key) },
                    )
                }

                is PerpsRoutes.Review -> NavEntry(key) {
                    val feature = koinViewModel<PerpsReviewFeature>(
                        parameters = { parametersOf(key.args) },
                    )
                    val context = LocalContext.current

                    LaunchedEffect(feature) {
                        feature.sendAction(PerpsReviewAction.Attach(context))
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
                        onAutoCloseChanged = { reviewAutoClose = PerpsAutoCloseEdit.Set(it) },
                    )
                }

                else -> throw IllegalStateException("Unknown key: $key")
            }
        }
        MoonToastHost(toastHost)
    }
}
