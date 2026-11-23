package com.tonapps.perps.screens.depositstatus

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.perps.PerpsDepositPendingStep
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.components.moon.MoonChevronRight
import ui.components.moon.MoonItemIcon
import ui.components.moon.cell.MoonCardCell
import ui.preview.ThemedPreview
import ui.theme.UIKit

private const val PLAQUE_DELAY_MS = 300L
private const val PLAQUE_SLIDE_MS = 700
private const val PLAQUE_FADE_MS = 220
private const val PLAQUE_HIDE_MS = 400
private const val CONTENT_DELAY_MS = 520L
private const val CONTENT_STEP_MS = 60L
private const val CONTENT_ELEMENTS = 4

private val plaqueOverlap = 12.dp
private val plaqueHeight = 52.dp
private val plaqueSlide = 44.dp
private val plaqueInset = 32.dp
private val plaqueShape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)

@Stable
private class PerpsDepositPlaqueMotion(visible: Boolean) {

    val progress = Animatable(visible.toFraction())
    val alpha = Animatable(visible.toFraction())
    val content = List(CONTENT_ELEMENTS) { PerpsDepositRevealMotion(visible) }

    suspend fun appear(motion: Boolean) = coroutineScope {
        if (!motion) {
            content.forEach { it.show() }
            progress.snapTo(1f)
            alpha.snapTo(0f)
            alpha.animateTo(1f, tween(PERPS_DEPOSIT_REDUCED_FADE_MS))
            return@coroutineScope
        }
        content.forEachIndexed { index, element ->
            launch { element.reveal(CONTENT_DELAY_MS + CONTENT_STEP_MS * index) }
        }
        progress.snapTo(0f)
        alpha.snapTo(0f)
        delay(PLAQUE_DELAY_MS)
        launch { alpha.animateTo(1f, tween(PLAQUE_FADE_MS, easing = perpsDepositFadeEasing)) }
        progress.animateTo(1f, tween(PLAQUE_SLIDE_MS, easing = perpsDepositSlideEasing))
    }

    suspend fun disappear(motion: Boolean) = coroutineScope {
        if (!motion) {
            alpha.animateTo(0f, tween(PERPS_DEPOSIT_REDUCED_FADE_MS))
            progress.snapTo(0f)
            return@coroutineScope
        }
        launch { alpha.animateTo(0f, tween(PLAQUE_FADE_MS, easing = perpsDepositFadeEasing)) }
        progress.animateTo(0f, tween(PLAQUE_HIDE_MS, easing = perpsDepositSlideEasing))
    }
}

@Composable
internal fun PerpsDepositPlaqueBlock(
    row: PerpsDepositRowUi?,
    revealOnStart: Boolean,
    onClick: (PerpsDepositRowUi) -> Unit,
    modifier: Modifier = Modifier,
    card: @Composable () -> Unit,
) {
    val motionEnabled = !rememberAnimationsDisabled()
    val motion = remember { PerpsDepositPlaqueMotion(visible = row != null && !revealOnStart) }
    var shown by remember { mutableStateOf(row) }
    if (row != null) {
        shown = row
    }
    val visible = row != null

    LaunchedEffect(visible) {
        if (visible && motion.progress.value < 1f) {
            motion.appear(motionEnabled)
        } else if (!visible && motion.progress.value > 0f) {
            motion.disappear(motionEnabled)
            shown = null
        } else if (visible) {
            motion.content.forEach { it.show() }
        }
    }

    Layout(
        modifier = modifier,
        content = {
            card()
            val current = shown
            if (current != null) {
                PerpsDepositPlaque(
                    row = current,
                    reveal = motion.content,
                    onClick = onClick,
                    modifier = Modifier
                        .padding(horizontal = plaqueInset)
                        .graphicsLayer {
                            alpha = motion.alpha.value
                            translationY = -plaqueSlide.toPx() * (1f - motion.progress.value)
                        },
                )
            }
        },
    ) { measurables, constraints ->
        val cardPlaceable = measurables[0].measure(constraints)
        val plaquePlaceable = measurables.getOrNull(1)?.measure(constraints.copy(minHeight = 0))
        val overlap = plaqueOverlap.roundToPx()
        val extra = plaquePlaceable?.let { (it.height - overlap) * motion.progress.value }?.toInt() ?: 0
        layout(cardPlaceable.width, cardPlaceable.height + extra.coerceAtLeast(0)) {
            plaquePlaceable?.place(0, cardPlaceable.height - overlap)
            cardPlaceable.place(0, 0)
        }
    }
}

@Composable
private fun PerpsDepositPlaque(
    row: PerpsDepositRowUi,
    reveal: List<PerpsDepositRevealMotion>,
    onClick: (PerpsDepositRowUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = plaqueHeight)
            .clip(plaqueShape)
            .background(UIKit.colorScheme.background.contentTint)
            .clickable { onClick(row) }
            .padding(start = 16.dp, top = 20.dp, end = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlaqueIcon(row = row, modifier = Modifier.perpsDepositReveal(reveal[0]))
        Text(
            text = plaqueText(row),
            style = UIKit.typography.label2,
            color = UIKit.colorScheme.text.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .perpsDepositReveal(reveal[1]),
        )
        val etaMinutes = (row as? PerpsDepositRowUi.Step)?.etaMinutes
        if (etaMinutes != null) {
            Text(
                text = stringResource(Localization.perps_deposit_status_eta, etaMinutes),
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.text.secondary,
                maxLines = 1,
                modifier = Modifier.perpsDepositReveal(reveal[2]),
            )
        }
        if (row !is PerpsDepositRowUi.Completed) {
            Row(modifier = Modifier.perpsDepositReveal(reveal[3])) {
                MoonChevronRight()
            }
        }
    }
}

@Composable
private fun PlaqueIcon(row: PerpsDepositRowUi, modifier: Modifier = Modifier) {
    val step = when (row) {
        is PerpsDepositRowUi.Step -> row.step
        is PerpsDepositRowUi.Delayed -> row.step
        is PerpsDepositRowUi.Problem,
        PerpsDepositRowUi.Multiple,
        PerpsDepositRowUi.Problems,
        is PerpsDepositRowUi.Completed -> null
    }
    when {
        row is PerpsDepositRowUi.Completed -> MoonItemIcon(
            painter = painterResource(UIKitIcon.ic_checkmark_circle_16),
            size = 16.dp,
            color = UIKit.colorScheme.accent.green,
            modifier = modifier,
        )

        row is PerpsDepositRowUi.Problem -> MoonItemIcon(
            painter = painterResource(UIKitIcon.ic_exclamationmark_circle_16),
            size = 16.dp,
            color = UIKit.colorScheme.accent.orange,
            modifier = modifier,
        )

        row is PerpsDepositRowUi.Problems -> MoonItemIcon(
            painter = painterResource(UIKitIcon.ic_tray_arrow_down_28),
            size = 16.dp,
            color = UIKit.colorScheme.icon.secondary,
            modifier = modifier,
        )

        step != null -> PerpsDepositStepsLoader(step = step.kind().ordinal, modifier = modifier)
        else -> PerpsDepositSpinner(modifier = modifier)
    }
}

@Composable
private fun plaqueText(row: PerpsDepositRowUi): String = when (row) {
    is PerpsDepositRowUi.Step -> when (row.step) {
        PerpsDepositPendingStep.SENDING -> stringResource(Localization.perps_deposit_status_sending, row.sourceFormat)
        PerpsDepositPendingStep.CONVERTING -> if (row.sourceIsUsdc) {
            stringResource(Localization.perps_deposit_status_transferring)
        } else {
            stringResource(Localization.perps_deposit_status_converting)
        }

        PerpsDepositPendingStep.ADDING -> stringResource(Localization.perps_deposit_status_adding)
        null -> stringResource(Localization.perps_deposit_in_progress)
    }

    is PerpsDepositRowUi.Delayed -> stringResource(Localization.perps_deposit_status_delayed)
    is PerpsDepositRowUi.Problem -> if (row.unconfirmed) {
        stringResource(Localization.perps_deposit_status_unconfirmed)
    } else {
        stringResource(Localization.perps_deposit_failed)
    }

    PerpsDepositRowUi.Multiple -> stringResource(Localization.perps_deposit_status_multiple)
    PerpsDepositRowUi.Problems -> stringResource(Localization.perps_deposit_statuses_title)
    is PerpsDepositRowUi.Completed -> row.amountFormat
        ?.let { stringResource(Localization.perps_deposit_status_credited, it) }
        ?: stringResource(Localization.perps_deposit_completed)
}

private val previewRows = listOf(
    PerpsDepositRowUi.Step("1", PerpsDepositPendingStep.SENDING, "350 GRAM", 2, sourceIsUsdc = false),
    PerpsDepositRowUi.Step("1", PerpsDepositPendingStep.CONVERTING, "350 GRAM", 1, sourceIsUsdc = false),
    PerpsDepositRowUi.Step("1", PerpsDepositPendingStep.CONVERTING, "350 USDC", 1, sourceIsUsdc = true),
    PerpsDepositRowUi.Step("1", PerpsDepositPendingStep.ADDING, "350 GRAM", 1, sourceIsUsdc = false),
    PerpsDepositRowUi.Step("1", null, "350 GRAM", null, sourceIsUsdc = false),
    PerpsDepositRowUi.Delayed("1", PerpsDepositPendingStep.CONVERTING),
    PerpsDepositRowUi.Problem("1", unconfirmed = false),
    PerpsDepositRowUi.Problem("1", unconfirmed = true),
    PerpsDepositRowUi.Multiple,
    PerpsDepositRowUi.Problems,
    PerpsDepositRowUi.Completed(setOf("1"), "+$707.19"),
    PerpsDepositRowUi.Completed(setOf("1", "2"), null),
)

@Preview
@Composable
private fun PerpsDepositPlaquePreview() {
    ThemedPreview(isDarkOnly = true) {
        Column {
            previewRows.forEach { row ->
                PerpsDepositPlaqueBlock(row = row, revealOnStart = false, onClick = {}) {
                    MoonCardCell(title = "$0.00", subtitle = "Perps balance")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
