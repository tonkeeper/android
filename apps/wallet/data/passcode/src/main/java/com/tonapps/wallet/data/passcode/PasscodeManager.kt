package com.tonapps.wallet.data.passcode

import android.content.Context
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.passcode.source.PasscodeStore
import com.tonapps.wallet.data.passcode.dialog.PasscodeDialog
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class PasscodeManager(
    private val settingsRepository: SettingsRepository,
    private val helper: PasscodeHelper,
) {

    val hasPinCode: Boolean
        get() = helper.hasPinCode

    suspend fun isValid(code: String): Boolean {
        return helper.isValid(code)
    }

    suspend fun change(old: String, new: String): Boolean {
        return helper.change(old, new)
    }

    suspend fun save(code: String) {
        helper.save(code)
    }

    suspend fun reset() {
        helper.reset()
    }

    suspend fun confirmation(
        context: Context,
        title: String
    ): Boolean = withContext(Dispatchers.Main) {
        if (!settingsRepository.biometric || !PasscodeBiometric.isAvailableOnDevice(context)) {
            return@withContext PasscodeDialog.confirmation(context)
        }

        if (!PasscodeBiometric.showPrompt(context, title)) {
            PasscodeDialog.confirmation(context)
        } else {
            true
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