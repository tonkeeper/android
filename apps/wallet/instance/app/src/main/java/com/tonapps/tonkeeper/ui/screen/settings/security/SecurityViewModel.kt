package com.tonapps.tonkeeper.ui.screen.settings.security

import android.app.Application
import android.content.Context
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class SecurityViewModel(
    app: Application,
    private val settingsRepository: SettingsRepository,
    private val rnLegacy: RNLegacy,
    private val passcodeManager: PasscodeManager,
): BaseWalletVM(app) {

    var lockScreen: Boolean
        get() = settingsRepository.lockScreen
        set(value) {
            settingsRepository.lockScreen = value
        }

    val biometric: Boolean
        get() = settingsRepository.biometric

    fun enableBiometric(context: Context, value: Boolean) = flow {
        if (value) {
            val code = passcodeManager.requestValidPasscode(context)
            rnLegacy.setupBiometry(code)
        } else {
            rnLegacy.removeBiometry()
        }
        settingsRepository.biometric = value
        emit(Unit)
    }.flowOn(Dispatchers.IO)
}