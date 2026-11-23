package com.tonapps.deposit.multicoin.screens.confirm

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonButtonCell
import ui.components.moon.cell.MoonTextContentCell
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.components.moon.tintedButtonColors
import ui.preview.ThemedPreview
import ui.theme.UIKit

@Composable
fun ValueDifferenceDialog(
    severity: PriceImpactSeverity,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    val router = rememberDialogNavigator { onClose() }
    MoonModalDialog(navigator = router) {
        ValueDifferenceContent(
            severity = severity,
            onConfirm = {
                onConfirm()
                router.close()
            },
            onClose = { router.close() },
        )
    }
}

@Composable
fun ValueDifferenceInfoSheet(onClose: () -> Unit) {
    SwapInfoSheet(
        title = stringResource(Localization.value_difference),
        text = stringResource(Localization.swap_value_difference_message),
        iconRes = null,
        onClose = onClose,
    )
}

@Composable
fun SwapInfoSheet(
    title: String,
    text: String,
    @DrawableRes iconRes: Int?,
    onClose: () -> Unit,
) {
    val router = rememberDialogNavigator { onClose() }
    MoonModalDialog(navigator = router) {
        SwapInfoContent(title = title, text = text, iconRes = iconRes, onClose = { router.close() })
    }
}

@Composable
fun PriceChangedDialog(
    text: String,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    val router = rememberDialogNavigator { onClose() }
    MoonModalDialog(navigator = router) {
        PriceChangedContent(
            text = text,
            onConfirm = {
                onConfirm()
                router.close()
            },
            onClose = { router.close() },
        )
    }
}

@Composable
private fun ColumnScope.ValueDifferenceContent(
    severity: PriceImpactSeverity,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    SwapWarningContent(
        danger = severity == PriceImpactSeverity.Danger,
        title = stringResource(Localization.value_difference),
        text = stringResource(Localization.swap_value_difference_message),
        confirmText = stringResource(Localization.swap_at_changed_price),
        iconSize = 84.dp,
        onConfirm = onConfirm,
        onClose = onClose,
    )
}

@Composable
private fun ColumnScope.PriceChangedContent(
    text: String,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    SwapWarningContent(
        danger = true,
        title = stringResource(Localization.swap_price_changed_title),
        text = text,
        confirmText = stringResource(Localization.swap_price_changed_confirm),
        iconSize = 84.dp,
        onConfirm = onConfirm,
        onClose = onClose,
    )
}

@Composable
fun ColumnScope.SwapInfoContent(
    title: String,
    text: String,
    @DrawableRes iconRes: Int?,
    onClose: () -> Unit,
) {
    MoonTopAppBarSimple(
        title = "",
        actionIconRes = UIKitIcon.ic_close_16,
        onActionClick = onClose,
        backgroundColor = Color.Transparent,
    )

    if (iconRes != null) {
        MoonItemIcon(
            painter = painterResource(iconRes),
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = UIKit.colorScheme.icon.secondary,
            size = 84.dp,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    MoonTextContentCell(
        title = title,
        description = text,
    )

    Spacer(modifier = Modifier.height(32.dp))

    MoonButtonCell(
        onClick = onClose,
        text = stringResource(Localization.got_it),
    )

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
internal fun ColumnScope.SwapWarningContent(
    danger: Boolean,
    title: String,
    text: String,
    confirmText: String,
    iconSize: Dp,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    MoonTopAppBarSimple(
        title = "",
        actionIconRes = UIKitIcon.ic_close_16,
        onActionClick = onClose,
        backgroundColor = Color.Transparent,
    )

    Column(
        modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        MoonItemIcon(
            painter = when (danger) {
                true -> painterResource(UIKitIcon.ic_exclamationmark_triangle_colorful_84)
                false -> painterResource(UIKitIcon.ic_exclamationmark_circle_colorful_84)
            },
            color = Color.Unspecified,
            size = iconSize,
        )

        Spacer(modifier = Modifier.height(12.dp))

        MoonTextContentCell(
            title = title,
            description = text,
            titleStyle = UIKit.typography.h2,
        )

        Spacer(modifier = Modifier.height(24.dp))
    }

    val accent = if (danger) {
        UIKit.colorScheme.accent.red
    } else {
        UIKit.colorScheme.accent.orange
    }
    MoonButtonCell(
        onClick = onConfirm,
        text = confirmText,
        colors = tintedButtonColors(accent),
    )

    MoonButtonCell(
        onClick = onClose,
        text = stringResource(Localization.back_to_swap),
        colors = ButtonColorsSecondary,
    )

    Spacer(modifier = Modifier.height(16.dp))
}

@Preview(heightDp = 1600)
@Composable
private fun ValueDifferenceWarningDialogPreview() {
    ThemedPreview {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ValueDifferenceContent(severity = PriceImpactSeverity.Warning, onConfirm = {}, onClose = {})
        }
    }
}

@Preview(heightDp = 1600)
@Composable
private fun ValueDifferenceDangerDialogPreview() {
    ThemedPreview {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ValueDifferenceContent(severity = PriceImpactSeverity.Danger, onConfirm = {}, onClose = {})
        }
    }
}

@Preview
@Composable
private fun ValueDifferenceInfoSheetPreview() {
    ThemedPreview {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SwapInfoContent(
                title = stringResource(Localization.value_difference),
                text = stringResource(Localization.swap_value_difference_message),
                iconRes = null,
                onClose = {},
            )
        }
    }
}

@Preview(heightDp = 1600)
@Composable
private fun PriceChangedDialogPreview() {
    ThemedPreview {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PriceChangedContent(
                text = "The rate changed while the swap was being sent. You’ll get 8.03 GRAM instead of 8.13 GRAM.",
                onConfirm = {},
                onClose = {},
            )
        }
    }
}
