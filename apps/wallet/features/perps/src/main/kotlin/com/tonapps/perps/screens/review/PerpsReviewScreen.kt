package com.tonapps.perps.screens.review

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tonapps.core.LocalIsTopFragment
import com.tonapps.paging.collectAsStateWorkaround
import com.tonapps.perps.data.PerpsAutoCloseDraft
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.bidiIsolate
import com.tonapps.perps.data.formatPpmPercent
import com.tonapps.perps.screens.common.PerpsLoyaltyRewardCell
import com.tonapps.perps.screens.markets.PerpsAssetIcon
import com.tonapps.perps.screens.order.PerpsAutoCloseDialog
import com.tonapps.perps.screens.order.perpsAutoCloseSummary
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonChevronRight
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLargeItemSubtitle
import ui.components.moon.MoonTopAppBar
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonButtonCellDefaults
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.cell.MoonSlideConfirmation
import ui.components.moon.cell.MoonSlideConfirmationState
import ui.components.moon.cell.rememberSliderState
import ui.components.moon.container.MoonScaffold
import ui.components.moon.container.MoonSurface
import ui.components.moon.dialog.DialogNavigator
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.components.moon.screen.MoonLoadingScreen
import ui.painterResource
import ui.theme.UIKit

@Composable
fun PerpsReviewScreen(
    feature: PerpsReviewFeature,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onAutoCloseChanged: (PerpsAutoCloseDraft?) -> Unit,
    onAutoCloseDropped: (PerpsAutoCloseDraft?) -> Unit,
    onOpenLoyalty: () -> Unit,
) {
    val content by feature.rows.collectAsStateWorkaround()
    val loyaltyReward by feature.loyaltyReward.collectAsStateWorkaround()
    val status by feature.status.collectAsStateWorkaround()
    val sheet by feature.sheet.collectAsStateWorkaround()
    val canConfirm by feature.canConfirm.collectAsStateWorkaround()
    val editor by feature.autoCloseEditor.collectAsStateWorkaround()
    val market by feature.market.collectAsStateWorkaround()
    var showAutoClose by remember { mutableStateOf(false) }

    val isTopFragment = LocalIsTopFragment.current
    LifecycleResumeEffect(feature, isTopFragment) {
        feature.sendAction(PerpsReviewAction.SetVisible(isTopFragment))
        onPauseOrDispose {
            feature.sendAction(PerpsReviewAction.SetVisible(false))
        }
    }

    PerpsReviewContent(
        symbol = market.symbol,
        iconUrl = market.iconUrl,
        side = feature.side,
        content = content,
        loyaltyReward = loyaltyReward,
        status = status,
        canConfirm = canConfirm,
        onAction = feature::sendAction,
        onAutoCloseClick = if (editor != null) {
            { showAutoClose = true }
        } else {
            null
        },
        onOpenLoyalty = onOpenLoyalty,
        onBack = onBack,
        onClose = onClose,
    )

    when (val current = sheet) {
        is PerpsReviewSheet.OrderNotPlaced -> PerpsOrderNotPlacedSheet(
            slippagePpm = current.slippagePpm,
            onTryAgain = { feature.sendAction(PerpsReviewAction.Retry) },
            onClose = { feature.sendAction(PerpsReviewAction.DismissSheet) },
        )

        is PerpsReviewSheet.AutoCloseUpdate -> PerpsAutoCloseUpdateSheet(
            stale = current.stale,
            onContinue = if (current.canContinue) {
                {
                    feature.sendAction(PerpsReviewAction.ContinueWithoutAutoClose(current.remaining))
                    onAutoCloseDropped(current.remaining)
                }
            } else {
                null
            },
            onUpdate = { feature.sendAction(PerpsReviewAction.UpdateAutoClose(current.fix)) },
            onClose = { feature.sendAction(PerpsReviewAction.DismissSheet) },
        )

        null -> Unit
    }

    val currentEditor = editor
    if (showAutoClose && currentEditor != null) {
        PerpsAutoCloseDialog(
            priceDecimals = currentEditor.priceDecimals,
            side = feature.side,
            bounds = currentEditor.bounds,
            initial = currentEditor.initial,
            estimate = currentEditor.estimate,
            onSet = { draft ->
                feature.sendAction(PerpsReviewAction.SetAutoClose(draft))
                onAutoCloseChanged(draft)
            },
            onClose = { showAutoClose = false },
            limitPrice = currentEditor.limitPrice,
        )
    }
}

@Composable
private fun PerpsReviewContent(
    symbol: String,
    iconUrl: String?,
    side: PerpsOrderSide,
    content: PerpsReviewContent?,
    loyaltyReward: String?,
    status: PerpsReviewStatus,
    canConfirm: Boolean,
    onAction: (PerpsReviewAction) -> Unit,
    onAutoCloseClick: (() -> Unit)?,
    onOpenLoyalty: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val sliderState = rememberSliderState()
    val sideText = stringResource(
        if (side == PerpsOrderSide.LONG) {
            Localization.perps_long
        } else {
            Localization.perps_short
        }
    )

    LaunchedEffect(status) {
        if (status != PerpsReviewStatus.Submitting && sliderState.isConfirmed) {
            sliderState.reset()
        }
    }

    MoonScaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        topBar = {
            MoonTopAppBar(
                modifier = Modifier.statusBarsPadding(),
                navigationIconRes = UIKitIcon.ic_chevron_left_16,
                onNavigationClick = onBack,
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
            ) {
                PerpsReviewHeader(
                    symbol = symbol,
                    iconUrl = iconUrl,
                    title = if (content != null) {
                        stringResource(content.title, symbol)
                    } else {
                        stringResource(Localization.perps_review_title_pattern, sideText, symbol)
                    },
                )

                if (content != null) {
                    PerpsReviewRowsCell(
                        symbol = symbol,
                        content = content,
                        loyaltyReward = loyaltyReward,
                        onAutoCloseClick = onAutoCloseClick,
                        onOpenLoyalty = onOpenLoyalty,
                    )
                } else if (status !is PerpsReviewStatus.Failed) {
                    MoonLoadingScreen()
                }

                PerpsReviewHint(status = status)
                Spacer(Modifier.height(16.dp))
            }

            MoonSlideConfirmation(
                state = if (status == PerpsReviewStatus.Submitting) {
                    MoonSlideConfirmationState.Loader
                } else {
                    MoonSlideConfirmationState.Slider
                },
                sliderState = sliderState,
                title = stringResource(Localization.perps_review_confirm),
                subtitle = stringResource(Localization.perps_review_swipe_hint),
                error = (status as? PerpsReviewStatus.Failed)?.let { stringResource(it.text) },
                buttonTitle = stringResource(Localization.perps_review_try_again),
                enabled = canConfirm,
                onConfirm = { onAction(PerpsReviewAction.Confirm) },
                onClick = { onAction(PerpsReviewAction.Retry) },
                onDone = {},
            )
        }
    }
}

@Composable
private fun PerpsReviewHeader(symbol: String, iconUrl: String?, title: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PerpsAssetIcon(symbol = symbol, iconUrl = iconUrl, size = 96.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(Localization.perps_review_confirm_action),
            style = UIKit.typography.body1,
            color = UIKit.colorScheme.text.secondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = UIKit.typography.h3,
            color = UIKit.colorScheme.text.primary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PerpsReviewRowsCell(
    symbol: String,
    content: PerpsReviewContent,
    loyaltyReward: String?,
    onAutoCloseClick: (() -> Unit)?,
    onOpenLoyalty: () -> Unit,
) {
    MoonBundleCell {
        Column {
            content.rows.forEachIndexed { index, row ->
                if (index > 0) {
                    MoonItemDivider()
                }
                PerpsReviewRowCell(row = row, symbol = symbol, onAutoCloseClick = onAutoCloseClick)
            }
            if (loyaltyReward != null) {
                MoonItemDivider()
                PerpsLoyaltyRewardCell(amount = loyaltyReward, hiddenBalance = false, onClick = onOpenLoyalty)
            }
        }
    }
}

@Composable
private fun PerpsReviewRowCell(row: PerpsReviewRow, symbol: String, onAutoCloseClick: (() -> Unit)?) {
    val isAutoClose = row.value is PerpsReviewValue.AutoClose
    val autoCloseUnset = row.value is PerpsReviewValue.AutoClose && row.value.isUnset
    val value = perpsReviewValueText(row.value)
    val previous = row.previous?.let { perpsReviewValueText(it) }
    val text: CharSequence = if (previous != null) {
        val arrowColor = UIKit.colorScheme.text.secondary
        // formatting the pattern with empty values leaves the bare arrow separator
        val arrow = stringResource(Localization.perps_size_change_arrow_pattern, "", "")
        buildAnnotatedString {
            append(bidiIsolate(previous.toString()))
            withStyle(SpanStyle(color = arrowColor)) {
                append(arrow)
            }
            append(bidiIsolate(value.toString()))
        }
    } else {
        value
    }

    MoonPropertyBigCell(
        title = { MoonLargeItemSubtitle(text = stringResource(row.title)) },
        content = {
            MoonItemTitle(
                text = text,
                color = if (autoCloseUnset) {
                    UIKit.colorScheme.text.accent
                } else {
                    when (row.tone) {
                        PerpsReviewTone.POSITIVE -> UIKit.colorScheme.accent.green
                        PerpsReviewTone.NEGATIVE -> UIKit.colorScheme.accent.red
                        PerpsReviewTone.WARNING -> UIKit.colorScheme.accent.orange
                        PerpsReviewTone.NEUTRAL -> UIKit.colorScheme.text.primary
                    }
                },
            )
        },
        contentDescription = perpsReviewSubText(row.sub, symbol)?.let { description ->
            {
                Text(
                    text = description,
                    style = UIKit.typography.body2,
                    color = UIKit.colorScheme.text.secondary,
                )
            }
        },
        valuePostfix = if (isAutoClose) {
            { MoonChevronRight() }
        } else {
            null
        },
        onClick = if (isAutoClose) {
            onAutoCloseClick
        } else {
            null
        },
    )
}

@Composable
private fun perpsReviewValueText(value: PerpsReviewValue): CharSequence {
    return when (value) {
        is PerpsReviewValue.Text -> value.text
        is PerpsReviewValue.Resource -> stringResource(value.text)
        is PerpsReviewValue.Leverage -> stringResource(
            Localization.perps_leverage_value_pattern,
            value.value,
        )

        is PerpsReviewValue.AutoClose -> if (!value.isUnset) {
            perpsAutoCloseSummary(value.takeProfit, value.stopLoss)
        } else {
            stringResource(Localization.perps_order_set)
        }
    }
}

@Composable
private fun perpsReviewSubText(sub: PerpsReviewSub?, symbol: String): String? {
    return when (sub) {
        null -> null
        is PerpsReviewSub.Base -> stringResource(
            Localization.perps_review_base_pattern,
            sub.amount,
            symbol,
        )

        is PerpsReviewSub.BaseDelta -> stringResource(
            if (sub.added) {
                Localization.perps_review_size_added_pattern
            } else {
                Localization.perps_review_size_removed_pattern
            },
            sub.amount,
            symbol,
        )

        is PerpsReviewSub.Percent -> sub.text
    }
}

@Composable
private fun PerpsReviewHint(status: PerpsReviewStatus) {
    if (status != PerpsReviewStatus.PriceUpdated) {
        return
    }

    Spacer(Modifier.height(12.dp))
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        text = stringResource(Localization.perps_review_price_updated),
        style = UIKit.typography.body2,
        color = UIKit.colorScheme.text.secondary,
    )
}

@Composable
private fun PerpsOrderNotPlacedSheet(
    slippagePpm: Long,
    onTryAgain: () -> Unit,
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)

    PerpsReviewAlertSheet(
        navigator = navigator,
        title = stringResource(Localization.perps_review_order_not_placed_title),
        text = stringResource(
            Localization.perps_review_order_not_placed_text,
            formatPpmPercent(slippagePpm),
        ),
    ) {
        MoonBottomButtonCell(
            text = stringResource(Localization.perps_review_try_again),
            onClick = {
                navigator.close()
                onTryAgain()
            },
        )
    }
}

private data class PerpsAutoCloseUpdateCopy(
    @StringRes val text: Int,
    @StringRes val continueText: Int,
    @StringRes val updateText: Int,
)

private fun autoCloseUpdateCopy(stale: Set<PerpsAutoCloseField>): PerpsAutoCloseUpdateCopy {
    return when {
        stale.size > 1 -> PerpsAutoCloseUpdateCopy(
            text = Localization.perps_review_auto_close_update_text,
            continueText = Localization.perps_review_continue_without_auto_close,
            updateText = Localization.perps_review_update_auto_close,
        )

        PerpsAutoCloseField.TAKE_PROFIT in stale -> PerpsAutoCloseUpdateCopy(
            text = Localization.perps_review_tp_update_text,
            continueText = Localization.perps_review_continue_without_tp,
            updateText = Localization.perps_review_update_tp,
        )

        else -> PerpsAutoCloseUpdateCopy(
            text = Localization.perps_review_sl_update_text,
            continueText = Localization.perps_review_continue_without_sl,
            updateText = Localization.perps_review_update_sl,
        )
    }
}

@Composable
private fun PerpsAutoCloseUpdateSheet(
    stale: Set<PerpsAutoCloseField>,
    onContinue: (() -> Unit)?,
    onUpdate: () -> Unit,
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)
    val copy = autoCloseUpdateCopy(stale)

    PerpsReviewAlertSheet(
        navigator = navigator,
        title = stringResource(stale.autoCloseUpdateTitle()),
        text = stringResource(copy.text),
    ) {
        if (onContinue != null) {
            MoonBottomButtonCell(
                text = stringResource(copy.continueText),
                colors = MoonButtonCellDefaults.ButtonColorsSecondary,
                contentPadding = remember { PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp) },
                onClick = {
                    navigator.close()
                    onContinue()
                },
            )
        }
        MoonBottomButtonCell(
            text = stringResource(copy.updateText),
            onClick = {
                navigator.close()
                onUpdate()
            },
        )
    }
}

@Composable
private fun PerpsReviewAlertSheet(
    navigator: DialogNavigator,
    title: String,
    text: String,
    buttons: @Composable () -> Unit,
) {
    MoonModalDialog(navigator = navigator) {
        MoonTopAppBarSimple(
            title = "",
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = { navigator.close() },
        )
        MoonSurface {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    modifier = Modifier.size(84.dp),
                    painter = painterResource(UIKitIcon.ic_exclamationmark_circle_84),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(UIKit.colorScheme.icon.secondary),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    text = title,
                    style = UIKit.typography.h2,
                    color = UIKit.colorScheme.text.primary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    text = text,
                    style = UIKit.typography.body1,
                    color = UIKit.colorScheme.text.secondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                buttons()
            }
        }
    }
}
