package com.tonapps.deposit.multicoin.screens.moonpay

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.core.helper.rememberClipboardManager
import com.tonapps.deposit.multicoin.moonpay.domain.MoonPayPaymentFailure
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonDivider
import ui.components.moon.MoonLoader
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBottomButtonCell
import ui.preview.ThemedPreview
import ui.theme.UIKit
import ui.theme.modifiers.modalSheetMaxHeight

@Composable
fun MoonPayPaymentScreen(
    feature: MoonPayPaymentFeature,
    onClose: () -> Unit,
    onRetry: (MoonPayPaymentRetry) -> Unit,
    onCancelled: () -> Unit,
    onDismissalChange: (MoonPayPaymentDismissal) -> Unit,
) {
    val steps by feature.steps.collectAsState()
    val orderId by feature.orderId.collectAsState()
    val showsDetails by feature.showsDetails.collectAsState()
    val retry by feature.retry.collectAsState()
    val dismissal by feature.dismissal.collectAsState()
    val canClose = dismissal != MoonPayPaymentDismissal.Blocked

    LaunchedEffect(feature) {
        feature.events.collect { event ->
            when (event) {
                is MoonPayPaymentEvent.Cancelled -> onCancelled()
            }
        }
    }

    LaunchedEffect(dismissal) {
        onDismissalChange(dismissal)
    }
    BackHandler {
        if (canClose) {
            onClose()
        }
    }

    MoonPayPaymentContent(
        summary = feature.summary,
        steps = steps,
        orderId = orderId,
        showsDetails = showsDetails,
        retry = retry,
        canClose = canClose,
        onClose = onClose,
        onRetry = onRetry,
    )
}

@Composable
private fun MoonPayPaymentContent(
    summary: MoonPayPaymentSummary,
    steps: MoonPayPaymentSteps,
    orderId: String?,
    showsDetails: Boolean,
    retry: MoonPayPaymentRetry?,
    canClose: Boolean,
    onClose: () -> Unit,
    onRetry: (MoonPayPaymentRetry) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .modalSheetMaxHeight()
            .background(UIKit.colorScheme.background.page)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        MoonTopAppBarSimple(
            title = stringResource(Localization.moonpay_payment_title),
            actionIconRes = UIKitIcon.ic_close_16.takeIf { canClose },
            onActionClick = onClose,
        )
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Steps(listOf(steps.started, steps.pending, steps.completed))
            if (showsDetails) {
                Summary(summary, orderId)
                Fees(summary)
            }
        }
        if (retry != null) {
            MoonBottomButtonCell(text = stringResource(retry.title)) {
                onRetry(retry)
            }
        }
    }
}

@Composable
private fun Steps(steps: List<MoonPayPaymentStep>) {
    Group {
        steps.forEachIndexed { index, step ->
            StepRow(step, first = index == 0, last = index == steps.lastIndex)
        }
    }
}

@Composable
private fun StepRow(step: MoonPayPaymentStep, first: Boolean, last: Boolean) {
    val (title, subtitle) = step.text.lines()
    val lineColor = UIKit.colorScheme.background.contentTint
    Box(modifier = Modifier.fillMaxWidth().height(STEP_HEIGHT)) {
        if (!first) {
            StepLine(color = lineColor, modifier = Modifier.align(Alignment.TopStart))
        }
        if (!last) {
            StepLine(color = lineColor, modifier = Modifier.align(Alignment.BottomStart))
            MoonDivider(modifier = Modifier.align(Alignment.BottomStart).padding(start = STEP_TEXT_START))
        }
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StepIndicator(step.status)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = UIKit.typography.label1,
                    color = if (step.status == MoonPayPaymentStepStatus.Waiting) {
                        UIKit.colorScheme.text.secondary
                    } else {
                        UIKit.colorScheme.text.primary
                    },
                )
                Text(
                    text = subtitle,
                    style = UIKit.typography.body2,
                    color = if (step.status == MoonPayPaymentStepStatus.Waiting) {
                        UIKit.colorScheme.text.tertiary
                    } else {
                        UIKit.colorScheme.text.secondary
                    },
                )
            }
        }
    }
}

@Composable
private fun StepLine(color: Color, modifier: Modifier) {
    Box(
        modifier = modifier
            .padding(start = 16.dp + INDICATOR_SIZE / 2 - 1.dp)
            .size(width = 2.dp, height = 12.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun StepIndicator(status: MoonPayPaymentStepStatus) {
    val background = when (status) {
        MoonPayPaymentStepStatus.Waiting,
        MoonPayPaymentStepStatus.InProgress,
        -> UIKit.colorScheme.background.contentTint
        MoonPayPaymentStepStatus.Done -> UIKit.colorScheme.accent.green.copy(alpha = TINT_ALPHA)
        MoonPayPaymentStepStatus.Failed -> UIKit.colorScheme.accent.red.copy(alpha = TINT_ALPHA)
    }
    Box(
        modifier = Modifier
            .size(INDICATOR_SIZE)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            MoonPayPaymentStepStatus.Waiting -> Unit
            MoonPayPaymentStepStatus.InProgress -> MoonLoader(modifier = Modifier.size(STEP_ICON_SIZE))
            MoonPayPaymentStepStatus.Done -> Icon(
                painter = painterResource(UIKitIcon.ic_checkmark_circle_28),
                contentDescription = null,
                tint = UIKit.colorScheme.accent.green,
                modifier = Modifier.size(STEP_ICON_SIZE),
            )
            MoonPayPaymentStepStatus.Failed -> Icon(
                painter = painterResource(UIKitIcon.ic_xmark_circle_32),
                contentDescription = null,
                tint = UIKit.colorScheme.accent.red,
                modifier = Modifier.size(STEP_ICON_SIZE),
            )
        }
    }
}

@Composable
private fun Summary(summary: MoonPayPaymentSummary, orderId: String?) {
    val clipboard = rememberClipboardManager()
    Group(modifier = Modifier.padding(vertical = 4.dp)) {
        SummaryRow(title = stringResource(Localization.moonpay_payment_you_paid), value = summary.paid)
        SummaryRow(
            title = stringResource(Localization.moonpay_payment_you_receive),
            value = summary.receive,
            highlighted = true,
        )
        SummaryRow(
            title = stringResource(Localization.moonpay_payment_order_id),
            value = orderId ?: NO_ORDER_ID,
            onClick = orderId?.let { { clipboard.copy(it) } },
        )
    }
}

@Composable
private fun Fees(summary: MoonPayPaymentSummary) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val expandable = summary.fees.isNotEmpty()
    val rotation by animateFloatAsState(
        if (expanded) {
            EXPANDED_ROTATION
        } else {
            0f
        },
    )
    Group(modifier = Modifier.padding(vertical = 4.dp), shape = UIKit.shapes.medium) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = expandable) { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Localization.moonpay_payment_fees),
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.text.secondary,
                modifier = Modifier.weight(1f),
            )
            Text(text = summary.feesTotal, style = UIKit.typography.body2, color = UIKit.colorScheme.text.primary)
            if (expandable) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    painter = painterResource(UIKitIcon.ic_chevron_down_16),
                    contentDescription = null,
                    tint = UIKit.colorScheme.icon.tertiary,
                    modifier = Modifier.rotate(rotation),
                )
            }
        }
        if (expanded) {
            summary.fees.forEach { fee ->
                SummaryRow(title = stringResource(fee.kind.title), value = fee.amount)
            }
        }
    }
}

@Composable
private fun SummaryRow(
    title: String,
    value: String,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = title, style = UIKit.typography.body2, color = UIKit.colorScheme.text.secondary)
        Text(
            text = value,
            style = UIKit.typography.body2,
            color = if (highlighted) {
                UIKit.colorScheme.accent.green
            } else {
                UIKit.colorScheme.text.primary
            },
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun Group(modifier: Modifier = Modifier, shape: Shape = UIKit.shapes.large, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(UIKit.colorScheme.background.content)
            .then(modifier),
    ) {
        content()
    }
}

@Composable
private fun MoonPayPaymentStepText.lines(): Pair<String, String> {
    return when (this) {
        is MoonPayPaymentStepText.Started -> stringResource(Localization.moonpay_payment_started) to
            stringResource(Localization.moonpay_payment_authorised, paymentTitle, amount)
        is MoonPayPaymentStepText.Checking -> stringResource(Localization.moonpay_payment_started) to
            stringResource(Localization.moonpay_payment_checking_subtitle)
        is MoonPayPaymentStepText.Pending -> stringResource(Localization.moonpay_payment_pending) to if (cleared) {
            stringResource(Localization.moonpay_payment_cleared_subtitle)
        } else {
            stringResource(Localization.moonpay_payment_pending_subtitle)
        }
        is MoonPayPaymentStepText.Completed -> stringResource(Localization.moonpay_payment_completed) to
            stringResource(Localization.moonpay_payment_completed_subtitle, amount)
        is MoonPayPaymentStepText.StillProcessing -> stringResource(Localization.moonpay_payment_completed) to
            stringResource(Localization.moonpay_payment_still_processing_subtitle)
        is MoonPayPaymentStepText.Unknown -> stringResource(Localization.moonpay_payment_unknown_title) to
            stringResource(Localization.moonpay_payment_unknown_subtitle)
        is MoonPayPaymentStepText.Failed -> failure.lines()
    }
}

@Composable
private fun MoonPayPaymentFailure.lines(): Pair<String, String> {
    val (title, subtitle) = when (this) {
        MoonPayPaymentFailure.Declined ->
            Localization.moonpay_payment_declined_title to Localization.moonpay_payment_declined_subtitle
        MoonPayPaymentFailure.LimitReached ->
            Localization.moonpay_payment_daily_limit_title to Localization.moonpay_payment_daily_limit_subtitle
        MoonPayPaymentFailure.NotConfirmed ->
            Localization.moonpay_payment_not_confirmed_title to Localization.moonpay_payment_not_confirmed_subtitle
        MoonPayPaymentFailure.Unavailable,
        MoonPayPaymentFailure.NoResponse,
        -> Localization.moonpay_payment_unavailable_title to Localization.moonpay_payment_unavailable_subtitle
        MoonPayPaymentFailure.VerificationPending -> Localization.moonpay_payment_verification_pending_title to
            Localization.moonpay_payment_verification_pending_subtitle
        MoonPayPaymentFailure.VerificationUnavailable -> Localization.moonpay_payment_verification_failed_title to
            Localization.moonpay_payment_verification_failed_subtitle
        MoonPayPaymentFailure.QuoteRejected,
        MoonPayPaymentFailure.QuoteExpired,
        MoonPayPaymentFailure.Cancelled,
        MoonPayPaymentFailure.TransactionCancelled,
        MoonPayPaymentFailure.SignInRequired,
        MoonPayPaymentFailure.Unknown,
        -> Localization.moonpay_payment_failed_title to Localization.something_went_wrong
    }
    return stringResource(title) to stringResource(subtitle)
}

private val MoonPayPaymentRetry.title: Int
    get() = when (this) {
        MoonPayPaymentRetry.AnotherCard -> Localization.moonpay_payment_try_another_card
        MoonPayPaymentRetry.AnotherMethod -> Localization.moonpay_payment_try_another_method
        MoonPayPaymentRetry.ChangeAmount -> Localization.moonpay_payment_change_amount
        MoonPayPaymentRetry.Again -> Localization.moonpay_payment_try_again
    }

internal val MoonPayPaymentFeeKind.title: Int
    get() = when (this) {
        MoonPayPaymentFeeKind.MoonPay -> Localization.moonpay_payment_fee_moonpay
        // The key keeps its old name: its English changed, so the locales translate it again.
        MoonPayPaymentFeeKind.Ecosystem -> Localization.moonpay_payment_fee_keeper
        MoonPayPaymentFeeKind.Network -> Localization.moonpay_payment_fee_network
        MoonPayPaymentFeeKind.Other -> Localization.moonpay_payment_fee_other
    }

private val INDICATOR_SIZE = 44.dp
private val STEP_HEIGHT = 76.dp
private val STEP_TEXT_START = 76.dp
private const val TINT_ALPHA = 0.12f
private val STEP_ICON_SIZE = 28.dp
private const val EXPANDED_ROTATION = 180f
private const val NO_ORDER_ID = "—"

@Preview
@Composable
private fun MoonPayPaymentStartedPreview() {
    ThemedPreview {
        MoonPayPaymentContent(
            summary = PREVIEW_SUMMARY,
            steps = previewSteps(started = MoonPayPaymentStepStatus.Done, pending = MoonPayPaymentStepStatus.InProgress),
            orderId = "mp_7f3c9a21",
            showsDetails = true,
            retry = null,
            canClose = false,
            onClose = {},
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun MoonPayPaymentCompletedPreview() {
    ThemedPreview {
        MoonPayPaymentContent(
            summary = PREVIEW_SUMMARY,
            steps = previewSteps(
                started = MoonPayPaymentStepStatus.Done,
                pending = MoonPayPaymentStepStatus.Done,
                completed = MoonPayPaymentStepStatus.Done,
            ),
            orderId = "mp_7f3c9a21",
            showsDetails = true,
            retry = null,
            canClose = true,
            onClose = {},
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun MoonPayPaymentDeclinedPreview() {
    ThemedPreview {
        MoonPayPaymentContent(
            summary = PREVIEW_SUMMARY,
            steps = previewSteps(started = MoonPayPaymentStepStatus.Done).copy(
                pending = MoonPayPaymentStep(
                    MoonPayPaymentStepText.Failed(MoonPayPaymentFailure.Declined),
                    MoonPayPaymentStepStatus.Failed,
                ),
            ),
            orderId = "mp_7f3c9a21",
            showsDetails = false,
            retry = MoonPayPaymentRetry.AnotherCard,
            canClose = true,
            onClose = {},
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun MoonPayPaymentUnknownPreview() {
    ThemedPreview {
        MoonPayPaymentContent(
            summary = PREVIEW_SUMMARY,
            steps = previewSteps(
                started = MoonPayPaymentStepStatus.Done,
                pending = MoonPayPaymentStepStatus.Done,
            ).copy(
                completed = MoonPayPaymentStep(MoonPayPaymentStepText.Unknown, MoonPayPaymentStepStatus.Failed),
            ),
            orderId = "mp_7f3c9a21",
            showsDetails = true,
            retry = null,
            canClose = true,
            onClose = {},
            onRetry = {},
        )
    }
}

private val PREVIEW_SUMMARY = MoonPayPaymentSummary(
    paid = "$200.00",
    receive = "187.07 USDT",
    feesTotal = "$13.30",
    fees = listOf(
        MoonPayPaymentFee(MoonPayPaymentFeeKind.MoonPay, "$9.99"),
        MoonPayPaymentFee(MoonPayPaymentFeeKind.Network, "$3.31"),
    ),
)

private fun previewSteps(
    started: MoonPayPaymentStepStatus = MoonPayPaymentStepStatus.InProgress,
    pending: MoonPayPaymentStepStatus = MoonPayPaymentStepStatus.Waiting,
    completed: MoonPayPaymentStepStatus = MoonPayPaymentStepStatus.Waiting,
): MoonPayPaymentSteps {
    return MoonPayPaymentSteps(
        started = MoonPayPaymentStep(MoonPayPaymentStepText.Started("Visa ··4242", "$200.00"), started),
        pending = MoonPayPaymentStep(MoonPayPaymentStepText.Pending(cleared = pending == MoonPayPaymentStepStatus.Done), pending),
        completed = MoonPayPaymentStep(MoonPayPaymentStepText.Completed("187.07 USDT"), completed),
    )
}
