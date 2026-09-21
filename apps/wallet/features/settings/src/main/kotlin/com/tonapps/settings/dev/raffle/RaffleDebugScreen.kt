package com.tonapps.settings.dev.raffle

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.tonapps.wallet.data.raffle.entities.RaffleEntity
import org.koin.androidx.compose.koinViewModel
import ui.components.moon.MoonItemTitle
import ui.components.moon.cell.TextCell
import ui.components.moon.container.MoonScaffold
import ui.preview.ThemedPreview
import ui.theme.UIKit
import uikit.navigation.Navigation
import java.time.OffsetDateTime

@Composable
fun RaffleDebugScreen(
    onBack: () -> Unit,
) {
    val feature = koinViewModel<RaffleDebugFeature>()
    val raffle by feature.raffle.collectAsState()
    val prizes by feature.prizes.collectAsState()
    val debugNow by feature.debugNow.collectAsState()
    val message by feature.message.collectAsState()
    val context = LocalContext.current

    RaffleDebugScreenInternal(
        raffleId = raffle?.id,
        prizes = prizes,
        debugNow = debugNow,
        message = message,
        onBack = onBack,
        onPhase = feature::setPhase,
        onWin = feature::win,
        onLose = feature::lose,
        onResetStoryViews = feature::resetStoryViews,
        onOpenUrl = { url -> Navigation.from(context)?.openURL(url) },
    )
}

@Composable
private fun RaffleDebugScreenInternal(
    raffleId: String?,
    prizes: List<RaffleEntity.Prize>,
    debugNow: OffsetDateTime?,
    message: String?,
    onBack: () -> Unit,
    onPhase: (RafflePhase) -> Unit,
    onWin: (RaffleEntity.Prize) -> Unit,
    onLose: () -> Unit,
    onResetStoryViews: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    MoonScaffold(
        title = "Raffle QA",
        onBack = onBack,
        content = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                TextCell(
                    title = "_debug_now",
                    subtitle = message,
                    content = {
                        MoonItemTitle(
                            text = debugNow?.toString() ?: "real time",
                            color = UIKit.colorScheme.text.secondary,
                        )
                    },
                )
                for (phase in RafflePhase.entries) {
                    TextCell(
                        title = phase.title,
                        onClick = { onPhase(phase) },
                    )
                }
                for (prize in prizes) {
                    TextCell(
                        title = "Win: ${prize.title}",
                        onClick = { onWin(prize) },
                    )
                }
                TextCell(
                    title = "Lose (clear winner row)",
                    onClick = onLose,
                )
                TextCell(
                    title = "Reset story view flags",
                    onClick = onResetStoryViews,
                )
                TextCell(
                    title = "Open raffle sheet",
                    onClick = { raffleId?.let { onOpenUrl("tonkeeper://raffle/$it") } },
                )
                TextCell(
                    title = "Open raffle story",
                    onClick = { raffleId?.let { onOpenUrl("tonkeeper://story/$it") } },
                )
            }
        },
    )
}

@Preview
@Composable
private fun RaffleDebugScreenPreview() {
    ThemedPreview {
        RaffleDebugScreenInternal(
            raffleId = "mystery_raffle",
            prizes = listOf(
                RaffleEntity.Prize(id = "usdt_10k", image = "", title = "$10 000 in USDT"),
                RaffleEntity.Prize(id = "plush_pepe", image = "", title = "Plush Pepe NFT"),
            ),
            debugNow = null,
            message = "Raffle mystery_raffle, status Joined",
            onBack = {},
            onPhase = {},
            onWin = {},
            onLose = {},
            onResetStoryViews = {},
            onOpenUrl = {},
        )
    }
}
