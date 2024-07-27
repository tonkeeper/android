package com.tonapps.wallet.data.rn

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.tonapps.wallet.data.rn.data.RNVaultState
import com.tonapps.wallet.data.rn.expo.SecureStoreModule
import com.tonapps.wallet.data.rn.expo.SecureStoreOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.ceil

internal class RNSeedStorage(context: Context) {

    private companion object {
        private const val keychainService = "TKProtected"
        private const val walletsKey = "wallets"
        private const val biometryKey = "biometry_passcode"
    }

    private val kv = SecureStoreModule(context)

    fun setActivity(activity: FragmentActivity) {
        kv.setActivity(activity)
    }

    suspend fun setTonProof(id: String, token: String) = withContext(Dispatchers.IO) {
        val key = keyTonProof(id)
        kv.setItemImpl(key, token)
    }

    suspend fun getTonProof(id: String): String? = withContext(Dispatchers.IO) {
        val key = keyTonProof(id)
        kv.getItemImpl(key)
    }

    private fun keyTonProof(id: String): String {
        return "proof-$id"
    }

    suspend fun exportPasscodeWithBiometry(): String = withContext(Dispatchers.IO) {
        val passcode = kv.getItemImpl(biometryKey, SecureStoreOptions(
            keychainService = keychainService
        ))
        if (passcode.isNullOrBlank()) {
            throw Exception("Biometry passcode is null")
        }
        passcode
    }

    suspend fun setupBiometry(passcode: String) = withContext(Dispatchers.IO) {
        kv.setItemImpl(biometryKey, passcode, SecureStoreOptions(
            keychainService = keychainService,
            requireAuthentication = true,
        ))
    }

    suspend fun removeBiometry() = withContext(Dispatchers.IO) {
        kv.deleteItemImpl(biometryKey, SecureStoreOptions(
            keychainService = keychainService
        ))
    }

    suspend fun hasPinCode(): Boolean {
        return readState() != null
    }

    suspend fun removeAll() {
        kv.getSharedPreferences().edit().clear().apply()
    }

    suspend fun save(passcode: String, state: RNVaultState) = withContext(Dispatchers.IO) {
        val seedState = ScryptBox.encrypt(passcode, state.string)
        saveState(seedState)
    }

    suspend fun get(passcode: String): RNVaultState = withContext(Dispatchers.IO) {
        val state = readState() ?: throw Exception("Seed state is null")
        val json = JSONObject(ScryptBox.decrypt(passcode, state))
        RNVaultState.of(json)
    }

    private suspend fun saveState(state: SeedState) {
        val encryptedString = state.string

        val chunkSize = 2048
        var index = 0

        while (index < encryptedString.length) {
            val chunk = encryptedString.substring(index, minOf(index + chunkSize, encryptedString.length))
            val key = "${walletsKey}_chunk_${index / chunkSize}"
            kv.setItemImpl(key, chunk)
            index += chunkSize
        }

        val key = "${walletsKey}_chunks"
        val count = ceil(encryptedString.length.toDouble() / chunkSize).toInt()
        kv.setItemImpl(key, "$count")
    }

    private suspend fun readState(): SeedState? {
        val chunks = kv.getItemImpl("${walletsKey}_chunks")?.toIntOrNull() ?: 0
        if (0 >= chunks) {
            return null
        }
        var encryptedString = ""
        for (i in 0 until chunks) {
            val chunk = kv.getItemImpl("${walletsKey}_chunk_$i") ?: throw Exception("Chunk $i is null")
            encryptedString += chunk
        }
        val json = JSONObject(encryptedString)
        return SeedState(json)
    }
}