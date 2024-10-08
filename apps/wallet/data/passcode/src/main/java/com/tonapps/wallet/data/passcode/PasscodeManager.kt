package com.tonapps.wallet.data.passcode

import android.content.Context
import androidx.biometric.BiometricPrompt
import com.tonapps.extensions.logError
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.passcode.dialog.PasscodeDialog
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.navigation.Navigation
import java.util.concurrent.atomic.AtomicBoolean

class PasscodeManager(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val helper: PasscodeHelper,
    private val rnLegacy: RNLegacy,
    private val scope: CoroutineScope
) {

    private val lockscreen = LockScreen(this, settingsRepository)

    val lockscreenFlow: Flow<LockScreen.State>
        get() = lockscreen.stateFlow

    init {
        scope.launch(Dispatchers.IO) {
            if (rnLegacy.isRequestMigration()) {
                helper.reset()
            }
            lockscreen.init()
        }
    }

    fun lockscreenBiometric(result: BiometricPrompt.AuthenticationResult) {
        lockscreen.biometric(result)
    }

    fun deleteAll() {
        scope.launch {
            reset()
        }
    }

    fun lockscreenCheck(context: Context, code: String) {
        scope.launch { lockscreen.check(context, code) }
    }

    suspend fun hasPinCode(): Boolean = withContext(Dispatchers.IO) {
        if (helper.hasPinCode) {
            true
        } else {
            rnLegacy.hasPinCode()
        }
    }

    private suspend fun isRequestMigration(): Boolean = withContext(Dispatchers.IO) {
        !helper.hasPinCode && rnLegacy.hasPinCode()
    }

    suspend fun requestValidPasscode(context: Context): String = withContext(Dispatchers.Main) {
        val code = PasscodeDialog.request(context) ?: throw Exception("failed to request passcode")
        if (!isValid(context, code)) {
            throw Exception("invalid passcode")
        }
        code
    }

    suspend fun isValid(context: Context, code: String): Boolean = withContext(Dispatchers.IO) {
        if (!isRequestMigration()) {
            helper.isValid(context, code)
        } else {
            try {
                migration(context, code)
                true
            } catch (e: Throwable) {
                context.logError(e)
                false
            }
        }
    }

    suspend fun change(context: Context, old: String, new: String): Boolean = withContext(Dispatchers.IO) {
        if (isRequestMigration()) {
            migration(context, old)
        }
        if (!helper.change(context, old, new)) {
            false
        } else {
            try {
                rnLegacy.changePasscode(old, new)
                if (settingsRepository.biometric) {
                    rnLegacy.setupBiometry(new)
                }
                true
            } catch (e: Throwable) {
                context.logError(e)
                false
            }
        }
    }

    suspend fun save(code: String) {
        helper.save(code)
    }

    suspend fun reset() = withContext(Dispatchers.IO) {
        helper.reset()
        rnLegacy.clearMnemonic()
    }

    suspend fun confirmation(
        context: Context,
        title: String
    ): Boolean = withContext(Dispatchers.Main) {
        if (isRequestMigration()) {
            return@withContext confirmationMigration(context)
        }

        val showDialog = if (settingsRepository.biometric && PasscodeBiometric.isAvailableOnDevice(context)) {
            !PasscodeBiometric.showPrompt(context, title)
        } else {
            true
        }

        if (showDialog) {
            PasscodeDialog.confirmation(context)
        } else {
            true
        }
    }

    suspend fun legacyGetPasscode(
        context: Context
    ): String? {
        return legacyGetPasscodeByBiometry() ?: PasscodeDialog.request(context)
    }

    private suspend fun legacyGetPasscodeByBiometry(): String? {
        try {
            if (settingsRepository.biometric) {
                return rnLegacy.exportPasscodeWithBiometry()
            }
            throw Exception("biometry is disabled")
        } catch (e: Throwable) {
            return null
        }
    }

    private suspend fun confirmationMigration(
        context: Context,
    ): Boolean = withContext(Dispatchers.Main) {
        try {
            val passcode = if (settingsRepository.biometric) {
                rnLegacy.exportPasscodeWithBiometry()
            } else {
                PasscodeDialog.request(context)
            }
            if (passcode.isNullOrBlank()) {
                throw Exception("failed to request passcode")
            }
            migration(context, passcode)
            true
        } catch (e: Throwable) {
            context.logError(e)
            false
        }
    }

    private suspend fun migration(
        context: Context,
        code: String
    ) = withContext(Dispatchers.Main) {
        val navigation = Navigation.from(context)
        navigation?.toast("...", true, 0)
        accountRepository.importPrivateKeysFromRNLegacy(code)
        save(code)
        navigation?.toast("...", false, 0)
    }

    fun confirmationFlow(context: Context, title: String) = flow {
        val valid = confirmation(context, title)
        if (!valid) {
            throw Exception("failed to request passcode")
        } else {
            emit(Unit)
        }
    }
}