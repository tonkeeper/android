package com.tonapps.perps.screens.order

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.paging.collectAsStateWorkaround
import com.tonapps.perps.data.PERPS_EMPTY_VALUE
import com.tonapps.perps.data.PerpsAutoCloseDraft
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsMarketRules
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.formatAutoClosePrice
import com.tonapps.perps.data.formatUsd
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.perps.data.formatUsdPrice
import com.tonapps.perps.screens.review.PerpsAutoCloseFix
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.RStr
import ui.components.moon.MoonBadgeButton
import ui.components.moon.MoonChevronRight
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLargeItemSubtitle
import ui.components.moon.MoonSmallItemTitle
import ui.components.moon.MoonTopAppBar
import ui.components.moon.MoonTopAppBarSubtitle
import ui.components.moon.MoonTopAppBarTitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.container.MoonScaffold
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit
import ui.utils.uppercased
import java.math.BigDecimal

@Composable
fun PerpsCreateOrderScreen(
    feature: PerpsCreateOrderFeature,
    autoCloseFix: PerpsAutoCloseFix?,
    onAutoCloseFixConsumed: () -> Unit,
    refusal: Int?,
    onRefusalConsumed: () -> Unit,
    onClose: () -> Unit,
    onReview: () -> Unit,
    onDeposit: () -> Unit,
    onSetLimitPrice: () -> Unit,
) {
    val mark by feature.mark.collectAsStateWorkaround()
    val orderType by feature.orderType.collectAsStateWorkaround()
    val limitPrice by feature.limitPrice.collectAsStateWorkaround()
    val rules by feature.rules.collectAsStateWorkaround()
    val amount by feature.amount.collectAsStateWorkaround()
    val inputInSize by feature.inputInSize.collectAsStateWorkaround()
    val amountFractionDigits by feature.amountFractionDigits.collectAsStateWorkaround()
    val leverage by feature.leverage.collectAsStateWorkaround()
    val autoClose by feature.autoClose.collectAsStateWorkaround()
    val autoCloseWarning by feature.autoCloseWarning.collectAsStateWorkaround()
    val draftLiquidation by feature.draftLiquidation.collectAsStateWorkaround()
    val autoCloseAvailable by feature.autoCloseAvailable.collectAsStateWorkaround()
    val marginQuote by feature.marginQuote.collectAsStateWorkaround()
    val sizeQuote by feature.sizeQuote.collectAsStateWorkaround()
    val sizeBase by feature.sizeBase.collectAsStateWorkaround()
    val availableFormat by feature.availableFormat.collectAsStateWorkaround()
    val issue by feature.issue.collectAsStateWorkaround()
    val canReview by feature.canReview.collectAsStateWorkaround()
    val refusalText by feature.refusal.collectAsStateWorkaround()

    LaunchedEffect(refusal) {
        if (refusal != null) {
            feature.sendAction(PerpsCreateOrderAction.ShowRefusal(refusal))
            onRefusalConsumed()
        }
    }

    PerpsCreateOrderContent(
        side = feature.side,
        symbol = feature.symbol,
        mark = mark,
        orderType = orderType,
        limitPrice = limitPrice,
        rules = rules,
        amount = amount,
        inputInSize = inputInSize,
        amountFractionDigits = amountFractionDigits,
        leverage = leverage,
        autoClose = autoClose,
        autoCloseWarning = autoCloseWarning,
        draftLiquidation = draftLiquidation,
        autoCloseAvailable = autoCloseAvailable,
        marginQuote = marginQuote,
        sizeQuote = sizeQuote,
        sizeBase = sizeBase,
        availableFormat = availableFormat,
        issue = issue,
        refusal = refusalText,
        canReview = canReview,
        autoCloseFix = autoCloseFix,
        onAutoCloseFixConsumed = onAutoCloseFixConsumed,
        onAction = feature::sendAction,
        onClose = onClose,
        onReview = onReview,
        onDeposit = onDeposit,
        onSetLimitPrice = onSetLimitPrice,
    )
}

@Composable
private fun PerpsCreateOrderContent(
    side: PerpsOrderSide,
    symbol: String,
    mark: BigDecimal?,
    orderType: PerpsOrderType,
    limitPrice: BigDecimal?,
    rules: PerpsMarketRules?,
    amount: String,
    inputInSize: Boolean,
    amountFractionDigits: Int,
    leverage: Int,
    autoClose: PerpsAutoCloseDraft?,
    autoCloseWarning: PerpsAutoCloseIssue? = null,
    draftLiquidation: BigDecimal? = null,
    autoCloseAvailable: Boolean,
    marginQuote: BigDecimal,
    sizeQuote: BigDecimal,
    sizeBase: BigDecimal?,
    availableFormat: String,
    issue: PerpsOrderIssue?,
    refusal: Int?,
    canReview: Boolean,
    autoCloseFix: PerpsAutoCloseFix?,
    onAutoCloseFixConsumed: () -> Unit,
    onAction: (PerpsCreateOrderAction) -> Unit,
    onClose: () -> Unit,
    onReview: () -> Unit,
    onDeposit: () -> Unit,
    onSetLimitPrice: () -> Unit,
) {
    var showOrderType by remember { mutableStateOf(false) }
    var showLeverage by remember { mutableStateOf(false) }
    var showAutoClose by remember { mutableStateOf(false) }

    val priceDecimals = rules?.priceDecimals ?: DEFAULT_PRICE_DECIMALS
    val markText = mark?.let { formatUsd(it) } ?: PERPS_EMPTY_VALUE
    val sideText = stringResource(
        if (side == PerpsOrderSide.LONG) {
            Localization.perps_long
        } else {
            Localization.perps_short
        }
    )
    val orderTypeText = stringResource(
        if (orderType == PerpsOrderType.Market) {
            Localization.perps_order_type_market
        } else {
            Localization.perps_order_type_limit
        }
    )
    val priceText = if (orderType == PerpsOrderType.Limit && limitPrice != null) {
        stringResource(
            Localization.perps_order_price,
            formatUsdPrice(limitPrice.toDouble(), priceDecimals),
        )
    } else {
        stringResource(Localization.perps_order_current_price, markText)
    }
    val autoCloseText: CharSequence = if (autoClose != null) {
        perpsAutoCloseSummary(
            takeProfit = autoClose.takeProfit?.let { formatAutoClosePrice(it, priceDecimals) },
            stopLoss = autoClose.stopLoss?.let { formatAutoClosePrice(it, priceDecimals) },
        )
    } else {
        stringResource(Localization.perps_order_set)
    }
    val pillText = if (inputInSize) {
        stringResource(Localization.perps_order_margin, formatUsdAmount(marginQuote))
    } else {
        stringResource(Localization.perps_order_size, formatUsdAmount(sizeQuote))
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(autoCloseFix) {
        if (autoCloseFix != null) {
            keyboardController?.hide()
            showAutoClose = true
            onAction(PerpsCreateOrderAction.SetAutoCloseSheetVisible(true))
        }
    }

    MoonScaffold(
        modifier = Modifier.windowInsetsPadding(
            WindowInsets.ime.union(WindowInsets.navigationBars).union(WindowInsets.statusBars),
        ),
        topBar = {
            MoonTopAppBar(
                title = {
                    MoonTopAppBarTitle(
                        text = stringResource(Localization.perps_order_side_title, sideText, symbol),
                    )
                },
                onTitleClick = {
                    keyboardController?.hide()
                    showOrderType = true
                },
                subtitle = {
                    Row(
                        modifier = if (orderType == PerpsOrderType.Limit) {
                            Modifier.clickable {
                                keyboardController?.hide()
                                onSetLimitPrice()
                            }
                        } else {
                            Modifier
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MoonTopAppBarSubtitle(
                            text = stringResource(
                                Localization.perps_order_price_market,
                                priceText,
                                orderTypeText,
                            ),
                        )
                        Spacer(Modifier.width(4.dp))
                        MoonItemIcon(
                            painter = painterResource(UIKitIcon.ic_switch_16),
                            size = 12.dp,
                            color = UIKit.colorScheme.icon.secondary,
                        )
                    }
                },
                actionIconRes = UIKitIcon.ic_close_16,
                onActionClick = onClose,
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .nestedScroll(rememberNestedScrollInteropConnection())
            ) {
                Spacer(Modifier.height(8.dp))

                PerpsAmountInputCell(
                    amount = amount,
                    pillText = pillText,
                    assetSymbol = symbol.takeIf { inputInSize },
                    maxFractionDigits = amountFractionDigits,
                    onAmountChange = { onAction(PerpsCreateOrderAction.SetAmount(it)) },
                    onSwap = { onAction(PerpsCreateOrderAction.ToggleInputMode) },
                    onDone = {
                        if (canReview) {
                            onReview()
                        }
                    },
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MoonBadgeButton(
                        content = { MoonSmallItemTitle(stringResource(RStr.max).uppercased()) },
                        onClick = { onAction(PerpsCreateOrderAction.Max) },
                    )

                    OrderIssueRow(
                        issue = issue,
                        availableFormat = availableFormat,
                        onRetry = { onAction(PerpsCreateOrderAction.Retry) },
                        onDeposit = onDeposit,
                        onSetLimitPrice = {
                            keyboardController?.hide()
                            onSetLimitPrice()
                        },
                    )
                }

                Spacer(Modifier.height(16.dp))

                MoonBundleCell {
                    Column {
                        MoonPropertyBigCell(
                            title = stringResource(Localization.perps_order_leverage),
                            value = stringResource(Localization.perps_leverage_value_pattern, leverage),
                            valueDescription = null,
                            onClick = if (rules != null) {
                                {
                                    keyboardController?.hide()
                                    showLeverage = true
                                }
                            } else {
                                null
                            },
                            valuePostfix = { MoonChevronRight() },
                        )

                        MoonItemDivider()

                        MoonPropertyBigCell(
                            title = {
                                MoonLargeItemSubtitle(
                                    text = stringResource(Localization.perps_order_auto_close),
                                )
                            },
                            content = {
                                MoonItemTitle(
                                    text = autoCloseText,
                                    color = if (autoClose != null) {
                                        UIKit.colorScheme.text.primary
                                    } else {
                                        UIKit.colorScheme.text.accent
                                    },
                                )
                            },
                            onClick = if (mark != null && rules != null && autoCloseAvailable) {
                                {
                                    keyboardController?.hide()
                                    showAutoClose = true
                                    onAction(PerpsCreateOrderAction.SetAutoCloseSheetVisible(true))
                                }
                            } else {
                                null
                            },
                            valuePostfix = { MoonChevronRight() },
                        )
                    }
                }

                val warning = when {
                    autoCloseWarning != null -> perpsAutoCloseIssueText(autoCloseWarning, priceDecimals)
                    refusal != null -> stringResource(refusal)
                    else -> null
                }
                if (warning != null) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp),
                        text = warning,
                        style = UIKit.typography.body2,
                        color = UIKit.colorScheme.accent.red,
                    )
                }
            }

            MoonBottomButtonCell(
                text = stringResource(Localization.perps_order_review),
                enabled = canReview,
            ) {
                onReview()
            }
        }
    }

    if (showOrderType) {
        PerpsOrderTypeDialog(
            selected = orderType,
            onSelect = { type ->
                if (type == PerpsOrderType.Limit && limitPrice == null) {
                    onSetLimitPrice()
                } else {
                    onAction(PerpsCreateOrderAction.SetOrderType(type))
                }
            },
            onClose = { showOrderType = false },
        )
    }

    if (showLeverage && rules != null) {
        PerpsLeverageDialog(
            initialLeverage = leverage,
            rules = rules,
            side = side,
            markPrice = mark,
            entryPrice = limitPrice ?: mark,
            onApply = { onAction(PerpsCreateOrderAction.SetLeverage(it)) },
            onClose = { showLeverage = false },
        )
    }

    if (showAutoClose && mark != null && rules != null) {
        PerpsAutoCloseDialog(
            priceDecimals = rules.priceDecimals,
            side = side,
            bounds = PerpsAutoCloseBounds(
                entryPrice = limitPrice ?: mark,
                leverage = BigDecimal(leverage),
                currentPrice = limitPrice ?: mark,
                liquidationPrice = draftLiquidation,
            ),
            initial = autoClose.withFix(autoCloseFix),
            estimate = autoCloseEstimate(sizeBase, limitPrice ?: mark),
            onSet = { onAction(PerpsCreateOrderAction.SetAutoClose(it)) },
            onClose = {
                showAutoClose = false
                onAction(PerpsCreateOrderAction.SetAutoCloseSheetVisible(false))
                onAutoCloseFixConsumed()
            },
            limitPrice = limitPrice,
        )
    }
}

@Composable
private fun OrderIssueRow(
    issue: PerpsOrderIssue?,
    availableFormat: String,
    onRetry: () -> Unit,
    onDeposit: () -> Unit,
    onSetLimitPrice: () -> Unit,
) {
    when (issue) {
        null -> IssueRow(
            text = stringResource(Localization.perps_available, availableFormat),
            color = UIKit.colorScheme.text.secondary,
            onDeposit = onDeposit,
        )

        PerpsOrderIssue.Loading -> Unit

        PerpsOrderIssue.LimitPriceMissing -> Text(
            modifier = Modifier.clickable(onClick = onSetLimitPrice),
            text = stringResource(Localization.perps_limit_price_title),
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.accent,
        )

        is PerpsOrderIssue.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
            MoonItemSubtitle(
                text = stringResource(
                    if (issue.error == PerpsError.ServiceUnavailable) {
                        Localization.perps_service_unavailable
                    } else {
                        Localization.perps_load_failed
                    }
                ),
                color = UIKit.colorScheme.accent.red,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                modifier = Modifier.clickable(onClick = onRetry),
                text = stringResource(Localization.perps_retry),
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.text.accent,
            )
        }

        PerpsOrderIssue.TradingDisabled -> MoonItemSubtitle(
            text = stringResource(Localization.perps_order_trading_disabled),
            color = UIKit.colorScheme.accent.red,
        )

        is PerpsOrderIssue.BelowMinMargin -> {
            val text = stringResource(Localization.perps_order_min_margin, formatUsdAmount(issue.minMargin))
            if (issue.canDeposit) {
                IssueRow(
                    text = text,
                    color = UIKit.colorScheme.accent.red,
                    onDeposit = onDeposit,
                )
            } else {
                MoonItemSubtitle(
                    text = text,
                    color = UIKit.colorScheme.accent.red,
                )
            }
        }

        PerpsOrderIssue.InsufficientBalance -> IssueRow(
            text = stringResource(Localization.perps_order_insufficient_balance),
            color = UIKit.colorScheme.accent.red,
            onDeposit = onDeposit,
        )
    }
}

@Composable
private fun IssueRow(
    text: String,
    color: Color,
    onDeposit: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MoonItemSubtitle(text = text, color = color)
        Spacer(Modifier.width(4.dp))
        MoonItemSubtitle(
            text = stringResource(Localization.perps_order_separator),
            color = UIKit.colorScheme.text.secondary,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            modifier = Modifier.clickable(onClick = onDeposit),
            text = stringResource(Localization.perps_deposit),
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.accent,
        )
    }
}

internal fun autoCloseEstimate(sizeBase: BigDecimal?, referencePrice: BigDecimal?): PerpsAutoCloseEstimate? {
    if (sizeBase == null || sizeBase.signum() <= 0 || referencePrice == null || referencePrice.signum() <= 0) {
        return null
    }

    return PerpsAutoCloseEstimate(sizeBase = sizeBase, referencePrice = referencePrice)
}

private const val DEFAULT_PRICE_DECIMALS = 2

internal val previewRules = PerpsMarketRules(
    maxLeverage = 40,
    priceDecimals = 2,
    sizeDecimals = 6,
    tickSize = BigDecimal("0.1"),
    stepSize = BigDecimal("0.00001"),
    minSizeBase = BigDecimal("0.0001"),
    minSizeQuote = BigDecimal("10"),
    takerFeePct = BigDecimal("0.05"),
    initialMarginBps = 500,
    maintenanceMarginBps = 250,
    markPrice = BigDecimal("66141.70"),
    tradable = true,
)

@Preview
@Composable
private fun PerpsCreateOrderScreenPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsCreateOrderContent(
            side = PerpsOrderSide.LONG,
            symbol = "BTC",
            mark = previewRules.markPrice,
            orderType = PerpsOrderType.Market,
            limitPrice = null,
            rules = previewRules,
            amount = "100",
            inputInSize = false,
            amountFractionDigits = 2,
            leverage = 20,
            autoClose = null,
            autoCloseAvailable = true,
            marginQuote = BigDecimal("100"),
            sizeQuote = BigDecimal("2000"),
            sizeBase = BigDecimal("0.03"),
            availableFormat = "$712.56",
            issue = null,
            refusal = null,
            canReview = true,
            autoCloseFix = null,
            onAutoCloseFixConsumed = {},
            onAction = {},
            onClose = {},
            onReview = {},
            onDeposit = {},
            onSetLimitPrice = {},
        )
    }
}

@Preview
@Composable
private fun PerpsCreateOrderScreenAssetInputPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsCreateOrderContent(
            side = PerpsOrderSide.SHORT,
            symbol = "BTC",
            mark = previewRules.markPrice,
            orderType = PerpsOrderType.Market,
            limitPrice = null,
            rules = previewRules,
            amount = "0.03",
            inputInSize = true,
            amountFractionDigits = previewRules.sizeDecimals,
            leverage = 20,
            autoClose = null,
            autoCloseAvailable = true,
            marginQuote = BigDecimal("99.21"),
            sizeQuote = BigDecimal("1984.25"),
            sizeBase = BigDecimal("0.03"),
            availableFormat = "$712.56",
            issue = null,
            refusal = null,
            canReview = true,
            autoCloseFix = null,
            onAutoCloseFixConsumed = {},
            onAction = {},
            onClose = {},
            onReview = {},
            onDeposit = {},
            onSetLimitPrice = {},
        )
    }
}

@Preview
@Composable
private fun PerpsCreateOrderScreenInsufficientPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsCreateOrderContent(
            side = PerpsOrderSide.SHORT,
            symbol = "BTC",
            mark = previewRules.markPrice,
            orderType = PerpsOrderType.Limit,
            limitPrice = BigDecimal("64200"),
            rules = previewRules,
            amount = "1000",
            inputInSize = false,
            amountFractionDigits = 2,
            leverage = 20,
            autoClose = PerpsAutoCloseDraft(
                takeProfit = BigDecimal("72755.87"),
                stopLoss = BigDecimal("62834.61"),
            ),
            autoCloseAvailable = true,
            marginQuote = BigDecimal("1000"),
            sizeQuote = BigDecimal("20000"),
            sizeBase = BigDecimal("0.3"),
            availableFormat = "$712.56",
            issue = PerpsOrderIssue.InsufficientBalance,
            refusal = null,
            canReview = false,
            autoCloseFix = null,
            onAutoCloseFixConsumed = {},
            onAction = {},
            onClose = {},
            onReview = {},
            onDeposit = {},
            onSetLimitPrice = {},
        )
    }
}
