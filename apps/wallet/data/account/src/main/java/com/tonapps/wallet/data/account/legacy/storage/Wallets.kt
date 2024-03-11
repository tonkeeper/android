package com.tonapps.wallet.data.account.legacy.storage

import android.graphics.Color
import com.tonapps.blockchain.ton.contract.WalletVersion
import org.ton.api.pub.PublicKeyEd25519
import com.tonapps.wallet.data.account.legacy.WalletLegacy
import com.tonapps.wallet.data.account.WalletType
import core.keyvalue.KeyValue

internal class Wallets(
    private val keyValue: KeyValue
) {

    companion object {
        private const val WALLET_IDS_KEY = "wallets"
        private const val WALLET_NAME = "name"
        private const val WALLET_PUBLIC_KEY = "public_key"
        private const val WALLET_TYPE = "type"
        private const val WALLET_VERSION = "version"
        private const val WALLET_EMOJI = "emoji"
        private const val WALLET_COLOR = "color"
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
        )
    }

    suspend fun add(wallet: WalletLegacy) {
        setPublicKey(wallet.id, wallet.publicKey)
        setName(wallet.id, wallet.name)
        setType(wallet.id, wallet.type)
        setEmoji(wallet.id, wallet.emoji)
        setColor(wallet.id, wallet.color)
        setVersion(wallet.id, wallet.version)

        addId(wallet.id)
    }

    suspend fun setName(id: Long, name: String?) {
        val key = key(WALLET_NAME, id)
        if (name.isNullOrBlank()) {
            keyValue.remove(key)
        } else {
            keyValue.putString(key, name)
        }
    }

    suspend fun setVersion(id: Long, version: WalletVersion) {
        val key = key(WALLET_VERSION, id)
        keyValue.putString(key, version.name)
    }

    private suspend fun getVersion(id: Long): WalletVersion {
        val type = keyValue.getString(key(WALLET_VERSION, id))?.let { WalletVersion.valueOf(it) }
        return type ?: WalletVersion.V4R2
    }

    private suspend fun setType(id: Long, type: WalletType) {
        keyValue.putString(key(WALLET_TYPE, id), type.name)
    }

    private suspend fun getName(id: Long): String {
        val name = keyValue.getString(key(WALLET_NAME, id))
        if (name.isNullOrBlank()) {
            return "Wallet"
        }
        return name
    }

    private suspend fun getType(id: Long): WalletType {
        val type = keyValue.getString(key(WALLET_TYPE, id))?.let { WalletType.valueOf(it) }
        return type ?: WalletType.Default
    }

    private suspend fun getPublicKey(id: Long): PublicKeyEd25519? {
        val key = key(WALLET_PUBLIC_KEY, id)
        return keyValue.getByteArray(key)?.let { PublicKeyEd25519(it) }
    }

    private suspend fun setPublicKey(id: Long, publicKey: PublicKeyEd25519) {
        val key = key(WALLET_PUBLIC_KEY, id)
        keyValue.putByteArray(key, publicKey.key.toByteArray())
    }

    suspend fun setEmoji(id: Long, emoji: CharSequence) {
        val key = key(WALLET_EMOJI, id)
        keyValue.putString(key, emoji.toString())
    }

    private suspend fun getEmoji(id: Long): String {
        val value = keyValue.getString(key(WALLET_EMOJI, id))
        if (value.isNullOrBlank()) {
            return "\uD83D\uDE00"
        }
        return value
    }

    suspend fun setColor(id: Long, color: Int) {
        keyValue.putInt(key(WALLET_COLOR, id), color)
    }

    private suspend fun getColor(id: Long): Int {
        val value = keyValue.getInt(key(WALLET_COLOR, id))
        if (value == 0) {
            return Color.parseColor("#2E3847")
        }
        return value
    }

    fun hasWallet(): Boolean {
        return keyValue.contains(WALLET_IDS_KEY)
    }

    suspend fun delete(id: Long) {
        keyValue.remove(key(WALLET_NAME, id))
        keyValue.remove(key(WALLET_PUBLIC_KEY, id))
        keyValue.remove(key(WALLET_TYPE, id))
        keyValue.remove(key(WALLET_EMOJI, id))

        deleteId(id)
    }

    private fun key(prefix: String, id: Long): String {
        return "${prefix}_$id"
    }

    suspend fun getIds(): LongArray {
        return keyValue.getLongArray(WALLET_IDS_KEY)
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
            keyValue.remove(WALLET_IDS_KEY)
        } else {
            keyValue.putLongArray(WALLET_IDS_KEY, ids)
        }
    }
}