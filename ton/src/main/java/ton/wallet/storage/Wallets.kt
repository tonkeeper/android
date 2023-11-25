package ton.wallet.storage

import core.keyvalue.KeyValue
import org.ton.api.pub.PublicKeyEd25519
import ton.wallet.Wallet

internal class Wallets(
    private val keyValue: KeyValue
) {

    companion object {
        private const val WALLET_IDS_KEY = "wallets"
        private const val WALLET_NAME = "name"
        private const val WALLET_PUBLIC_KEY = "public_key"
    }

    suspend fun get(id: Long): Wallet? {
        val publicKey = getPublicKey(id) ?: return null
        val name = getName(id)
        return Wallet(
            id = id,
            name = name,
            publicKey = publicKey
        )
    }

    suspend fun add(wallet: Wallet) {
        setPublicKey(wallet.id, wallet.publicKey)
        setName(wallet.id, wallet.name)

        addId(wallet.id)
    }

    suspend fun setName(id: Long, name: String?) {
        keyValue.putString(key(WALLET_NAME, id), name)
    }

    private suspend fun getName(id: Long): String? {
        return keyValue.getString(key(WALLET_NAME, id))
    }

    private suspend fun getPublicKey(id: Long): PublicKeyEd25519? {
        val key = key(WALLET_PUBLIC_KEY, id)
        return keyValue.getByteArray(key)?.let { PublicKeyEd25519(it) }
    }

    private suspend fun setPublicKey(id: Long, publicKey: PublicKeyEd25519) {
        val key = key(WALLET_PUBLIC_KEY, id)
        keyValue.putByteArray(key, publicKey.key.toByteArray())
    }

    suspend fun delete(id: Long) {
        keyValue.remove(key(WALLET_NAME, id))
        keyValue.remove(key(WALLET_PUBLIC_KEY, id))

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