package com.tonapps.perps.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLabelDefault
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.container.MoonSurface
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit

@Composable
fun PerpsSharePositionDialog(
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)

    MoonModalDialog(navigator = navigator) {
        PerpsSharePositionDialogBody(
            onShare = {},
            onClose = { navigator.close() },
        )
    }
}

@Composable
private fun PerpsSharePositionDialogBody(
    isProfit: Boolean = true,
    onShare: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    MoonTopAppBarSimple(
        title = "Share position", // TODO Str
        actionIconRes = UIKitIcon.ic_close_16,
        onActionClick = onClose,
    )

    MoonSurface {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            SharePositionCard(isProfit = isProfit)

            Spacer(Modifier.height(16.dp))

            MoonAccentButton(
                modifier = Modifier.fillMaxWidth(),
                text = "Share", // TODO Str
                size = ButtonSizeLarge,
                buttonColors = ButtonColorsSecondary,
                icon = {
                    MoonItemIcon(
                        painter = painterResource(UIKitIcon.ic_share_16),
                        size = 16.dp,
                        color = UIKit.colorScheme.buttonSecondary.primaryForeground,
                    )
                },
                onClick = onShare,
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SharePositionCard(
    isProfit: Boolean,
) {
    val accentColor = if (isProfit) {
        UIKit.colorScheme.accent.green
    } else {
        UIKit.colorScheme.accent.red
    }
    val glowBrush = remember(accentColor) {
        object : ShaderBrush() {
            override fun createShader(size: Size): Shader {
                return RadialGradientShader(
                    center = Offset(x = size.width * 0.85f, y = size.height * 1.1f),
                    radius = size.height * 1.3f,
                    colors = listOf(
                        accentColor.copy(alpha = 0.8f),
                        accentColor.copy(alpha = 0f),
                    ),
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(UIKit.shapes.large)
            .background(UIKit.colorScheme.background.content)
            .background(glowBrush),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            MoonItemImage(
                image = "",
                size = 56.dp,
            )

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Bitcoin",
                    style = UIKit.typography.h3,
                    color = UIKit.colorScheme.text.primary,
                )
                Spacer(Modifier.width(8.dp))
                MoonLabel("40X", colors = MoonLabelDefault.grey())
                Spacer(Modifier.width(6.dp))
                MoonLabel("LONG", colors = MoonLabelDefault.grey())
            }

            Spacer(Modifier.height(4.dp))

            val pnlText = if (isProfit) {
                "+128.97%"
            } else {
                "−128.97%"
            }
            Text(
                text = pnlText,
                style = UIKit.typography.h1,
                color = accentColor,
            )

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                SharePositionProperty(
                    title = "Entry", // TODO Str
                    value = "$66 541",
                )
                if (isProfit) {
                    SharePositionProperty(
                        title = "Current", // TODO Str
                        value = "$120 541",
                    )
                } else {
                    SharePositionProperty(
                        title = "Close", // TODO Str
                        value = "$35 541",
                    )
                }
                SharePositionProperty(
                    title = "Date", // TODO Str
                    value = "25 Jan, 14:10",
                )
            }

            Spacer(Modifier.height(64.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                MoonItemIcon(
                    painter = painterResource(UIKitIcon.ic_logo_128),
                    size = 20.dp,
                    color = Color.White,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Keeper",
                    style = UIKit.typography.label1,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun SharePositionProperty(
    title: String,
    value: String,
) {
    Column {
        Text(
            text = title,
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.secondary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = UIKit.typography.label1,
            color = UIKit.colorScheme.text.primary,
        )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun PerpsSharePositionDialogProfitPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsSharePositionDialogBody(isProfit = true)
    }
}

@Preview(showSystemUi = true)
@Composable
private fun PerpsSharePositionDialogLossPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsSharePositionDialogBody(isProfit = false)
    }
}
