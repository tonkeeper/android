package com.tonapps.wallet.data.rn

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.tonapps.wallet.data.rn.data.RNDecryptedData
import com.tonapps.wallet.data.rn.data.RNVaultState
import com.tonapps.wallet.data.rn.data.RNWallet
import com.tonapps.wallet.data.rn.data.RNWallets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.ceil

class RNLegacy(context: Context) {

    companion object {
        const val DEFAULT_KEYSTORE_ALIAS = "key_v1"
    }

    private val sql = RNSql(context)
    private val seedStorage = RNSeedStorage(context)
    private var cacheWallets: RNWallets? = null

    suspend fun addMnemonics(passcode: String, walletIds: List<String>, mnemonic: List<String>) {
        val vaultState = getVaultState(passcode)

        for (walletId in walletIds) {
            vaultState.keys[walletId] = RNDecryptedData(walletId, mnemonic.joinToString(" "))
        }
        seedStorage.save(passcode, vaultState)
    }

    suspend fun getVaultState(passcode: String): RNVaultState = withContext(Dispatchers.IO) {
        try {
            seedStorage.get(passcode)
        } catch (e: Throwable) {
            RNVaultState()
        }
    }

    fun getValue(key: String): String? {
        return sql.getValue(key)
    }

    fun getJSONValue(key: String): JSONObject? {
        return sql.getJSONObject(key)
    }

    fun setJSONValue(key: String, value: JSONObject) {
        sql.setJSONObject(key, value)
    }

    fun getJSONState(key: String): JSONObject? {
        return getJSONValue(key)?.getJSONObject("state")
    }

    fun setJSONState(key: String, value: JSONObject) {
        val state = JSONObject()
        state.put("state", value)
        setJSONValue(key, state)
    }

    fun setActivity(activity: FragmentActivity) {
        seedStorage.setActivity(activity)
    }

    suspend fun getWallets(): RNWallets {
        return cacheWallets?.copy() ?: loadWallets().also { cacheWallets = it.copy() }
    }

    suspend fun setWallets(wallets: RNWallets) {
        cacheWallets = wallets.copy()
        saveWallets(wallets)
    }

    suspend fun setSelectedWallet(id: String) {
        val wallets = getWallets()
        if (wallets.selectedIdentifier != id) {
            setWallets(wallets.copy(
                selectedIdentifier = id
            ))
        }
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