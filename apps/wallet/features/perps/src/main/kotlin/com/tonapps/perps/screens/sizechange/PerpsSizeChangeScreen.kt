package com.tonapps.perps.screens.sizechange

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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.paging.collectAsStateWorkaround
import com.tonapps.perps.data.PERPS_EMPTY_VALUE
import com.tonapps.perps.data.PerpsAutoCloseDraft
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsOpenPosition
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.PerpsSizeDirection
import com.tonapps.perps.data.bidiIsolate
import com.tonapps.perps.data.autoCloseLeverage
import com.tonapps.perps.data.formatAutoClosePrice
import com.tonapps.perps.data.formatUsd
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.perps.screens.order.PerpsAmountInputCell
import com.tonapps.perps.screens.order.PerpsAutoCloseBounds
import com.tonapps.perps.screens.order.PerpsAutoCloseDialog
import com.tonapps.perps.screens.order.PerpsAutoCloseEstimate
import com.tonapps.perps.screens.order.perpsAutoCloseSummary
import com.tonapps.perps.screens.order.withFix
import com.tonapps.perps.screens.review.PerpsAutoCloseArgs
import com.tonapps.perps.screens.review.PerpsAutoCloseFix
import com.tonapps.perps.screens.review.PerpsReviewArgs
import com.tonapps.perps.trade.PerpsAutoCloseEdit
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.RStr
import ui.components.moon.MoonBadgeButton
import ui.components.moon.MoonChevronRight
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLargeItemSubtitle
import ui.components.moon.MoonSmallItemTitle
import ui.components.moon.MoonTopAppBar
import ui.components.moon.MoonTopAppBarSubtitle
import ui.components.moon.MoonTopAppBarTitle
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.container.MoonScaffold
import ui.theme.UIKit
import ui.utils.uppercased
import java.math.BigDecimal

@Composable
fun PerpsSizeChangeScreen(
    feature: PerpsSizeChangeFeature,
    refusal: Int?,
    onRefusalConsumed: () -> Unit,
    autoCloseFix: PerpsAutoCloseFix?,
    onAutoCloseFixConsumed: () -> Unit,
    onReview: (PerpsReviewArgs.SizeChange) -> Unit,
    onDeposit: () -> Unit,
    onClose: () -> Unit,
) {
    val position by feature.position.collectAsStateWorkaround()
    val mark by feature.mark.collectAsStateWorkaround()
    val priceDecimals by feature.priceDecimals.collectAsStateWorkaround()
    val amount by feature.amount.collectAsStateWorkaround()
    val sizePreview by feature.sizePreview.collectAsStateWorkaround()
    val autoCloseEstimate by feature.autoCloseEstimate.collectAsStateWorkaround()
    val restingAutoClose by feature.restingAutoClose.collectAsStateWorkaround()
    val autoClose by feature.autoClose.collectAsStateWorkaround()
    val autoCloseEdit by feature.autoCloseEdit.collectAsStateWorkaround()
    val autoCloseEditable by feature.autoCloseEditable.collectAsStateWorkaround()
    val availableFormat by feature.availableFormat.collectAsStateWorkaround()
    val issue by feature.issue.collectAsStateWorkaround()
    val canReview by feature.canReview.collectAsStateWorkaround()
    val refusalText by feature.refusal.collectAsStateWorkaround()

    LaunchedEffect(refusal) {
        if (refusal != null) {
            feature.sendAction(PerpsSizeChangeAction.ShowRefusal(refusal))
            onRefusalConsumed()
        }
    }

    PerpsSizeChangeContent(
        symbol = feature.symbol,
        side = feature.side,
        direction = feature.direction,
        position = position,
        mark = mark,
        priceDecimals = priceDecimals,
        amount = amount,
        sizePreview = sizePreview,
        autoCloseEstimate = autoCloseEstimate,
        autoClose = autoClose,
        autoCloseEditable = autoCloseEditable,
        availableFormat = availableFormat,
        issue = issue,
        refusal = refusalText,
        canReview = canReview,
        autoCloseFix = autoCloseFix,
        onAutoCloseFixConsumed = onAutoCloseFixConsumed,
        onAction = feature::sendAction,
        onReview = {
            onReview(
                PerpsReviewArgs.SizeChange(
                    marketIndex = feature.marketIndex,
                    symbol = feature.symbol,
                    side = feature.side,
                    direction = feature.direction,
                    marginDelta = amount,
                    autoClose = autoCloseEdit.toArgs(),
                    restingTakeProfit = restingAutoClose?.takeProfit?.toPlainString(),
                    restingStopLoss = restingAutoClose?.stopLoss?.toPlainString(),
                    autoCloseEditable = autoCloseEditable,
                )
            )
        },
        onDeposit = onDeposit,
        onClose = onClose,
    )
}

@Composable
private fun PerpsSizeChangeContent(
    symbol: String,
    side: PerpsOrderSide,
    direction: PerpsSizeDirection,
    position: PerpsOpenPosition?,
    mark: BigDecimal?,
    priceDecimals: Int,
    amount: String,
    sizePreview: PerpsSizePreview?,
    autoCloseEstimate: PerpsAutoCloseEstimate?,
    autoClose: PerpsAutoCloseDraft?,
    autoCloseEditable: Boolean,
    availableFormat: String,
    issue: PerpsSizeChangeIssue?,
    refusal: Int?,
    canReview: Boolean,
    autoCloseFix: PerpsAutoCloseFix?,
    onAutoCloseFixConsumed: () -> Unit,
    onAction: (PerpsSizeChangeAction) -> Unit,
    onReview: () -> Unit,
    onDeposit: () -> Unit,
    onClose: () -> Unit,
) {
    var showAutoClose by remember { mutableStateOf(false) }

    val isAdd = direction == PerpsSizeDirection.ADD
    val markText = mark?.let { formatUsd(it) } ?: PERPS_EMPTY_VALUE
    val sideText = stringResource(
        if (side == PerpsOrderSide.LONG) {
            Localization.perps_long
        } else {
            Localization.perps_short
        }
    )
    val sizeText = sizePreview?.let { preview ->
        val next = preview.next
        if (next != null) {
            stringResource(
                Localization.perps_size_change_arrow_pattern,
                bidiIsolate(preview.current),
                bidiIsolate(next),
            )
        } else {
            preview.current
        }
    }
    val autoCloseText: CharSequence = if (autoClose != null) {
        perpsAutoCloseSummary(
            takeProfit = autoClose.takeProfit?.let { formatAutoClosePrice(it, priceDecimals) },
            stopLoss = autoClose.stopLoss?.let { formatAutoClosePrice(it, priceDecimals) },
        )
    } else {
        stringResource(Localization.perps_order_set)
    }

    val autoCloseLocked = position != null && !autoCloseEditable
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(autoCloseFix) {
        if (autoCloseFix != null) {
            keyboardController?.hide()
            showAutoClose = true
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
                        text = stringResource(
                            if (isAdd) {
                                Localization.perps_review_add_title
                            } else {
                                Localization.perps_review_reduce_title
                            },
                            sideText,
                            symbol,
                        ),
                    )
                },
                subtitle = {
                    MoonTopAppBarSubtitle(
                        text = stringResource(Localization.perps_order_current_price, markText),
                    )
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
                    pillText = sizeText?.let { stringResource(Localization.perps_order_size, it) },
                    onAmountChange = { onAction(PerpsSizeChangeAction.SetAmount(it)) },
                    onSwap = { onAction(PerpsSizeChangeAction.ToggleSizeMode) },
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
                    if (isAdd) {
                        MoonBadgeButton(
                            content = { MoonSmallItemTitle(stringResource(RStr.max).uppercased()) },
                            onClick = { onAction(PerpsSizeChangeAction.Max) },
                        )
                    } else {
                        Spacer(Modifier.width(0.dp))
                    }

                    SizeChangeIssueRow(
                        issue = issue,
                        isAdd = isAdd,
                        available = availableFormat,
                        onRetry = { onAction(PerpsSizeChangeAction.Retry) },
                        onDeposit = onDeposit,
                    )
                }

                Spacer(Modifier.height(16.dp))

                MoonBundleCell {
                    Column {
                        MoonPropertyBigCell(
                            title = stringResource(Localization.perps_order_leverage),
                            value = stringResource(
                                Localization.perps_leverage_value_pattern,
                                position?.leverage ?: 0,
                            ),
                            valueDescription = null,
                        )

                        MoonItemDivider()

                        MoonPropertyBigCell(
                            title = {
                                MoonLargeItemSubtitle(
                                    text = stringResource(Localization.perps_order_auto_close),
                                    color = if (!autoCloseLocked) {
                                        UIKit.colorScheme.text.secondary
                                    } else {
                                        UIKit.colorScheme.text.tertiary
                                    },
                                )
                            },
                            content = {
                                MoonItemTitle(
                                    text = autoCloseText,
                                    color = when {
                                        autoCloseLocked -> UIKit.colorScheme.text.tertiary
                                        autoClose != null -> UIKit.colorScheme.text.primary
                                        else -> UIKit.colorScheme.text.accent
                                    },
                                )
                            },
                            onClick = if (mark != null && autoCloseEditable) {
                                {
                                    keyboardController?.hide()
                                    showAutoClose = true
                                }
                            } else {
                                null
                            },
                            valuePostfix = if (!autoCloseLocked) {
                                { MoonChevronRight() }
                            } else {
                                null
                            },
                        )
                    }
                }

                if (refusal != null) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp),
                        text = stringResource(refusal),
                        style = UIKit.typography.body2,
                        color = UIKit.colorScheme.accent.red,
                    )
                }

                if (autoCloseLocked) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 8.dp),
                        text = stringResource(Localization.perps_auto_close_unavailable),
                        style = UIKit.typography.body2,
                        color = UIKit.colorScheme.text.secondary,
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

    if (showAutoClose && mark != null) {
        PerpsAutoCloseDialog(
            priceDecimals = priceDecimals,
            side = side,
            bounds = PerpsAutoCloseBounds(
                entryPrice = autoCloseEstimate?.referencePrice ?: position?.avgEntryPrice ?: mark,
                leverage = position?.autoCloseLeverage() ?: BigDecimal.ONE,
                currentPrice = mark,
                liquidationPrice = position?.liquidationPrice,
            ),
            initial = autoClose.withFix(autoCloseFix),
            estimate = autoCloseEstimate,
            onSet = { onAction(PerpsSizeChangeAction.SetAutoClose(it)) },
            onClose = {
                showAutoClose = false
                onAutoCloseFixConsumed()
            },
        )
    }
}

@Composable
private fun SizeChangeIssueRow(
    issue: PerpsSizeChangeIssue?,
    isAdd: Boolean,
    available: String,
    onRetry: () -> Unit,
    onDeposit: () -> Unit,
) {
    when (issue) {
        null -> Row(verticalAlignment = Alignment.CenterVertically) {
            MoonItemSubtitle(
                text = stringResource(Localization.perps_available, available),
                color = UIKit.colorScheme.text.secondary,
            )
            if (isAdd) {
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

        PerpsSizeChangeIssue.Loading -> Unit

        is PerpsSizeChangeIssue.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
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

        PerpsSizeChangeIssue.NoPosition -> MoonItemSubtitle(
            text = stringResource(Localization.perps_review_no_position),
            color = UIKit.colorScheme.accent.red,
        )

        PerpsSizeChangeIssue.TradingDisabled -> MoonItemSubtitle(
            text = stringResource(Localization.perps_order_trading_disabled),
            color = UIKit.colorScheme.accent.red,
        )

        is PerpsSizeChangeIssue.ExceedsMargin -> MoonItemSubtitle(
            text = stringResource(
                Localization.perps_reduce_exceeds_margin,
                issue.marginFormat,
            ),
            color = UIKit.colorScheme.accent.red,
        )

        is PerpsSizeChangeIssue.AboveMaximum -> MoonItemSubtitle(
            text = stringResource(
                Localization.perps_order_max_margin,
                issue.maxMarginFormat,
            ),
            color = UIKit.colorScheme.accent.red,
        )

        is PerpsSizeChangeIssue.BelowMinimum -> MoonItemSubtitle(
            text = stringResource(
                Localization.perps_order_min_margin,
                formatUsdAmount(issue.minMargin),
            ),
            color = UIKit.colorScheme.accent.red,
        )

        PerpsSizeChangeIssue.OnlyFullClose -> MoonItemSubtitle(
            text = stringResource(Localization.perps_reduce_only_full),
            color = UIKit.colorScheme.accent.red,
        )

        PerpsSizeChangeIssue.InsufficientBalance -> Row(verticalAlignment = Alignment.CenterVertically) {
            MoonItemSubtitle(
                text = stringResource(Localization.perps_order_insufficient_balance),
                color = UIKit.colorScheme.accent.red,
            )
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
}

private fun PerpsAutoCloseEdit.toArgs(): PerpsAutoCloseArgs {
    return when (this) {
        PerpsAutoCloseEdit.Keep -> PerpsAutoCloseArgs.Keep
        is PerpsAutoCloseEdit.Set -> PerpsAutoCloseArgs.Set(
            takeProfit = target?.takeProfit?.toPlainString(),
            stopLoss = target?.stopLoss?.toPlainString(),
            takeProfitRoi = target?.takeProfitRoi?.toPlainString(),
            stopLossRoi = target?.stopLossRoi?.toPlainString(),
        )
    }
}
