package com.tonapps.wallet.data.passcode

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricPrompt
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.extensions.logError
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.passcode.dialog.PasscodeDialog
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.navigation.Navigation

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
        settingsRepository.isMigratedFlow.onEach {
            lockscreen.init()
        }.launchIn(scope)
    }

    fun lockscreenBiometric() {
        lockscreen.biometric()
    }

    fun deleteAll() {
        settingsRepository.biometric = false
        scope.launch {
            reset()
        }
    }

    fun lockscreenCheck(context: Context, code: String) {
        scope.launch {
            lockscreen.check(context, code)
        }
    }

    suspend fun hasPinCode(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (helper.hasPinCode) {
                true
            } else {
                rnLegacy.hasPinCode()
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            false
        }
    }

    private suspend fun isRequestMigration(): Boolean = withContext(Dispatchers.IO) {
        !helper.hasPinCode && rnLegacy.hasPinCode()
    }

    suspend fun requestValidPasscode(context: Context): String = withContext(Dispatchers.Main) {
        val code = PasscodeDialog.request(context) ?: throw Exception("invalid passcode")
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
            } catch (e: Throwable) {
                FirebaseCrashlytics.getInstance().recordException(e)
                context.logError(e)
                false
            }
        }
    }

    suspend fun change(context: Context, old: String, new: String): Boolean = withContext(Dispatchers.IO) {
        if (isRequestMigration() && !migration(context, old)) {
            false
        } else {
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
                    FirebaseCrashlytics.getInstance().recordException(e)
                    context.logError(e)
                    false
                }
            }
        }
    }

    suspend fun save(code: String) {
        helper.save(code)
    }

    suspend fun reset() = withContext(Dispatchers.IO) {
        settingsRepository.lockScreen = false
        settingsRepository.biometric = false
        helper.reset()
        rnLegacy.clearMnemonic()
    }

    suspend fun isBiometricRequest(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!PasscodeBiometric.isAvailableOnDevice(context)) {
            false
        } else if (isRequestMigration()) {
            rnLegacy.getWallets().biometryEnabled
        } else {
            settingsRepository.biometric
        }
    }

    suspend fun confirmationByBiometric(
        context: Context,
        title: String
    ): Boolean = withContext(Dispatchers.Main) {
        try {
            if (isRequestMigration()) {
                val passcode = rnLegacy.exportPasscodeWithBiometry()
                if (passcode.isBlank()) {
                    throw Exception("failed to request passcode")
                }
                migration(context, passcode)
            } else {
                PasscodeBiometric.showPrompt(context, title)
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            false
        }
    }

    suspend fun confirmation(
        context: Context,
        title: String
    ): Boolean = withContext(Dispatchers.Main) {
        if (isRequestMigration()) {
            return@withContext confirmationMigration(context)
        }

        if (settingsRepository.biometric && PasscodeBiometric.isAvailableOnDevice(context) && PasscodeBiometric.showPrompt(context, title)) {
            true
        } else {
            val passcode = PasscodeDialog.request(context)
            if (passcode.isNullOrBlank()) {
                false
            } else {
                helper.isValid(context, passcode)
            }
        }
    }

    suspend fun legacyGetPasscode(
        context: Context
    ): String? {
        return legacyGetPasscodeByBiometry() ?: PasscodeDialog.request(context)
    }

    private suspend fun legacyGetPasscodeByBiometry(): String? {
        try {
            return rnLegacy.exportPasscodeWithBiometry()
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            return null
        }
    }

    private suspend fun confirmationMigration(
        context: Context,
    ): Boolean = withContext(Dispatchers.Main) {
        val passcodeByBiometric: String? = try {
            if (settingsRepository.biometric) {
                rnLegacy.exportPasscodeWithBiometry()
            } else {
                null
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            null
        }

        val passcodeByDialog: String? = try {
            if (passcodeByBiometric.isNullOrEmpty()) {
                PasscodeDialog.request(context)
            } else {
                null
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            null
        }
        try {
            val passcode = passcodeByBiometric ?: passcodeByDialog
            if (passcode.isNullOrBlank()) {
                throw Exception("failed to request passcode")
            }
            migration(context, passcode)
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            context.logError(e)
            false
        }
    }

    private suspend fun migration(
        context: Context,
        code: String
    ): Boolean = withContext(Dispatchers.Main) {
        val navigation = Navigation.from(context)
        navigation?.migrationLoader(true)
        if (accountRepository.importPrivateKeysFromRNLegacy(code)) {
            save(code)
            navigation?.migrationLoader(false)
            true
        } else {
            navigation?.migrationLoader(false)
            false
        }
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