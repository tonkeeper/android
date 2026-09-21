package com.tonapps.tonkeeper.ui.screen.settings.security

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.tonkeeper.core.FirebaseHelper
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.settings.SafeModeState
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SecurityViewModel(
    app: Application,
    private val settingsRepository: SettingsRepository,
    private val rnLegacy: RNLegacy,
    private val passcodeManager: PasscodeManager,
): BaseWalletVM(app) {

    private val _hasPasscodeFlow = MutableStateFlow<Boolean?>(null)
    val hasPasscodeFlow = _hasPasscodeFlow.asStateFlow().filterNotNull()

    val biometric: Boolean
        get() = settingsRepository.biometric

    init {
        refreshPasscodeState()
    }

    fun refreshPasscodeState() {
        viewModelScope.launch(Dispatchers.IO) {
            _hasPasscodeFlow.value = passcodeManager.hasPinCode()
        }
    }

    fun safeModeFlow(wallet: WalletEntity): Flow<SafeModeState> =
        settingsRepository.safeModeChangedFlow.map {
            settingsRepository.getSafeModeState(wallet.id)
        }

    fun isSafeModeEnabled(wallet: WalletEntity) =
        settingsRepository.isSafeModeEnabled(wallet.id, wallet.network)

    fun setSafeModeState(wallet: WalletEntity, state: SafeModeState) {
        settingsRepository.setSafeModeState(wallet.id, state)
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
