package com.tonapps.wallet.data.passcode.source

import android.content.Context
import com.tonapps.extensions.clear
import com.tonapps.extensions.putString
import com.tonapps.extensions.remove
import com.tonapps.security.Security
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PasscodeStore(context: Context) {

    companion object {
        const val CODE_LENGTH = 4

        private const val NAME = "passcode"
        private const val CODE_KEY = "code"
        private const val KEY_ALIAS = "_com_tonapps_passcode_master_key_"
    }

    private val keyValue = Security.pref(context, KEY_ALIAS, NAME)

    val hasPinCode: Boolean
        get() = keyValue.contains(CODE_KEY)

    suspend fun setPinCode(code: String) = withContext(Dispatchers.IO) {
        keyValue.putString(CODE_KEY, code)
    }

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

    suspend fun compare(code: String): Boolean = withContext(Dispatchers.IO) {
        val savedCode = keyValue.getString(CODE_KEY, null)
        savedCode == code
    }

}