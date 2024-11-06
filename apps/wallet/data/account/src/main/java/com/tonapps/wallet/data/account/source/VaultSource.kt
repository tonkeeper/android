package com.tonapps.wallet.data.account.source

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.tonapps.blockchain.ton.extensions.getPrivateKey
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.extensions.getByteArray
import com.tonapps.extensions.putByteArray
import com.tonapps.security.Security
import com.tonapps.security.clear
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.mnemonic.Mnemonic

internal class VaultSource(context: Context) {

    private companion object {
        private const val NAME = "vault"
        private const val PRIVATE_KEY_PREFIX = "private_key"
        private const val MNEMONIC_KEY_PREFIX = "mnemonic"
        private const val KEY_ALIAS = "_com_tonapps_vault_master_key_"
    }

    private val prefs = Security.pref(context, KEY_ALIAS, NAME)

    fun getMnemonic(publicKey: PublicKeyEd25519): Array<String>? {
        val value = prefs.getString(mnemonicKey(publicKey), null) ?: return null
        val mnemonic = value.split(",").toTypedArray()
        if (mnemonic.isEmpty()) {
            return null
        }
        return mnemonic
    }

    fun addMnemonic(mnemonic: List<String>): PublicKeyEd25519 {
        val seed = Mnemonic.toSeed(mnemonic)
        val privateKey = PrivateKeyEd25519(seed)
        val publicKey = privateKey.publicKey()

        prefs.edit {
            putString(mnemonicKey(publicKey), mnemonic.joinToString(","))
            putByteArray(privateKey(publicKey), seed)
        }

        seed.clear()
        return publicKey
    }

    suspend fun getPrivateKey(publicKey: PublicKeyEd25519): PrivateKeyEd25519? = withContext(Dispatchers.IO) {
        val privateKey = prefs.getPrivateKey(privateKey(publicKey))
        if (privateKey == null) {
            val fromMnemonic = getPrivateKeyFromMnemonic(publicKey) ?: return@withContext null
            prefs.edit {
                putByteArray(privateKey(publicKey), fromMnemonic.key.toByteArray())
            }
            fromMnemonic
        } else {
            privateKey
        }
    }

    private fun getPrivateKeyFromMnemonic(publicKey: PublicKeyEd25519): PrivateKeyEd25519? {
        val mnemonic = getMnemonic(publicKey) ?: return null
        val seed = Mnemonic.toSeed(mnemonic.toList())
        val privateKey = PrivateKeyEd25519(seed)
        seed.clear()
        return privateKey
    }

    private fun privateKey(publicKey: PublicKeyEd25519) = key(PRIVATE_KEY_PREFIX, publicKey)

    private fun mnemonicKey(publicKey: PublicKeyEd25519) = key(MNEMONIC_KEY_PREFIX, publicKey)

    private fun key(prefix: String, publicKey: PublicKeyEd25519): String {
        return "${prefix}_${publicKey.hex()}"
    }
}