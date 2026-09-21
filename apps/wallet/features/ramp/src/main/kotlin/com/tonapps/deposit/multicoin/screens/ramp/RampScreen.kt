package com.tonapps.deposit.multicoin.screens.ramp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import io.exchangeapi.models.ExchangeLayoutCard
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
    onReceive: () -> Unit,
    onSend: () -> Unit,
    onCardClick: (ExchangeLayoutCard) -> Unit,
) {
    val state by feature.state.collectAsState()

    MoonScaffold(
        modifier = Modifier
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .nestedScroll(rememberNestedScrollInteropConnection()),
        title = stringResource(
            when (rampType) {
                RampType.RampOn -> Localization.add_funds
                RampType.RampOff -> Localization.withdraw
            }
        ),
        onClose = onClose,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (rampType) {
                RampType.RampOn -> TransferActionCell(
                    icon = painterResource(id = UIKitIcon.ic_qr_code_28),
                    title = stringResource(Localization.receive_tokens),
                    description = stringResource(Localization.deposit_from_another_wallet_mc),
                    onClick = onReceive,
                )

                RampType.RampOff -> {
                    val sendAvailable = (state as? RampState.Data)?.isSendAvailable ?: true
                    if (sendAvailable) {
                        TransferActionCell(
                            icon = painterResource(id = UIKitIcon.ic_tray_arrow_up_28),
                            title = stringResource(Localization.send_tokens),
                            description = stringResource(Localization.deposit_to_another_wallet),
                            onClick = onSend,
                        )
                    }
                }
            }

            when (val current = state) {
                is RampState.Loading -> MoonLoaderCell()
                is RampState.Data -> current.cards.forEach { card ->
                    LayoutCardCell(card = card, onClick = { onCardClick(card) })
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
    onClick: () -> Unit,
) {
    val color = UIKit.colorScheme.accent.blue
    val bg = remember(color) { color.copy(alpha = 0.13f) }

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
private fun LayoutCardCell(card: ExchangeLayoutCard, onClick: () -> Unit) {
    ActionCell(
        image = { MoonItemImage(image = card.image, size = 44.dp) },
        title = card.title,
        description = card.description,
        onClick = onClick,
    )
}

@Composable
private fun ActionCell(
    image: @Composable () -> Unit,
    title: String,
    description: String,
    onClick: () -> Unit,
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
