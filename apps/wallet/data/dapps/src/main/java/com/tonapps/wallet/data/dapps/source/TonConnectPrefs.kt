package com.tonapps.wallet.data.dapps.source

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.extensions.prefs
import com.tonapps.extensions.putLong
import com.tonapps.extensions.remove
import com.tonapps.extensions.toParcel
import com.tonapps.security.CryptoBox
import com.tonapps.security.Security

internal class TonConnectPrefs(context: Context) {

    private val encrypted by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Security.pref(context, KEY_ALIAS, ENCRYPTED_NAME)
    }
    private val prefs = context.prefs(PREFS_NAME)

    fun getLastEventId(): Long = prefs.getLong(LAST_EVENT_ID_KEY, 0)

    fun setLastEventId(id: Long) {
        if (id > getLastEventId()) prefs.putLong(LAST_EVENT_ID_KEY, id)
    }

    fun clearLastEventId() {
        prefs.remove(LAST_EVENT_ID_KEY)
    }

    fun getLastAppRequestId(clientId: String): Long {
        return prefs.getLong(LAST_APP_REQUEST_ID_PREFIX + clientId, -1)
    }

    fun setLastAppRequestId(clientId: String, id: Long) {
        if (id > getLastAppRequestId(clientId)) {
            prefs.putLong(LAST_APP_REQUEST_ID_PREFIX + clientId, id)
        }
    }

    fun isPushEnabled(accountId: String, network: TonNetwork, appUrl: Uri): Boolean {
        val key = prefixPush(prefixAccount(accountId, network), appUrl)
        if (prefs.contains(key)) return prefs.getBoolean(key, false)
        return prefs.getBoolean(prefixPush(legacyPrefixAccount(accountId, network), appUrl), false)
    }

    fun setPushEnabled(accountId: String, network: TonNetwork, appUrl: Uri, enabled: Boolean) {
        prefs.edit {
            putBoolean(prefixPush(prefixAccount(accountId, network), appUrl), enabled)
        }
    }

    @Synchronized
    fun addPendingOriginCleanup(profileName: String, host: String) {
        val entries = pendingOriginCleanupEntries()
        entries.add("$profileName|$host|${System.currentTimeMillis()}")
        prefs.edit { putStringSet(ORIGIN_CLEANUP_KEY, entries) }
    }

    @Synchronized
    fun hasPendingOriginCleanup(profileName: String, host: String): Boolean {
        val prefix = originCleanupPrefix(profileName, host)
        return pendingOriginCleanupEntries().any { it.startsWith(prefix) }
    }

    @Synchronized
    fun consumePendingOriginCleanup(profileName: String, host: String): Boolean {
        val prefix = originCleanupPrefix(profileName, host)
        val entries = pendingOriginCleanupEntries()
        val remaining = entries.filterTo(mutableSetOf()) { !it.startsWith(prefix) }
        if (remaining.size == entries.size) {
            return false
        }
        prefs.edit { putStringSet(ORIGIN_CLEANUP_KEY, remaining) }
        return true
    }

    // Copies the stored set: SharedPreferences forbids mutating the instance it returns.
    private fun pendingOriginCleanupEntries(): MutableSet<String> {
        val stored = prefs.getStringSet(ORIGIN_CLEANUP_KEY, null) ?: return mutableSetOf()
        val oldestAllowed = System.currentTimeMillis() - ORIGIN_CLEANUP_TTL_MS
        return stored.filterTo(mutableSetOf()) { entry ->
            (entry.substringAfterLast('|').toLongOrNull() ?: 0L) >= oldestAllowed
        }
    }

    private fun originCleanupPrefix(profileName: String, host: String) = "$profileName|$host|"

    // Used once, on v3 -> v4 boot, to migrate keypairs from EncryptedSharedPreferences into the
    // connect row. Returns the keypair bytes if found, or null. Also clears the prefs entry on hit.
    fun consumeLegacyKeyPair(
        accountId: String,
        network: TonNetwork,
        clientId: String,
    ): ByteArray? {
        val prefix = prefixAccount(accountId, network)
        readLegacyKeyPair(prefix, clientId)?.let {
            encrypted.transaction { remove(prefixKeyPair(prefix, clientId)) }
            return it
        }

        val legacyPrefix = legacyPrefixAccount(accountId, network)
        readLegacyKeyPair(legacyPrefix, clientId)?.let {
            encrypted.transaction { remove(prefixKeyPair(legacyPrefix, clientId)) }
            return it
        }
        return null
    }

    private fun readLegacyKeyPair(prefix: String, clientId: String): ByteArray? {
        return try {
            val bytes = encrypted.getByteArray(prefixKeyPair(prefix, clientId)) ?: return null
            // Validate the blob still parses as a KeyPair; reject corrupted ones.
            bytes.toParcel<CryptoBox.KeyPair>() ?: return null
            bytes
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            null
        }
    }

    private fun prefixKeyPair(prefix: String, clientId: String) = "key_pair_${prefix}_${clientId}"
    private fun prefixPush(prefix: String, appUrl: Uri) = "push_${prefix}_${appUrl}"
    private fun prefixAccount(accountId: String, network: TonNetwork) = "account_${accountId}:${network.value}"
    private fun legacyPrefixAccount(accountId: String, network: TonNetwork): String {
        return "account_${accountId}:${if (network.isTestnet) "1" else "0"}"
    }

    private companion object {
        private const val ENCRYPTED_NAME = "dapps"
        private const val KEY_ALIAS = "_com_tonapps_dapps_master_key_"
        private const val PREFS_NAME = "tonconnect"
        private const val LAST_EVENT_ID_KEY = "last_event_id"
        private const val LAST_APP_REQUEST_ID_PREFIX = "last_app_request_id_"
        private const val ORIGIN_CLEANUP_KEY = "pending_origin_cleanup"
        private const val ORIGIN_CLEANUP_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }
}
