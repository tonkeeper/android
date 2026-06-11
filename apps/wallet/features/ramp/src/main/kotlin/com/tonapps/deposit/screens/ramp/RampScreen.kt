package com.tonapps.deposit.screens.ramp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import com.tonapps.deposit.screens.method.RampAsset
import com.tonapps.deposit.screens.provider.ProviderConfirmDialog
import com.tonapps.mvi.props.observeSafeState
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import io.exchangeapi.models.ExchangeLayoutItem
import io.exchangeapi.models.ExchangeMerchantInfo
import ui.components.moon.MoonCircleIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.cell.MoonCardCell
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.container.MoonScaffold
import ui.painterResource
import ui.theme.UIKit

@Composable
fun RampScreen(
    feature: RampFeature,
    rampType: RampType,
    onClose: () -> Unit,
    onSend: () -> Unit,
    onQr: () -> Unit,
    onBuyCash: (preferredCurrency: String?) -> Unit,
    onBuyCrypto: (RampAsset) -> Unit,
    onBuyStablecoins: () -> Unit,
) {
    val state by feature.state.global.observeSafeState()

    RampScreenContent(
        state = state,
        rampType = rampType,
        onClose = onClose,
        onSend = onSend,
        onQr = onQr,
        onBuyCash = onBuyCash,
        onBuyCrypto = onBuyCrypto,
        onBuyStablecoins = onBuyStablecoins,
    )
}

@Composable
private fun RampScreenContent(
    state: RampState,
    rampType: RampType,
    onClose: () -> Unit,
    onSend: () -> Unit,
    onQr: () -> Unit,
    onBuyCash: (preferredCurrency: String?) -> Unit,
    onBuyCrypto: (RampAsset) -> Unit,
    onBuyStablecoins: () -> Unit,
) {
    val isSendAvailable = (state as? RampState.Data)?.isSendAvailable ?: true

    MoonScaffold(
        Modifier
            .verticalScroll(rememberScrollState())
            .nestedScroll(rememberNestedScrollInteropConnection()),
        title = stringResource(
            when (rampType) {
                RampType.RampOn -> Localization.add_funds
                RampType.RampOff -> Localization.withdraw
            }
        ),
        onClose = onClose
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (rampType) {
                RampType.RampOn -> TransferActionCell(
                    icon = painterResource(id = UIKitIcon.ic_qr_code_28),
                    title = stringResource(Localization.receive_tokens),
                    description = stringResource(Localization.deposit_from_another_wallet),
                    onClick = onQr,
                )
                RampType.RampOff -> if (isSendAvailable) {
                    TransferActionCell(
                        icon = painterResource(id = UIKitIcon.ic_tray_arrow_up_28),
                        title = stringResource(Localization.send_tokens),
                        description = stringResource(Localization.deposit_to_another_wallet),
                        onClick = onSend,
                    )
                }
            }

            when (state) {
                is RampState.Loading -> MoonLoaderCell()
                is RampState.Empty -> MoonLoaderCell()
                is RampState.Data -> {
                    state.fiatItem?.let { item ->
                        LayoutItemCell(
                            item = item,
                            onClick = { onBuyCash(item.preferredCurrency) },
                        )
                    }
                    state.cryptoItem?.let { item ->
                        val asset = state.cryptoAsset ?: return@let
                        LayoutItemCell(item = item, onClick = { onBuyCrypto(asset) })
                    }
                    state.stablecoinItem?.let { item ->
                        LayoutItemCell(item = item, onClick = onBuyStablecoins)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferActionCell(
    icon: Painter,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    val color = UIKit.colorScheme.accent.blue
    val bg = remember(color) {
        color.copy(alpha = 0.13f)
    }

    ActionCell(
        image = {
            MoonCircleIcon(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(bg),
                size = 44.dp,
                iconSize = 28.dp,
                painter = icon,
                color = Color.Transparent,
            )
        },
        title = title,
        description = description,
        onClick = onClick,
    )
}

@Composable
private fun LayoutItemCell(item: ExchangeLayoutItem, onClick: () -> Unit) {
    ActionCell(
        image = { MoonItemImage(image = item.image, size = 44.dp) },
        title = item.title,
        description = item.description,
        onClick = onClick,
    )
}

@Composable
private fun ActionCell(
    image: @Composable () -> Unit,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    MoonCardCell(
        image = image,
        title = title,
        subtitle = description,
        onClick = onClick,
        maxLinesTitle = 2,
        maxLinesSubtitle = 2,
    )
}
