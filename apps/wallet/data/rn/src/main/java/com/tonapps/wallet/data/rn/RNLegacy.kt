package com.tonapps.wallet.data.rn

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.extensions.bestMessage
import com.tonapps.extensions.map
import com.tonapps.wallet.data.rn.data.RNDecryptedData
import com.tonapps.wallet.data.rn.data.RNFavorites
import com.tonapps.wallet.data.rn.data.RNSpamTransactions
import com.tonapps.wallet.data.rn.data.RNTC
import com.tonapps.wallet.data.rn.data.RNVaultState
import com.tonapps.wallet.data.rn.data.RNWallet
import com.tonapps.wallet.data.rn.data.RNWallets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class RNLegacy(
    context: Context,
    private val scope: CoroutineScope
) {

    companion object {
        const val DEFAULT_KEYSTORE_ALIAS = "key_v1"
    }

    private val sql: RNSql by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RNSql(context)
    }

    private val seedStorage: RNSeedStorage by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RNSeedStorage(context)
    }

    @Volatile
    private var cacheWallets: RNWallets? = null

    @Volatile
    private var requestMigration: Boolean? = null

    @Volatile
    private var walletMigrated: Boolean? = null

    init {
        scope.launch(Dispatchers.IO) {
            requestMigration = sql.getValue("x") == null
            if (requestMigration == true) {
                sql.setValue("x", "true")
            } else {
                walletMigrated = true
            }
        }
    }

    fun getAllKeyValuesForDebug(): JSONObject {
        return seedStorage.getAllKeyValuesForDebug()
    }

    fun isRequestMainMigration(): Boolean {
        while (requestMigration == null) {
            Thread.sleep(16)
        }
        return requestMigration!!
    }

    private fun isWalletMigrated(): Boolean {
        while (walletMigrated == null) {
            Thread.sleep(16)
        }
        return walletMigrated!!
    }

    fun isRequestMigration(): Boolean {
        if (!isRequestMainMigration()) {
            return false
        }
        return isWalletMigrated()
    }

    fun setWalletMigrated() {
        walletMigrated = true
    }

    suspend fun setTonProof(id: String, token: String) {
        if (getTonProof(id) == null) {
            seedStorage.setTonProof(id, token)
        }
    }

    suspend fun getTonProof(id: String): String? {
        return seedStorage.getTonProof(id)
    }

    suspend fun exportPasscodeWithBiometry(): String {
        return seedStorage.exportPasscodeWithBiometry()
    }

    suspend fun setupBiometry(passcode: String) = withContext(Dispatchers.IO) {
        seedStorage.setupBiometry(passcode)
    }

    suspend fun removeBiometry() = withContext(Dispatchers.IO) {
        seedStorage.removeBiometry()
    }

    suspend fun clearMnemonic() {
        seedStorage.removeAll()
    }

    suspend fun hasPinCode(): Boolean {
        return seedStorage.hasPinCode()
    }

    suspend fun addMnemonics(passcode: String, walletIds: List<String>, mnemonic: List<String>) {
        /*val vaultState = getVaultState(passcode)

        for (walletId in walletIds) {
            vaultState.keys[walletId] = RNDecryptedData(walletId, mnemonic.joinToString(" "))
        }
        seedStorage.save(passcode, vaultState)*/
    }

    suspend fun changePasscode(oldPasscode: String, newPasscode: String) {
        val vaultState = getVaultState(oldPasscode)
        seedStorage.save(newPasscode, vaultState)
    }

    suspend fun getVaultState(passcode: String): RNVaultState = withContext(Dispatchers.IO) {
        try {
            seedStorage.get(passcode)
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            RNVaultState(original = e.bestMessage, cause = e)
        }
    }

    fun getSpamTransactions(walletId: String): RNSpamTransactions {
        val key = keySpamTransactions(walletId)
        val json = sql.getJSONObject(key) ?: return RNSpamTransactions(walletId)
        val spam = mutableListOf<String>()
        val nonSpam = mutableListOf<String>()
        for (transactionId in json.keys()) {
            if (json.optBoolean(transactionId)) {
                spam.add(transactionId)
            } else {
                nonSpam.add(transactionId)
            }
        }
        return RNSpamTransactions(walletId, spam.toList(), nonSpam.toList())
    }

    fun setSpamTransactions(walletId: String, data: RNSpamTransactions) {
        val json = JSONObject()
        for (transactionId in data.spam) {
            json.put(transactionId, true)
        }
        for (transactionId in data.nonSpam) {
            json.put(transactionId, false)
        }
        val key = keySpamTransactions(walletId)
        sql.setJSONObject(key, json)
    }

    private fun keySpamTransactions(walletId: String): String {
        return "${walletId}/local-scam"
    }

    fun getValue(key: String): String? {
        return sql.getValue(key)
    }

    fun getJSONValue(key: String): JSONObject? {
        return sql.getJSONObject(key)
    }

    fun getJSONArray(key: String): JSONArray? {
        return sql.getJSONArray(key)
    }

    fun setJSONValue(key: String, value: JSONObject, v: Int = -1) {
        if (v >= 0) {
            value.put("__version", v)
        }
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

    fun getTCApps(): RNTC {
        val tcApps = getJSONState("TCApps")?.getJSONObject("connectedApps") ?: JSONObject()
        return RNTC(tcApps)
    }

    fun getFavorites(): List<RNFavorites> {
        val array = getJSONArray("favorites") ?: return emptyList()
        return array.map {
            RNFavorites(it)
        }
    }

    fun getHiddenAddresses(): List<String> {
        val array = getJSONArray("hidden_addresses") ?: return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
            list.add(array.getString(i))
        }
        return list.toList()
    }

    fun setTCApps(data: RNTC) {
        val json = JSONObject()
        json.put("connectedApps", data.toJSON())
        setJSONState("TCApps", json)
    }

    fun setTokenHidden(
        walletId: String,
        tokenAddress: String,
        hidden: Boolean
    ) {
        val key = "${walletId}/tokenApproval"
        val json = getJSONValue(key)?.getJSONObject("tokens") ?: JSONObject()
        if (hidden) {
            json.put(tokenAddress, JSONObject().apply {
                put("current", "declined")
                put("updated_at", System.currentTimeMillis())
            })
        } else {
            json.remove(tokenAddress)
        }
        setJSONValue(key, JSONObject().apply {
            put("tokens", json)
        })
    }

    fun getSetup(walletId: String): Pair<Boolean, Boolean> {
        val json = getJSONValue("${walletId}/setup") ?: JSONObject()
        val setupDismissed = json.optBoolean("setupDismissed", false)
        val hasOpenedTelegramChannel = json.optBoolean("hasOpenedTelegramChannel", false)
        return Pair(setupDismissed, hasOpenedTelegramChannel)
    }

    suspend fun setSetupLastBackupAt(walletId: String, date: Long) {
        val json = getSetupJSON(walletId)
        json.put("lastBackupAt", date)
        setSetupJSON(walletId, json)
    }

    suspend fun setSetupDismissed(walletId: String) {
        val json = getSetupJSON(walletId)
        json.put("setupDismissed", true)
        setSetupJSON(walletId, json)
    }

    suspend fun setHasOpenedTelegramChannel(walletId: String) {
        val json = getSetupJSON(walletId)
        json.put("hasOpenedTelegramChannel", true)
        setSetupJSON(walletId, json)
    }

    private fun getSetupJSON(walletId: String): JSONObject {
        val key = "${walletId}/setup"
        return getJSONValue(key) ?: JSONObject()
    }

    private fun setSetupJSON(walletId: String, json: JSONObject) {
        val key = "${walletId}/setup"
        setJSONValue(key, json, 1)
    }

    fun getNotificationsEnabled(walletId: String): Boolean {
        val key = "$walletId/notifications"
        return getJSONValue(key)?.getBoolean("isSubscribed") ?: false
    }

    fun setNotificationsEnabled(walletId: String, enabled: Boolean) {
        val key = "$walletId/notifications"
        val value = getJSONValue(key) ?: JSONObject()
        value.put("isSubscribed", enabled)
        setJSONValue(key, value)
    }

    fun getHiddenTokens(walletId: String): List<String> {
        val tokens = getJSONValue("${walletId}/tokenApproval")?.getJSONObject("tokens") ?: JSONObject()
        val list = mutableListOf<String>()
        for (key in tokens.keys()) {
            val json = tokens.getJSONObject(key)
            val current = json.optString("current") ?: continue
            val hidden = current == "declined" || current == "spam"
            if (hidden) {
                list.add(key)
            }
        }
        return list
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
        val state = getWallets()
        val newWallets = state.copy(
            wallets = state.wallets.toMutableList().apply {
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
        val value = getJSONValue("walletsStore") ?: return@withContext RNWallets.empty
        try {
            RNWallets(value)
        } catch (e: Throwable) {
            RNWallets.empty
        }
    }

    private suspend fun saveWallets(wallets: RNWallets) = withContext(Dispatchers.IO) {
        val json = wallets.toJSON()
        json.put("__version", 2)
        setJSONValue("walletsStore", json)
    }

}