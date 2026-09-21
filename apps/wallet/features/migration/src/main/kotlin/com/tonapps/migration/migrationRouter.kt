package com.tonapps.migration

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.onboading.screens.backup.BackupRouter
import com.tonapps.migration.data.MigrationFeeShortage
import com.tonapps.migration.screens.confirm.MigrationConfirmFeature
import com.tonapps.migration.screens.confirm.MigrationConfirmScreen
import com.tonapps.migration.screens.confirm.MigrationFeeOptionIcon
import com.tonapps.migration.screens.prepare.MigrationPrepareFeature
import com.tonapps.migration.screens.prepare.MigrationPrepareScreen
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import ui.moon.MoonNav
import ui.moon.rememberNestedNavBackStack

@Serializable
sealed interface MigrationRoutes : NavKey {
    @Serializable
    data object Prepare : MigrationRoutes

    @Serializable
    data object Backup : MigrationRoutes

    @Serializable
    data class Confirm(val walletId: String) : MigrationRoutes
}

@Composable
fun MigrationRouter(
    onClose: () -> Unit,
    onMigrationSucceeded: () -> Unit,
    onAddWallet: () -> Unit,
    onDepositForFees: (WalletEntity, MigrationFeeShortage) -> Unit,
    onDepositForFeeOption: (WalletEntity, MigrationFeeOptionIcon) -> Unit,
    skippable: Boolean = false,
) {
    val backStack = rememberNestedNavBackStack(MigrationRoutes.Prepare, onClose)
    val prepareFeature = koinViewModel<MigrationPrepareFeature>()

    MoonNav(backStack = backStack) { key ->
        when (key) {
            is MigrationRoutes.Prepare -> NavEntry(key) {
                MigrationPrepareScreen(
                    feature = prepareFeature,
                    onClose = onClose,
                    onAddWallet = onAddWallet,
                    onNavigateToBackup = {
                        backStack.add(MigrationRoutes.Backup)
                    },
                    onNavigateToConfirm = { walletId ->
                        backStack.add(MigrationRoutes.Confirm(walletId))
                    },
                    onSkip = onClose.takeIf { skippable },
                )
            }

            is MigrationRoutes.Backup -> NavEntry(key) {
                BackupRouter(
                    onBack = { backStack.safeRemoveLastOrNull(key) },
                    onClose = { backStack.safeRemoveLastOrNull(key) },
                    onFinished = {
                        backStack.safeRemoveLastOrNull(key)
                        prepareFeature.continueToConfirm()
                    },
                )
            }

            is MigrationRoutes.Confirm -> NavEntry(key) {
                val feature = koinViewModel<MigrationConfirmFeature>(
                    key = key.walletId,
                ) {
                    parametersOf(key.walletId)
                }

                MigrationConfirmScreen(
                    feature = feature,
                    onBack = { backStack.safeRemoveLastOrNull(key) },
                    onClose = onClose,
                    onMigrationSucceeded = onMigrationSucceeded,
                    onDepositForFees = onDepositForFees,
                    onDepositForFeeOption = onDepositForFeeOption,
                )
            }

            else -> throw IllegalStateException("Unknown key: $key")
        }
    }
}
