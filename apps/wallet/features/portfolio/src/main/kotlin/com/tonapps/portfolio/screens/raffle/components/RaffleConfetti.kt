package com.tonapps.portfolio.screens.raffle.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import ui.components.moon.MoonLottie

private const val CONFETTI_LEFT_FILE = "raffle_confetti_left.json"
private const val CONFETTI_RIGHT_FILE = "raffle_confetti_right.json"
private const val CONFETTI_HALF_ASPECT_RATIO = 187f / 540f
private const val CONFETTI_START_DELAY_MS = 600L

@Composable
fun RaffleConfetti(modifier: Modifier = Modifier) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(CONFETTI_START_DELAY_MS)
        started = true
    }
    if (!started) {
        return
    }
    Row(modifier = modifier) {
        MoonLottie(
            fileName = CONFETTI_LEFT_FILE,
            iterations = 1,
            modifier = Modifier
                .weight(1f)
                .aspectRatio(CONFETTI_HALF_ASPECT_RATIO),
        )
        MoonLottie(
            fileName = CONFETTI_RIGHT_FILE,
            iterations = 1,
            modifier = Modifier
                .weight(1f)
                .aspectRatio(CONFETTI_HALF_ASPECT_RATIO),
        )
    }
}
