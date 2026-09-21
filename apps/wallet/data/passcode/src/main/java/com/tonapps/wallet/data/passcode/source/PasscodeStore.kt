package com.tonapps.wallet.data.passcode.source

import android.content.Context
import com.tonapps.extensions.clear
import com.tonapps.security.Security
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PasscodeStore(context: Context) {

    private val keyValue by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Security.pref(context, KEY_ALIAS, NAME) }

    val hasPinCode: Boolean
        get() = !keyValue.get(CODE_KEY).isNullOrBlank()


    suspend fun clearPinCode() = withContext(Dispatchers.IO) {
        keyValue.clear()
    }

    suspend fun change(oldCode: String, newCode: String): Boolean {
        if (compare(oldCode)) {
            setPinCode(newCode)
            return true
        }
        return false
    }

    suspend fun setPinCode(code: String) = withContext(Dispatchers.IO) {
        keyValue.put(CODE_KEY, code)
    }

    suspend fun getPinCode(): String? = withContext(Dispatchers.IO) {
        keyValue.get(CODE_KEY)
    }

    // Two-phase PIN-change marker: the target PIN is recorded before the multichain vault is
    // re-sealed and cleared when every store agrees, so an interrupted change can be healed.
    suspend fun setPendingPinChange(code: String?) = withContext(Dispatchers.IO) {
        keyValue.put(PENDING_PIN_CHANGE_KEY, code)
    }

    suspend fun getPendingPinChange(): String? = withContext(Dispatchers.IO) {
        keyValue.get(PENDING_PIN_CHANGE_KEY)
    }

    suspend fun compare(code: String): Boolean = withContext(Dispatchers.IO) {
        val savedCode = keyValue.get(CODE_KEY)
        if (savedCode.isNullOrBlank()) {
            false
        } else {
            savedCode == code
        }
    }

    companion object {
        private const val NAME = "passcode"
        private const val CODE_KEY = "code"
        private const val PENDING_PIN_CHANGE_KEY = "pending_pin_change"
        private const val KEY_ALIAS = "_com_tonapps_passcode_master_key_"
    }
}
