package com.tonapps.wallet.data.account.legacy.storage

import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.content.edit
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.extensions.getByteArray
import com.tonapps.extensions.getLongArray
import com.tonapps.extensions.putByteArray
import com.tonapps.extensions.putInt
import com.tonapps.extensions.putLongArray
import com.tonapps.extensions.putString
import com.tonapps.extensions.remove
import com.tonapps.wallet.data.account.WalletSource
import org.ton.api.pub.PublicKeyEd25519
import com.tonapps.wallet.data.account.legacy.WalletLegacy
import com.tonapps.wallet.data.account.WalletType

internal class Wallets(
    private val prefs: SharedPreferences
) {

    companion object {
        private const val WALLET_IDS_KEY = "wallets"
        private const val WALLET_NAME = "name"
        private const val WALLET_PUBLIC_KEY = "public_key"
        private const val WALLET_TYPE = "type"
        private const val WALLET_VERSION = "version"
        private const val WALLET_EMOJI = "emoji"
        private const val WALLET_COLOR = "color"
        private const val WALLET_SOURCE = "source"
    }

    suspend fun get(id: Long): WalletLegacy? {
        val publicKey = getPublicKey(id) ?: return null
        val name = getName(id)
        return WalletLegacy(
            id = id,
            name = name,
            publicKey = publicKey,
            type = getType(id),
            version = getVersion(id),
            emoji = getEmoji(id),
            color = getColor(id),
            source = getSource(id)
        )
    }

    suspend fun add(wallet: WalletLegacy) {
        setPublicKey(wallet.id, wallet.publicKey)
        setName(wallet.id, wallet.name)
        setType(wallet.id, wallet.type)
        setEmoji(wallet.id, wallet.emoji)
        setColor(wallet.id, wallet.color)
        setVersion(wallet.id, wallet.version)
        setSource(wallet.id, wallet.source)

        addId(wallet.id)
    }

    suspend fun setName(id: Long, name: String?) {
        val key = key(WALLET_NAME, id)
        if (!name.isNullOrBlank()) {
            prefs.putString(key, name)
        }
    }

    suspend fun setVersion(id: Long, version: WalletVersion) {
        val key = key(WALLET_VERSION, id)
        prefs.putString(key, version.name)
    }

    private suspend fun getVersion(id: Long): WalletVersion {
        val type = prefs.getString(key(WALLET_VERSION, id), null)?.let { WalletVersion.valueOf(it) }
        return type ?: WalletVersion.V4R2
    }

    private suspend fun setType(id: Long, type: WalletType) {
        prefs.putString(key(WALLET_TYPE, id), type.name)
    }

    private suspend fun getName(id: Long): String {
        val name = prefs.getString(key(WALLET_NAME, id), null)
        if (name.isNullOrBlank()) {
            return "Wallet"
        }
        return name
    }

    private suspend fun getType(id: Long): WalletType {
        val type = prefs.getString(key(WALLET_TYPE, id), null)?.let { WalletType.valueOf(it) }
        return type ?: WalletType.Default
    }

    private suspend fun getPublicKey(id: Long): PublicKeyEd25519? {
        val key = key(WALLET_PUBLIC_KEY, id)
        return prefs.getByteArray(key)?.let { PublicKeyEd25519(it) }
    }

    private suspend fun setPublicKey(id: Long, publicKey: PublicKeyEd25519) {
        val key = key(WALLET_PUBLIC_KEY, id)
        prefs.putByteArray(key, publicKey.key.toByteArray())
    }

    suspend fun setEmoji(id: Long, emoji: CharSequence) {
        val key = key(WALLET_EMOJI, id)
        prefs.putString(key, emoji.toString())
    }

    private fun getEmoji(id: Long): String {
        val value = prefs.getString(key(WALLET_EMOJI, id), null)
        if (value.isNullOrBlank()) {
            return "\uD83D\uDE00"
        }
        return value
    }

    fun setColor(id: Long, color: Int) {
        prefs.putInt(key(WALLET_COLOR, id), color)
    }

    private fun getColor(id: Long): Int {
        val value = prefs.getInt(key(WALLET_COLOR, id), 0)
        if (value == 0) {
            return Color.parseColor("#2E3847")
        }
        return value
    }

    private fun setSource(id: Long, source: WalletSource) {
        prefs.putString(key(WALLET_SOURCE, id), source.name)
    }

    private fun getSource(id: Long): WalletSource {
        val value = prefs.getString(key(WALLET_SOURCE, id), null)
        return WalletSource.valueOf(value ?: WalletSource.Default.name)
    }

    fun hasWallet(): Boolean {
        return prefs.contains(WALLET_IDS_KEY)
    }

    suspend fun delete(id: Long) {
        prefs.edit {
            remove(key(WALLET_NAME, id))
            remove(key(WALLET_PUBLIC_KEY, id))
            remove(key(WALLET_TYPE, id))
            remove(key(WALLET_EMOJI, id))
            remove(key(WALLET_COLOR, id))
            remove(key(WALLET_VERSION, id))
            remove(key(WALLET_SOURCE, id))
        }
        deleteId(id)
    }

    private fun key(prefix: String, id: Long): String {
        return "${prefix}_$id"
    }

    fun getIds(): LongArray {
        return prefs.getLongArray(WALLET_IDS_KEY)
    }

    private suspend fun addId(id: Long) {
        val ids = getIds().toMutableList()
        ids.add(id)
        setIds(ids.distinct().toLongArray())
    }

    private suspend fun deleteId(id: Long) {
        val ids = getIds().toMutableList()
        ids.remove(id)
        setIds(ids.toLongArray())
    }

    private suspend fun setIds(ids: LongArray) {
        if (ids.isEmpty()) {
            prefs.remove(WALLET_IDS_KEY)
        } else {
            prefs.putLongArray(WALLET_IDS_KEY, ids)
        }
    }
}