package com.tonkeeper.ton

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

internal class SafeStorage(context: Context) {

    private companion object {
        private val WORDS_KEY = "words"
    }

    private val keyGenParameterSpec = MasterKeys.AES256_GCM_SPEC
    private val mainKeyAlias = MasterKeys.getOrCreate(keyGenParameterSpec)

    private val sharedPreferences = EncryptedSharedPreferences.create(
        "tonkeeper",
        mainKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveWallet(wallet: WalletInfo) {
        with (sharedPreferences.edit()) {
            putString(WORDS_KEY, wallet.words.joinToString(","))
            apply()
        }
    }

    fun clear() {
        with (sharedPreferences.edit()) {
            clear()
            apply()
        }
    }

    fun getWallet(): WalletInfo? {
        val words = sharedPreferences.getString(WORDS_KEY, null)?.split(",")
        return if (words != null) WalletInfo(words) else null
    }

}