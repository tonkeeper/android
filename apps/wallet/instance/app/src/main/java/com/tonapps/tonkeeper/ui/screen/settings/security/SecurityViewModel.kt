package com.tonapps.tonkeeper.ui.screen.settings.security

import android.app.Application
import android.content.Context
import com.tonapps.tonkeeper.core.FirebaseHelper
import com.tonapps.tonkeeper.extensions.isSafeModeEnabled
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.settings.SafeModeState
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class SecurityViewModel(
    app: Application,
    private val settingsRepository: SettingsRepository,
    private val rnLegacy: RNLegacy,
    private val passcodeManager: PasscodeManager,
    private val api: API
): BaseWalletVM(app) {

    var lockScreen: Boolean
        get() = settingsRepository.lockScreen
        set(value) {
            settingsRepository.lockScreen = value
        }

    val biometric: Boolean
        get() = settingsRepository.biometric

    val safeModeFlow: Flow<SafeModeState>
        get() = settingsRepository.safeModeStateFlow

    fun isSafeModeEnabled() = settingsRepository.isSafeModeEnabled(api)

    fun setSafeModeState(state: SafeModeState) {
        settingsRepository.setSafeModeState(state)
        FirebaseHelper.secureModeEnabled(state)
    }

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