package com.tonkeeper

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PasscodeManager(context: Context) {

    companion object {
        const val CODE_LENGTH = 4

        private const val NAME = "passcode"
        private const val CODE_KEY = "code"
    }

    private val prefs = EncryptedSharedPreferences.create(
        NAME,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    val hasPinCode: Boolean
        get() = prefs.contains(CODE_KEY)

    var temporalPinCode: String? = null

    suspend fun setPinCode(code: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(CODE_KEY, code).apply()
    }

    fun checkTemporalPinCode(code: String): Boolean {
        if (code.length != CODE_LENGTH) {
            return false
        }
        return temporalPinCode == code
    }

    suspend fun checkPinCode(code: String): Boolean = withContext(Dispatchers.IO) {
        if (code.length != CODE_LENGTH) {
            return@withContext false
        }
        prefs.getString(CODE_KEY, null) == code
    }

}