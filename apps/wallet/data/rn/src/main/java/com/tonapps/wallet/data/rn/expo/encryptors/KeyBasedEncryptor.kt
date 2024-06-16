package com.tonapps.wallet.data.rn.expo.encryptors

import com.tonapps.wallet.data.rn.expo.AuthenticationHelper
import com.tonapps.wallet.data.rn.expo.SecureStoreOptions
import org.json.JSONException
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.KeyStore

interface KeyBasedEncryptor<E : KeyStore.Entry> {
    fun getExtendedKeyStoreAlias(options: SecureStoreOptions, requireAuthentication: Boolean): String

    fun getKeyStoreAlias(options: SecureStoreOptions): String

    @Throws(GeneralSecurityException::class)
    fun initializeKeyStoreEntry(keyStore: KeyStore, options: SecureStoreOptions): E

    @Throws(GeneralSecurityException::class, JSONException::class)
    suspend fun createEncryptedItem(
        plaintextValue: String,
        keyStoreEntry: E,
        requireAuthentication: Boolean,
        authenticationPrompt: String,
        authenticationHelper: AuthenticationHelper
    ): JSONObject

    @Throws(GeneralSecurityException::class, JSONException::class)
    suspend fun decryptItem(
        key: String,
        encryptedItem: JSONObject,
        keyStoreEntry: E,
        options: SecureStoreOptions,
        authenticationHelper: AuthenticationHelper
    ): String
}