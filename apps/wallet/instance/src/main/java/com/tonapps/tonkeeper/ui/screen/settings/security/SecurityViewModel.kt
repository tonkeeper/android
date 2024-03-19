package com.tonapps.tonkeeper.ui.screen.settings.security

import android.content.Context
import androidx.lifecycle.ViewModel
import com.tonapps.tonkeeper.password.PasscodeRepository
import com.tonapps.wallet.data.account.WalletRepository
import com.tonapps.wallet.data.account.WalletType
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

class SecurityViewModel(
    private val settingsRepository: SettingsRepository,
    private val passcodeRepository: PasscodeRepository,
    private val walletRepository: WalletRepository,
): ViewModel() {

    val hasMnemonicFlow: Flow<Boolean> = walletRepository.activeWalletFlow.map { it.type == WalletType.Default || it.type == WalletType.Testnet }

    var lockScreen: Boolean
        get() = settingsRepository.lockScreen
        set(value) {
            settingsRepository.lockScreen = value
        }

    var biometric: Boolean
        get() = settingsRepository.biometric
        set(value) {
            settingsRepository.biometric = value
        }

    fun getRecoveryPhrase(
        context: Context
    ) = walletRepository.activeWalletFlow.combine(passcodeRepository.confirmationFlow(context)) { wallet, _ ->
        walletRepository.getMnemonic(wallet.id)
    }.take(1)
}