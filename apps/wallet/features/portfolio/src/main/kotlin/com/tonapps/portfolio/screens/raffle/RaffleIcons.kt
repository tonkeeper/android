package com.tonapps.portfolio.screens.raffle

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import ui.components.moon.MoonItemIcon
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit

private class RaffleIconSpec(@DrawableRes val glyph: Int, val accent: (@Composable () -> Color)?)

/**
 * Backend-driven icon ids -> tintable glyph + accent color, rendered on a
 * 12%-alpha circle (same pattern as FinishSetupCard). Unknown ids must never
 * crash: they fall back to the ticket glyph on the neutral circle.
 */
private fun raffleIconSpec(iconId: String?): RaffleIconSpec = when (iconId) {
    "keeper" -> RaffleIconSpec(UIKitIcon.ic_raffle_keeper_44) { UIKit.colorScheme.accent.blue }
    "trusted" -> RaffleIconSpec(UIKitIcon.ic_raffle_trusted_44) { UIKit.colorScheme.accent.green }
    "senior" -> RaffleIconSpec(UIKitIcon.ic_raffle_senior_44) { UIKit.colorScheme.accent.orange }
    "legend" -> RaffleIconSpec(UIKitIcon.ic_raffle_legend_44) { UIKit.colorScheme.accent.purple }
    "swap" -> RaffleIconSpec(UIKitIcon.ic_raffle_swap_44) { UIKit.colorScheme.accent.green }
    "ticket", "mystery_raffle" -> RaffleIconSpec(UIKitIcon.ic_raffle_ticket_44) { UIKit.colorScheme.accent.blue }
    "zero_fee" -> RaffleIconSpec(UIKitIcon.ic_raffle_zap_44) { UIKit.colorScheme.accent.green }
    "migrate" -> RaffleIconSpec(UIKitIcon.ic_tray_arrow_down_28) { UIKit.colorScheme.accent.blue }
    "metamask" -> RaffleIconSpec(UIKitIcon.ic_key_28) { UIKit.colorScheme.accent.orange }
    else -> RaffleIconSpec(UIKitIcon.ic_ticket_28, accent = null)
}

/**
 * 44dp list icon for tasks/milestones/benefits/history.
 * [grayscale] renders the desaturated variant used in the tickets history.
 */
@Composable
fun RaffleIcon(
    iconId: String?,
    modifier: Modifier = Modifier,
    grayscale: Boolean = false,
) {
    val spec = raffleIconSpec(iconId)
    val tint = when {
        grayscale -> UIKit.colorScheme.icon.secondary
        else -> spec.accent?.invoke() ?: UIKit.colorScheme.icon.secondary
    }
    val background = when {
        grayscale || spec.accent == null -> UIKit.colorScheme.background.contentTint
        else -> tint.copy(alpha = 0.12f)
    }
    // MoonActionIcon is close, but it can't size the glyph inside the circle:
    // raffle glyphs are drawn on the full 44x44 grid while system 28-grid
    // glyphs render at 24dp per Figma — so the circle stays local.
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        MoonItemIcon(
            painter = painterResource(spec.glyph),
            size = if (spec.glyph in fullGridGlyphs) { 44.dp } else { 24.dp },
            color = tint,
        )
    }
}

private val fullGridGlyphs = setOf(
    UIKitIcon.ic_raffle_keeper_44,
    UIKitIcon.ic_raffle_trusted_44,
    UIKitIcon.ic_raffle_senior_44,
    UIKitIcon.ic_raffle_legend_44,
    UIKitIcon.ic_raffle_swap_44,
    UIKitIcon.ic_raffle_ticket_44,
    UIKitIcon.ic_raffle_zap_44,
)

@DrawableRes
fun rafflePrizeIconResOrNull(prizeId: String?): Int? = when (prizeId) {
    "battery_big" -> UIKitIcon.ic_battery_100_44
    "battery_medium" -> UIKitIcon.ic_battery_50_44
    "battery_small" -> UIKitIcon.ic_battery_25_44
    else -> null
}

/** Small tintable glyph for the compact wallet row, hero badge and card labels. */
@DrawableRes
fun raffleBadgeIconResOrNull(iconId: String?): Int? = when (iconId) {
    "clock" -> UIKitIcon.ic_clock_28
    "flash", "zero_fee" -> UIKitIcon.ic_flash_24
    "fire" -> UIKitIcon.ic_fire_badge_16
    "checkmark" -> UIKitIcon.ic_checkmark_circle_32
    "ticket", "mystery_raffle" -> UIKitIcon.ic_ticket_28
    else -> null
}

@DrawableRes
fun raffleBadgeIconRes(iconId: String?): Int =
    raffleBadgeIconResOrNull(iconId) ?: UIKitIcon.ic_ticket_28

@Preview
@Composable
private fun RaffleIconPreview() {
    ThemedPreview(isDarkOnly = true) {
        Row {
            RaffleIcon("keeper")
            RaffleIcon("trusted")
            RaffleIcon("senior")
            RaffleIcon("legend")
            RaffleIcon("migrate")
            RaffleIcon("swap", grayscale = true)
            RaffleIcon(null)
        }
    }
}
