package com.tonapps.onboading.screens.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import ui.moon.MoonNav
import ui.moon.rememberNestedNavBackStack

@Serializable
sealed interface BackupRoutes : NavKey {
    @Serializable
    data object Intro : BackupRoutes

    @Serializable
    data object Phrase : BackupRoutes

    @Serializable
    data object Check : BackupRoutes
}

@Composable
fun BackupRouter(
    onBack: () -> Unit,
    onClose: () -> Unit,
    onFinished: () -> Unit,
) {
    val feature = koinViewModel<BackupFeature>()
    // Words live only in memory, so Phrase/Check must not be restored without them.
    val backStack = rememberNestedNavBackStack(BackupRoutes.Intro, onBack, saveable = false)
    val words by feature.words.collectAsState()

    MoonNav(backStack = backStack) { key ->
        when (key) {
            is BackupRoutes.Intro -> NavEntry(key) {
                BackupIntroScreen(
                    feature = feature,
                    onBack = { backStack.safeRemoveLastOrNull(key) },
                    onContinue = { backStack.add(BackupRoutes.Phrase) },
                )
            }

            is BackupRoutes.Phrase -> NavEntry(key) {
                BackupPhraseScreen(
                    words = words,
                    onCheckBackup = { backStack.add(BackupRoutes.Check) },
                    onBack = { backStack.safeRemoveLastOrNull(key) },
                )
            }

            is BackupRoutes.Check -> NavEntry(key) {
                BackupCheckScreen(
                    data = BackupCheckData(words),
                    onDone = {
                        feature.saveBackup(onFinished)
                    },
                    onBack = { backStack.safeRemoveLastOrNull(key) },
                )
            }

            else -> throw IllegalStateException("Unknown key: $key")
        }
    }
}
