package com.tonapps.onboading.screens.loader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tonapps.wallet.localization.Localization
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlinx.coroutines.flow.filter
import ui.theme.Shapes
import ui.theme.UIKit

private const val LOADER_ANIMATION = "loader_blue_opt.json"
private const val DONE_ANIMATION = "done_green_opt.json"
private val ANIMATION_SIZE = 48.dp

sealed interface InitLoaderState {
    data object Loading : InitLoaderState

    // Carries the synced wallet id so the host can defer selecting it (which swaps the root to the
    // main screen) until the success animation has played through.
    data class Done(val walletId: String) : InitLoaderState
}

/**
 * Non-cancellable, dimmed overlay shown while a wallet is being created and synced. The host drives
 * [state]: it loops the loader animation while [InitLoaderState.Loading], then plays the success
 * animation once on [InitLoaderState.Done] and invokes [onDone] when it finishes.
 */
@Composable
fun InitLoaderDialog(
    state: InitLoaderState,
    onDone: () -> Unit,
) {
    Dialog(
        onDismissRequest = { /* non-cancellable: ignore back press / outside taps */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier.width(193.dp),
            shape = Shapes.large,
            color = UIKit.colorScheme.background.content,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (state) {
                    is InitLoaderState.Loading -> LoaderAnimation()
                    is InitLoaderState.Done -> DoneAnimation(onFinished = onDone)
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(
                        when (state) {
                            is InitLoaderState.Loading -> Localization.creating_wallet
                            is InitLoaderState.Done -> Localization.wallet_created_all_set
                        }
                    ),
                    style = UIKit.typography.label1,
                    color = UIKit.colorScheme.text.primary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun LoaderAnimation() {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset(LOADER_ANIMATION))
    LottieAnimation(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        modifier = Modifier.size(ANIMATION_SIZE),
    )
}

@Composable
private fun DoneAnimation(onFinished: () -> Unit) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset(DONE_ANIMATION))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
    )

    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = Modifier.size(ANIMATION_SIZE),
    )

    // Close once the success animation has fully played through.
    LaunchedEffect(composition) {
        snapshotFlow { progress }
            .filter { composition != null && it >= 1f }
            .collect { onFinished() }
    }
}
