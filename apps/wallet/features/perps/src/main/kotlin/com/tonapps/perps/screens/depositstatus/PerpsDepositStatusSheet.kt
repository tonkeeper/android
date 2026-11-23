package com.tonapps.perps.screens.depositstatus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonActionIcon
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.container.MoonSurface
import ui.components.moon.dialog.MoonAlertDialog
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.preview.ThemedPreview
import ui.theme.UIKit

private const val CONNECTOR_DONE_ALPHA = 0.32f
private const val ACCENT_BACKGROUND_ALPHA = 0.12f
private const val PROTOCOL = "Lighter"

private val stepHeight = 76.dp
private val stepIconSize = 44.dp
private val detailsRowHeight = 36.dp
private val headerButtonSize = 32.dp
private val connectorWidth = 2.dp
private val connectorHeight = 24.dp
private val connectorStart = 37.dp
private val connectorTop = 64.dp

@Composable
fun PerpsDepositStatusSheet(
    status: PerpsDepositStatusUi,
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)

    MoonModalDialog(navigator = navigator) {
        PerpsDepositStatusContent(status = status, onClose = { navigator.close() })
    }
}

@Composable
private fun PerpsDepositStatusContent(
    status: PerpsDepositStatusUi,
    onClose: () -> Unit,
) {
    var helpShown by remember { mutableStateOf(false) }
    MoonSurface {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            StatusHeader(header = status.header, onHelp = { helpShown = true }, onClose = onClose)
            StatusSteps(status = status)
            if (status.delayed) {
                MoonItemSubtitle(
                    text = stringResource(Localization.perps_deposit_status_delayed_note),
                    maxLines = Int.MAX_VALUE,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            StatusDetails(status = status)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    if (helpShown) {
        MoonAlertDialog(
            title = stringResource(Localization.perps_deposit_status_title),
            message = stringResource(Localization.perps_deposit_status_help),
            positiveButtonText = stringResource(Localization.ok),
            onDismiss = { helpShown = false },
            onPositiveClick = { helpShown = false },
        )
    }
}

@Composable
private fun StatusHeader(
    header: PerpsDepositStatusHeader?,
    onHelp: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 16.dp)
                .size(headerButtonSize)
                .clip(CircleShape)
                .background(UIKit.colorScheme.buttonSecondary.primaryBackground)
                .clickable(onClick = onHelp),
            contentAlignment = Alignment.Center,
        ) {
            MoonItemIcon(
                painter = painterResource(UIKitIcon.ic_question_16),
                size = 16.dp,
                color = UIKit.colorScheme.buttonSecondary.primaryForeground,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(start = 72.dp, top = 18.dp, end = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Localization.perps_deposit_status_title),
                style = UIKit.typography.h3,
                color = UIKit.colorScheme.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (header != null) {
                Text(
                    text = headerText(header),
                    style = UIKit.typography.body2.copy(fontWeight = FontWeight.Medium),
                    color = UIKit.colorScheme.text.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        MoonActionIcon(
            painter = painterResource(UIKitIcon.ic_close_16),
            onClick = onClose,
            tintColor = UIKit.colorScheme.buttonSecondary.primaryForeground,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp),
        )
    }
}

@Composable
private fun headerText(header: PerpsDepositStatusHeader): String = when (header) {
    is PerpsDepositStatusHeader.Eta -> stringResource(Localization.perps_deposit_status_eta, header.minutes)
    PerpsDepositStatusHeader.Delayed -> stringResource(Localization.perps_deposit_status_delayed)
    PerpsDepositStatusHeader.Completed -> stringResource(Localization.perps_deposit_status_completed)
    PerpsDepositStatusHeader.Failed -> stringResource(Localization.failed)
    PerpsDepositStatusHeader.Refunded -> stringResource(Localization.perps_deposit_status_refunded)
    PerpsDepositStatusHeader.Unconfirmed -> stringResource(Localization.perps_deposit_status_unconfirmed)
}

@Composable
private fun StatusSteps(status: PerpsDepositStatusUi) {
    val doneConnector = UIKit.colorScheme.accent.green.copy(alpha = CONNECTOR_DONE_ALPHA)
    val pendingConnector = UIKit.colorScheme.background.contentTint
    val connectors = status.steps.dropLast(1).map { step ->
        if (step.state == PerpsDepositStepState.DONE) {
            doneConnector
        } else {
            pendingConnector
        }
    }
    val sent = stringResource(Localization.perps_deposit_step_sending_details, status.sourceFormat, status.network)
    MoonBundleCell {
        Column(
            modifier = Modifier.drawBehind {
                connectors.forEachIndexed { index, color ->
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(connectorStart.toPx(), connectorTop.toPx() + stepHeight.toPx() * index),
                        size = Size(connectorWidth.toPx(), connectorHeight.toPx()),
                        cornerRadius = CornerRadius(connectorWidth.toPx() / 2f),
                    )
                }
            },
        ) {
            if (status.steps.isEmpty()) {
                StatusStep(
                    state = if (status.inProgress) {
                        PerpsDepositStepState.CURRENT
                    } else {
                        PerpsDepositStepState.FAILED
                    },
                    refunded = status.refunded,
                    title = status.header
                        ?.takeUnless { status.inProgress }
                        ?.let { headerText(it) }
                        ?: stringResource(Localization.perps_deposit_in_progress),
                    subtitle = sent,
                )
            }
            status.steps.forEach { step ->
                StatusStep(
                    state = step.state,
                    refunded = status.refunded,
                    title = stepTitle(step, status.sourceIsUsdc),
                    subtitle = stepCaption(step, status, sent),
                )
            }
        }
    }
}

@Composable
private fun stepTitle(step: PerpsDepositStepUi, sourceIsUsdc: Boolean): String {
    if (step.state == PerpsDepositStepState.FAILED) {
        return stringResource(
            when (step.kind) {
                PerpsDepositStepKind.SENDING -> Localization.perps_deposit_step_send_failed
                PerpsDepositStepKind.CONVERTING -> if (sourceIsUsdc) {
                    Localization.perps_deposit_step_transfer_failed
                } else {
                    Localization.perps_deposit_step_convert_failed
                }

                PerpsDepositStepKind.ADDING -> Localization.perps_deposit_step_add_failed
            },
        )
    }
    return stringResource(
        when (step.kind) {
            PerpsDepositStepKind.SENDING -> Localization.perps_deposit_step_sending
            PerpsDepositStepKind.CONVERTING -> when {
                sourceIsUsdc && step.state == PerpsDepositStepState.DONE -> Localization.perps_deposit_step_transferred
                sourceIsUsdc -> Localization.perps_deposit_status_transferring
                step.state == PerpsDepositStepState.DONE -> Localization.perps_deposit_step_converted
                else -> Localization.perps_deposit_status_converting
            }

            PerpsDepositStepKind.ADDING -> if (step.state == PerpsDepositStepState.DONE) {
                Localization.perps_deposit_step_confirmed
            } else {
                Localization.perps_deposit_status_adding
            }
        },
    )
}

@Composable
private fun stepCaption(step: PerpsDepositStepUi, status: PerpsDepositStatusUi, sent: String): String {
    if (step.state == PerpsDepositStepState.FAILED) {
        return if (status.refunded) {
            stringResource(Localization.perps_deposit_step_returned, status.sourceFormat)
        } else {
            stringResource(Localization.failed)
        }
    }
    return when (step.kind) {
        PerpsDepositStepKind.SENDING -> sent
        PerpsDepositStepKind.CONVERTING -> if (status.sourceIsUsdc) {
            stringResource(Localization.perps_deposit_step_transferring_details, status.network, PROTOCOL)
        } else {
            stringResource(Localization.perps_deposit_step_converting_details, status.sourceSymbol, PROTOCOL)
        }

        PerpsDepositStepKind.ADDING -> {
            val received = approximate(status)
            if (status.delayed) {
                stringResource(Localization.perps_deposit_step_amount_usdc, received)
            } else {
                received
            }
        }
    }
}

@Composable
private fun approximate(status: PerpsDepositStatusUi): String = if (status.approximate) {
    stringResource(Localization.perps_deposit_approx_value, status.receivedFormat)
} else {
    status.receivedFormat
}

@Composable
private fun StatusStep(
    state: PerpsDepositStepState,
    refunded: Boolean,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(stepHeight)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepIcon(state = state, refunded = refunded)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
        ) {
            Text(
                text = title,
                style = UIKit.typography.label1,
                color = if (state == PerpsDepositStepState.WAITING) {
                    UIKit.colorScheme.text.secondary
                } else {
                    UIKit.colorScheme.text.primary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = UIKit.typography.body2,
                color = when (state) {
                    PerpsDepositStepState.FAILED -> UIKit.colorScheme.accent.orange
                    PerpsDepositStepState.WAITING -> UIKit.colorScheme.text.tertiary
                    PerpsDepositStepState.CURRENT,
                    PerpsDepositStepState.DONE -> UIKit.colorScheme.text.secondary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StepIcon(state: PerpsDepositStepState, refunded: Boolean) {
    val accent = when (state) {
        PerpsDepositStepState.DONE -> UIKit.colorScheme.accent.green
        PerpsDepositStepState.FAILED -> UIKit.colorScheme.accent.orange
        PerpsDepositStepState.WAITING,
        PerpsDepositStepState.CURRENT -> null
    }
    val background = when (state) {
        PerpsDepositStepState.WAITING -> UIKit.colorScheme.buttonTertiary.primaryBackgroundDisable
        PerpsDepositStepState.CURRENT,
        PerpsDepositStepState.DONE,
        PerpsDepositStepState.FAILED -> UIKit.colorScheme.background.contentTint
    }
    Box(
        modifier = Modifier
            .size(stepIconSize)
            .clip(CircleShape)
            .background(background)
            .background(accent?.copy(alpha = ACCENT_BACKGROUND_ALPHA) ?: Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            PerpsDepositStepState.DONE -> MoonItemIcon(
                painter = painterResource(UIKitIcon.ic_checkmark_circle_28),
                size = 28.dp,
                color = UIKit.colorScheme.accent.green,
            )

            PerpsDepositStepState.FAILED -> MoonItemIcon(
                painter = painterResource(
                    if (refunded) {
                        UIKitIcon.ic_return_28
                    } else {
                        UIKitIcon.ic_exclamationmark_circle_28
                    },
                ),
                size = 28.dp,
                color = UIKit.colorScheme.accent.orange,
            )

            PerpsDepositStepState.CURRENT -> PerpsDepositSpinner(
                size = 28.dp,
                radius = 10.5.dp,
                strokeWidth = 3.dp,
            )

            PerpsDepositStepState.WAITING -> Unit
        }
    }
}

@Composable
private fun StatusDetails(status: PerpsDepositStatusUi) {
    MoonBundleCell {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            DetailsRow(
                label = stringResource(Localization.perps_deposit_status_you_send),
                value = stringResource(Localization.perps_deposit_source_pattern, status.sourceFormat, status.network),
            )
            DetailsRow(
                label = stringResource(Localization.perps_deposit_status_you_get),
                value = approximate(status),
            )
            DetailsRow(
                label = stringResource(Localization.protocol),
                value = PROTOCOL,
            )
        }
    }
}

@Composable
private fun DetailsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = detailsRowHeight)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.secondary,
            maxLines = 1,
        )
        Text(
            text = value,
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

private val previewStatus = PerpsDepositStatusUi(
    depositId = "1",
    header = PerpsDepositStatusHeader.Eta(2),
    steps = listOf(
        PerpsDepositStepUi(PerpsDepositStepKind.SENDING, PerpsDepositStepState.DONE),
        PerpsDepositStepUi(PerpsDepositStepKind.CONVERTING, PerpsDepositStepState.CURRENT),
        PerpsDepositStepUi(PerpsDepositStepKind.ADDING, PerpsDepositStepState.WAITING),
    ),
    inProgress = true,
    refunded = false,
    delayed = false,
    sourceFormat = "350 GRAM",
    sourceSymbol = "GRAM",
    sourceIsUsdc = false,
    network = "TON",
    receivedFormat = "$707.19",
    approximate = true,
)

@Preview
@Composable
private fun PerpsDepositStatusConvertingPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsDepositStatusContent(status = previewStatus, onClose = {})
    }
}

@Preview
@Composable
private fun PerpsDepositStatusTransferringPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsDepositStatusContent(
            status = previewStatus.copy(
                sourceFormat = "350 USDC",
                sourceSymbol = "USDC",
                sourceIsUsdc = true,
                network = "Arbitrum",
            ),
            onClose = {},
        )
    }
}

@Preview
@Composable
private fun PerpsDepositStatusDelayedPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsDepositStatusContent(
            status = previewStatus.copy(header = PerpsDepositStatusHeader.Delayed, delayed = true),
            onClose = {},
        )
    }
}

@Preview
@Composable
private fun PerpsDepositStatusCompletedPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsDepositStatusContent(
            status = previewStatus.copy(
                header = PerpsDepositStatusHeader.Completed,
                steps = PerpsDepositStepKind.entries.map { PerpsDepositStepUi(it, PerpsDepositStepState.DONE) },
                inProgress = false,
            ),
            onClose = {},
        )
    }
}

@Preview
@Composable
private fun PerpsDepositStatusRefundedPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsDepositStatusContent(
            status = previewStatus.copy(
                header = PerpsDepositStatusHeader.Refunded,
                steps = listOf(
                    PerpsDepositStepUi(PerpsDepositStepKind.SENDING, PerpsDepositStepState.DONE),
                    PerpsDepositStepUi(PerpsDepositStepKind.CONVERTING, PerpsDepositStepState.FAILED),
                    PerpsDepositStepUi(PerpsDepositStepKind.ADDING, PerpsDepositStepState.WAITING),
                ),
                inProgress = false,
                refunded = true,
            ),
            onClose = {},
        )
    }
}

@Preview
@Composable
private fun PerpsDepositStatusWithoutStepPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsDepositStatusContent(
            status = previewStatus.copy(steps = emptyList(), header = null),
            onClose = {},
        )
    }
}
