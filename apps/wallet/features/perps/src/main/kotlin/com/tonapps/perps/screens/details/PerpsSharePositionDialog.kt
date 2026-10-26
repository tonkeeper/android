package com.tonapps.perps.screens.details

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonapps.async.Async
import com.tonapps.extensions.retrieveUri
import com.tonapps.log.L
import com.tonapps.perps.data.PERPS_EMPTY_VALUE
import com.tonapps.perps.data.PerpsPositionSide
import com.tonapps.perps.data.bidiIsolate
import com.tonapps.perps.data.formatSignedPercent
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.perps.screens.markets.PerpsAssetIcon
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLabelDefault
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.container.MoonSurface
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit
import ui.utils.uppercased
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PerpsShareCard(
    val name: String,
    val symbol: String,
    val iconUrl: String?,
    val side: PerpsPositionSide,
    val leverage: Int?,
    val roiPct: BigDecimal?,
    val isProfit: Boolean,
    val entryPrice: BigDecimal?,
    val currentPrice: BigDecimal?,
    val date: Long,
    val closePrice: BigDecimal? = null,
)

@Composable
fun PerpsSharePositionDialog(
    card: PerpsShareCard,
    onShared: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    val chooserTitle = stringResource(Localization.share)
    var sharing by remember { mutableStateOf(false) }

    MoonModalDialog(navigator = navigator) {
        PerpsSharePositionDialogBody(
            card = card,
            graphicsLayer = graphicsLayer,
            shareEnabled = !sharing,
            onShare = {
                sharing = true
                scope.launch {
                    val uri = try {
                        val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                        val file = withContext(Async.Io) { writeCard(context.cacheDir, bitmap) }
                        retrieveUri(context, file)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        L.e(e)
                        null
                    }
                    if (uri != null) {
                        val intent = Intent(Intent.ACTION_SEND)
                            .setType(SHARE_MIME_TYPE)
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(intent, chooserTitle))
                        sharing = false
                        onShared(true)
                    } else {
                        sharing = false
                        onShared(false)
                    }
                }
            },
            onClose = { navigator.close() },
        )
    }
}

private fun writeCard(cacheDir: File, bitmap: Bitmap): File {
    val directory = File(cacheDir, SHARE_DIRECTORY).apply { mkdirs() }
    val file = File(directory, SHARE_FILE_NAME)
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }

    return file
}

@Composable
private fun PerpsSharePositionDialogBody(
    card: PerpsShareCard,
    graphicsLayer: GraphicsLayer?,
    shareEnabled: Boolean,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    MoonTopAppBarSimple(
        title = stringResource(Localization.perps_share_position_title),
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
            SharePositionCard(card = card, graphicsLayer = graphicsLayer)

            Spacer(Modifier.height(24.dp))

            MoonAccentButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(Localization.share),
                size = ButtonSizeLarge,
                buttonColors = ButtonColorsSecondary,
                enabled = shareEnabled,
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
    card: PerpsShareCard,
    graphicsLayer: GraphicsLayer?,
) {
    val accentColor = if (card.isProfit) {
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
    val dateText = remember(card.date) {
        SimpleDateFormat(SHARE_DATE_PATTERN, Locale.getDefault()).format(Date(card.date))
    }
    val sideText = stringResource(
        if (card.side == PerpsPositionSide.LONG) {
            Localization.perps_long
        } else {
            Localization.perps_short
        }
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (graphicsLayer != null) {
                    Modifier.drawWithContent {
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(graphicsLayer)
                    }
                } else {
                    Modifier
                }
            )
            .clip(UIKit.shapes.large)
            .background(UIKit.colorScheme.background.content)
            .background(glowBrush),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            PerpsAssetIcon(
                symbol = card.symbol,
                iconUrl = card.iconUrl,
                size = 56.dp,
            )

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.name,
                    style = UIKit.typography.h3,
                    color = UIKit.colorScheme.text.primary,
                )
                card.leverage?.let { leverage ->
                    Spacer(Modifier.width(8.dp))
                    MoonLabel(
                        text = stringResource(Localization.perps_leverage_pattern, leverage),
                        colors = MoonLabelDefault.grey(),
                    )
                }
                Spacer(Modifier.width(6.dp))
                MoonLabel(text = sideText.uppercased(), colors = MoonLabelDefault.grey())
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = card.roiPct?.let { formatSignedPercent(it) } ?: PERPS_EMPTY_VALUE,
                style = UIKit.typography.h1.copy(
                    fontSize = 46.sp,
                    lineHeight = 40.sp,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Proportional,
                        trim = LineHeightStyle.Trim.None,
                    ),
                ),
                color = accentColor,
            )

            Spacer(Modifier.height(28.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                SharePositionProperty(
                    title = stringResource(Localization.perps_share_entry),
                    value = card.entryPrice?.let { bidiIsolate(formatUsdAmount(it)) } ?: PERPS_EMPTY_VALUE,
                )
                if (card.closePrice != null) {
                    SharePositionProperty(
                        title = stringResource(Localization.perps_share_close),
                        value = bidiIsolate(formatUsdAmount(card.closePrice)),
                    )
                } else {
                    SharePositionProperty(
                        title = stringResource(Localization.perps_share_current),
                        value = card.currentPrice?.let { bidiIsolate(formatUsdAmount(it)) } ?: PERPS_EMPTY_VALUE,
                    )
                }
                SharePositionProperty(
                    title = stringResource(Localization.perps_share_date),
                    value = dateText,
                )
            }

            Spacer(Modifier.height(64.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(UIKitIcon.ic_keeper_mark),
                    contentDescription = null,
                    modifier = Modifier.size(19.4.dp),
                    tint = UIKit.colorScheme.text.primary,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    painter = painterResource(UIKitIcon.ic_keeper_wordmark),
                    contentDescription = null,
                    modifier = Modifier.size(width = 73.dp, height = 21.dp),
                    tint = UIKit.colorScheme.text.primary,
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
            style = UIKit.typography.body1,
            color = UIKit.colorScheme.text.secondary,
        )
        Text(
            text = value,
            style = UIKit.typography.label1,
            color = UIKit.colorScheme.text.primary,
        )
    }
}

private const val SHARE_MIME_TYPE = "image/png"
private const val SHARE_DIRECTORY = "shared"
private const val SHARE_FILE_NAME = "perps_position.png"
private const val SHARE_DATE_PATTERN = "d MMM, HH:mm"
private const val PNG_QUALITY = 100

private val previewCard = PerpsShareCard(
    name = "Bitcoin",
    symbol = "BTC",
    iconUrl = null,
    side = PerpsPositionSide.LONG,
    leverage = 40,
    roiPct = BigDecimal("128.97"),
    isProfit = true,
    entryPrice = BigDecimal("66541"),
    currentPrice = BigDecimal("120541"),
    date = 0L,
)

@Preview(showSystemUi = true)
@Composable
private fun PerpsSharePositionDialogProfitPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsSharePositionDialogBody(
            card = previewCard,
            graphicsLayer = null,
            shareEnabled = true,
            onShare = {},
            onClose = {},
        )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun PerpsSharePositionDialogLossPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsSharePositionDialogBody(
            card = previewCard.copy(
                roiPct = BigDecimal("-128.97"),
                isProfit = false,
                currentPrice = BigDecimal("35541"),
            ),
            graphicsLayer = null,
            shareEnabled = true,
            onShare = {},
            onClose = {},
        )
    }
}
