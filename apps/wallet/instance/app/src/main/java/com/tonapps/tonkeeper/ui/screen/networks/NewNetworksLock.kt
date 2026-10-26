package com.tonapps.tonkeeper.ui.screen.networks

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.tonapps.onboading.screens.networks.NewNetworksScreen
import com.tonapps.onboading.screens.networks.NewNetworksState
import com.tonapps.wallet.data.passcode.NewChainsSync
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.delay

// Long enough for the icons to fly out and the orbit to turn before it stops on the result.
private const val MIN_LOADING_MS = 3_000L

/**
 * The new networks screen as part of the lock screen (LockScreen.State.NewChains): shown while the
 * chains the wallets missed get added, closed with the lock screen by "Open wallet" or the close
 * button. The close button skips the wait: the sync goes on in the background, and whatever it
 * misses is retried on the next unlock. Back is blocked.
 */
@Composable
fun NewNetworksLock(passcodeManager: PasscodeManager) {
    val sync by passcodeManager.newChainsFlow.collectAsState()
    // Nothing between sessions, so each one starts from the intro again.
    val current = sync ?: return
    NewNetworksSession(
        sync = current,
        onContinue = passcodeManager::newChainsShown,
        onRetry = passcodeManager::retryNewChains,
        onClose = passcodeManager::newChainsShown,
    )
}

@Composable
private fun NewNetworksSession(
    sync: NewChainsSync,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    var state by remember { mutableStateOf<NewNetworksState>(NewNetworksState.Loading) }
    var loadingSince by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(sync) {
        when (sync) {
            is NewChainsSync.Running -> {
                loadingSince = SystemClock.elapsedRealtime()
                state = NewNetworksState.Loading
            }
            is NewChainsSync.Finished -> {
                delay(MIN_LOADING_MS - (SystemClock.elapsedRealtime() - loadingSince))
                state = if (sync.added.isEmpty()) {
                    NewNetworksState.Error
                } else {
                    NewNetworksState.Added(sync.added)
                }
            }
        }
    }

    NewNetworksScreen(
        chains = sync.chains,
        state = state,
        continueText = stringResource(Localization.new_networks_open_wallet),
        onContinue = onContinue,
        onRetry = onRetry,
        onClose = onClose,
    )
}
