package com.tonapps.wallet.data.rn

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.tonapps.security.Sodium
import com.tonapps.security.hex
import com.tonapps.wallet.data.rn.data.RNMnemonic
import com.tonapps.wallet.data.rn.data.RNWallet
import com.tonapps.wallet.data.rn.data.RNWallets
import com.tonapps.wallet.data.rn.expo.SecureStoreModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class RNLegacy(context: Context) {

    companion object {
        private const val SHARED_PREFERENCES_NAME = "SecureStore"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val SCHEME_PROPERTY = "scheme"
        private const val KEYSTORE_ALIAS_PROPERTY = "keystoreAlias"
        const val USES_KEYSTORE_SUFFIX_PROPERTY = "usesKeystoreSuffix"
        const val DEFAULT_KEYSTORE_ALIAS = "key_v1"
        const val AUTHENTICATED_KEYSTORE_SUFFIX = "keystoreAuthenticated"
        const val UNAUTHENTICATED_KEYSTORE_SUFFIX = "keystoreUnauthenticated"

        private const val walletsKey = "wallets"
    }

    private val kv = SecureStoreModule(context)
    private val sql = RNSql(context)
    private var cacheWallets: RNWallets? = null

    suspend fun loadSecureStore(passcode: String): List<RNMnemonic> {
        val password = passcode.toByteArray()
        val chunks = kv.getItemImpl("${walletsKey}_chunks")?.toIntOrNull() ?: 0
        if (0 >= chunks) {
            throw Exception("Chunks is $chunks")
        }
        var encryptedString = ""
        for (i in 0 until chunks) {
            val chunk = kv.getItemImpl("${walletsKey}_chunk_$i") ?: throw Exception("Chunk $i is null")
            encryptedString += chunk
        }

        val encrypted = JSONObject(encryptedString)

        val salt = encrypted.getString("salt").hex()
        val N = encrypted.getInt("N")
        val r = encrypted.getInt("r")
        val p = encrypted.getInt("p")
        val nonce = salt.slice(0 until 24).toByteArray()
        val ct = encrypted.getString("ct").hex()
        val enckey = Sodium.scryptHash(password, salt, N, r, p, 32) ?: throw Exception("scryptHash failed")
        val text = Sodium.cryptoSecretboxOpen(ct, nonce, enckey)?.decodeToString() ?: throw Exception("cryptoSecretboxOpen failed")
        val json = JSONObject(text)
        val list = mutableListOf<RNMnemonic>()
        for (key in json.keys()) {
            list.add(RNMnemonic(json.getJSONObject(key)))
        }
        return list
    }

    fun getValue(key: String): String? {
        return sql.getValue(key)
    }

    fun getJSONValue(key: String): JSONObject? {
        return sql.getJSONObject(key)
    }

    fun getJSONState(key: String): JSONObject? {
        return getJSONValue(key)?.getJSONObject("state")
    }

    fun setActivity(activity: FragmentActivity) {
        kv.setActivity(activity)
    }

    suspend fun getWallets(): RNWallets {
        return cacheWallets?.copy() ?: loadWallets().also { cacheWallets = it.copy() }
    }

    suspend fun setWallets(wallets: RNWallets) {
        cacheWallets = wallets.copy()
        saveWallets(wallets)
    }

    suspend fun clear() {
        cacheWallets = null
        saveWallets(getWallets().copy(
            wallets = emptyList()
        ))
    }

    suspend fun addWallet(wallet: RNWallet) {
        val newWallets = getWallets().copy(
            wallets = getWallets().wallets.toMutableList().apply {
                add(wallet)
            }
        )
        setWallets(newWallets)
    }

    suspend fun edit(id: String, name: String, emoji: String, color: Int) {
        val wallets = getWallets()
        val index = wallets.wallets.indexOfFirst { it.identifier == id }
        if (index == -1) {
            return
        }
        val wallet = wallets.wallets[index]
        val newWallet = wallet.copy(
            name = name,
            emoji = emoji,
            color = RNWallet.resolveColor(color)
        )
        setWallets(wallets.copy(
            wallets = wallets.wallets.toMutableList().apply {
                set(index, newWallet)
            }
        ))
    }

    suspend fun delete(id: String) {
        val wallets = getWallets()
        val index = wallets.wallets.indexOfFirst { it.identifier == id }
        if (index == -1) {
            return
        }
        setWallets(wallets.copy(
            wallets = wallets.wallets.toMutableList().apply {
                removeAt(index)
            }
        ))
    }

    private suspend fun loadWallets(): RNWallets = withContext(Dispatchers.IO) {
        val value = sql.getJSONObject("walletsStore") ?: return@withContext RNWallets.empty
        try {
            RNWallets(value)
        } catch (e: Throwable) {
            RNWallets.empty
        }
    }

    private suspend fun saveWallets(wallets: RNWallets) = withContext(Dispatchers.IO) {
        sql.setJSONObject("walletsStore", wallets.toJSON())
    }

}