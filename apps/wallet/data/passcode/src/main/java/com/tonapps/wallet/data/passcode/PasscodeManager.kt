package com.tonapps.wallet.data.passcode

import android.content.Context
import android.util.Log
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.passcode.source.PasscodeStore
import com.tonapps.wallet.data.passcode.ui.PasscodeDialog
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class PasscodeManager(
    private val context: Context,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val rnLegacy: RNLegacy,
) {

    private val store = PasscodeStore(context)

    val hasPinCode: Boolean
        get() = store.hasPinCode

    suspend fun isValid(context: Context, code: String): Boolean = withContext(Dispatchers.IO) {
        if (store.hasPinCode && store.compare(code)) {
            true
        } else {
            isValidLegacy(context, code)
        }
    }

    private suspend fun isValidLegacy(context: Context, code: String): Boolean {
        try {
            accountRepository.importPrivateKeysFromRNLegacy(code)
            store.setPinCode(code)
            return true
        } catch (e: Throwable) {
            return false
        }
    }

    suspend fun change(context: Context, old: String, new: String): Boolean {
        if (!store.hasPinCode && !isValidLegacy(context, old)) {
            return false
        }
        return store.change(old, new)
    }

    suspend fun save(code: String) {
        store.setPinCode(code)
    }

    suspend fun reset() {
        store.clearPinCode()
    }

    suspend fun confirmation(
        context: Context,
        title: String
    ): Boolean = withContext(Dispatchers.Main) {
        if (!settingsRepository.biometric || !PasscodeBiometric.isAvailableOnDevice(context)) {
            return@withContext dialog(context)
        }

        if (!PasscodeBiometric.showPrompt(context, title)) {
            dialog(context)
        } else {
            true
        }
    }

    fun confirmationFlow(context: Context, title: String) = flow {
        val valid = confirmation(context, title)
        Log.d("BackupViewModelLog", "confirmationFlow: $valid")
        if (!valid) {
            throw Exception("failed to request passcode")
        } else {
            emit(Unit)
        }
    }

    private suspend fun dialog(context: Context): Boolean {
        val (dialog, code) = passcode(context)
        if (code.isEmpty()) {
            dialog.close()
            return false
        } else if (isValid(context, code)) {
            dialog.setSuccess()
            return true
        }
        dialog.setError()
        return false
    }

    private suspend fun passcode(context: Context): Pair<PasscodeDialog, String> = suspendCancellableCoroutine { continuation ->
        val dialog = PasscodeDialog(context)
        dialog.callback = { code ->
            if (isActive) {
                continuation.resume(dialog to code)
                dialog.callback = null
            }
        }
        dialog.show()
    }
}