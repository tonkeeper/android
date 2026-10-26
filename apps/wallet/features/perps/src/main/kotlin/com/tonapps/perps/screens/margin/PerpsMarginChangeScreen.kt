package com.tonapps.perps.screens.margin

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.tonapps.paging.collectAsStateWorkaround
import com.tonapps.perps.data.PERPS_EMPTY_VALUE
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsOpenPosition
import com.tonapps.perps.data.bidiIsolate
import com.tonapps.perps.data.formatUsd
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.perps.screens.order.PerpsAmountInputCell
import com.tonapps.perps.screens.review.PerpsReviewArgs
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.RStr
import ui.components.moon.MoonBadgeButton
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
fun PerpsMarginChangeScreen(
    feature: PerpsMarginChangeFeature,
    refusal: Int?,
    onRefusalConsumed: () -> Unit,
    onReview: (PerpsReviewArgs.Margin) -> Unit,
    onDeposit: () -> Unit,
    onClose: () -> Unit,
) {
    val position by feature.position.collectAsStateWorkaround()
    val mark by feature.mark.collectAsStateWorkaround()
    val amount by feature.amount.collectAsStateWorkaround()
    val available by feature.availableFormat.collectAsStateWorkaround()
    val liquidation by feature.liquidation.collectAsStateWorkaround()
    val issue by feature.issue.collectAsStateWorkaround()
    val canReview by feature.canReview.collectAsStateWorkaround()
    val refusalText by feature.refusal.collectAsStateWorkaround()

    LaunchedEffect(refusal) {
        if (refusal != null) {
            feature.sendAction(PerpsMarginChangeAction.ShowRefusal(refusal))
            onRefusalConsumed()
        }
    }

    PerpsMarginChangeContent(
        add = feature.add,
        position = position,
        mark = mark,
        amount = amount,
        available = available,
        liquidation = liquidation,
        issue = issue,
        refusal = refusalText,
        canReview = canReview,
        onAction = feature::sendAction,
        onReview = {
            onReview(
                PerpsReviewArgs.Margin(
                    marketIndex = feature.marketIndex,
                    symbol = feature.symbol,
                    side = feature.side,
                    amountQuote = amount,
                    add = feature.add,
                )
            )
        },
        onDeposit = onDeposit,
        onClose = onClose,
    )
}

@Composable
private fun PerpsMarginChangeContent(
    add: Boolean,
    position: PerpsOpenPosition?,
    mark: BigDecimal?,
    amount: String,
    available: String,
    liquidation: PerpsLiquidationChange?,
    issue: PerpsMarginChangeIssue?,
    refusal: Int?,
    canReview: Boolean,
    onAction: (PerpsMarginChangeAction) -> Unit,
    onReview: () -> Unit,
    onDeposit: () -> Unit,
    onClose: () -> Unit,
) {
    val markText = mark?.let { formatUsd(it) } ?: PERPS_EMPTY_VALUE
    val liquidationText = liquidationText(liquidation)

    MoonScaffold(
        modifier = Modifier.windowInsetsPadding(
            WindowInsets.ime.union(WindowInsets.navigationBars).union(WindowInsets.statusBars),
        ),
        topBar = {
            MoonTopAppBar(
                title = {
                    MoonTopAppBarTitle(
                        text = stringResource(
                            if (add) {
                                Localization.perps_add_margin
                            } else {
                                Localization.perps_reduce_margin
                            }
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
                    pillText = null,
                    minHeight = 148.dp,
                    onAmountChange = { onAction(PerpsMarginChangeAction.SetAmount(it)) },
                    onSwap = null,
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
                        onClick = { onAction(PerpsMarginChangeAction.Max) },
                    )

                    MarginIssueRow(
                        issue = issue,
                        add = add,
                        available = available,
                        onRetry = { onAction(PerpsMarginChangeAction.Retry) },
                        onDeposit = onDeposit,
                    )
                }

                Spacer(Modifier.height(16.dp))

                MoonBundleCell {
                    MoonPropertyBigCell(
                        title = {
                            MoonLargeItemSubtitle(
                                text = stringResource(Localization.perps_review_liquidation),
                            )
                        },
                        content = {
                            MoonItemTitle(
                                text = liquidationText,
                                color = if (liquidation?.atRisk == true) {
                                    UIKit.colorScheme.accent.orange
                                } else {
                                    UIKit.colorScheme.text.primary
                                },
                            )
                        },
                    )
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
            }

            MoonBottomButtonCell(
                text = stringResource(Localization.perps_order_review),
                enabled = canReview,
            ) {
                onReview()
            }
        }
    }
}

@Composable
private fun liquidationText(liquidation: PerpsLiquidationChange?): CharSequence {
    val current = liquidation?.current ?: PERPS_EMPTY_VALUE
    val next = liquidation?.next ?: return current

    val currentText = bidiIsolate(current)
    val nextText = bidiIsolate(next)
    val text = stringResource(Localization.perps_size_change_arrow_pattern, currentText, nextText)
    val arrowColor = UIKit.colorScheme.text.secondary
    return buildAnnotatedString {
        append(text)
        addStyle(SpanStyle(color = arrowColor), currentText.length, text.length - nextText.length)
    }
}

@Composable
private fun MarginIssueRow(
    issue: PerpsMarginChangeIssue?,
    add: Boolean,
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
            if (add) {
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

        PerpsMarginChangeIssue.Loading -> Unit

        is PerpsMarginChangeIssue.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
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

        PerpsMarginChangeIssue.NoPosition -> MoonItemSubtitle(
            text = stringResource(Localization.perps_review_no_position),
            color = UIKit.colorScheme.accent.red,
        )

        PerpsMarginChangeIssue.TradingDisabled -> MoonItemSubtitle(
            text = stringResource(Localization.perps_order_trading_disabled),
            color = UIKit.colorScheme.accent.red,
        )

        is PerpsMarginChangeIssue.MinAmount -> MoonItemSubtitle(
            text = stringResource(Localization.perps_order_min_margin, formatUsdAmount(issue.min)),
            color = UIKit.colorScheme.accent.red,
        )

        is PerpsMarginChangeIssue.ExceedsMargin -> MoonItemSubtitle(
            text = stringResource(Localization.perps_margin_max_remove, issue.maxFormat),
            color = UIKit.colorScheme.accent.red,
        )

        PerpsMarginChangeIssue.ImmediateRisk -> MoonItemSubtitle(
            text = stringResource(Localization.perps_margin_reduce_risk),
            color = UIKit.colorScheme.accent.red,
        )

        PerpsMarginChangeIssue.InsufficientBalance -> Row(verticalAlignment = Alignment.CenterVertically) {
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
