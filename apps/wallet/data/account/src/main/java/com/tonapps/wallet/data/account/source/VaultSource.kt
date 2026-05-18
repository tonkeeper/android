package com.tonapps.wallet.data.account.source

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.blockchain.MnemonicHelper
import com.tonapps.blockchain.ton.extensions.decodePrivateKey
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.extensions.putByteArray
import com.tonapps.security.KeyHelperException
import com.tonapps.security.Security
import com.tonapps.security.clear
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519

internal class VaultSource(context: Context) {

    private companion object {
        private const val NAME = "vault"
        private const val PRIVATE_KEY_PREFIX = "private_key"
        private const val MNEMONIC_KEY_PREFIX = "mnemonic"
        private const val KEY_ALIAS = "_com_tonapps_vault_master_key_"
    }

    private val prefs = Security.pref(context, KEY_ALIAS, NAME)

    fun getVaultKeys(): String {
        val result = JSONObject()
        for ((key, value) in prefs.all()) {
            result.put(key, value.toString())
        }
        return result.toString()
    }

    fun getMnemonic(publicKey: PublicKeyEd25519): Array<String>? {
        val value = prefs.get(mnemonicKey(publicKey)) ?: return null
        val mnemonic = value.split(",").toTypedArray()
        if (mnemonic.isEmpty()) {
            return null
        }
        return mnemonic
    }

    fun addMnemonic(mnemonic: List<String>): PublicKeyEd25519 {
        val privateKey = MnemonicHelper.privateKey(mnemonic)
        val seed = privateKey.key.toByteArray()
        val publicKey = privateKey.publicKey()

        try {
            val success = prefs.transaction {
                putString(mnemonicKey(publicKey), mnemonic.joinToString(","))
                putByteArray(pkKey(publicKey), seed)
            }

            if (!success) {
                throw KeyHelperException.AddMnemonic().also {
                    FirebaseCrashlytics.getInstance()
                        .recordException(it)
                }
            }
        } finally {
            seed.clear()
        }

        return publicKey
    }

    suspend fun getPrivateKey(publicKey: PublicKeyEd25519): PrivateKeyEd25519 = withContext(Dispatchers.IO) {
        val privateKey = prefs.get(pkKey(publicKey))
            ?.decodePrivateKey()

        if (privateKey == null) {
            val fromMnemonic = getPrivateKeyFromMnemonic(publicKey)
                ?: run {
                    throw KeyHelperException.GetPkFromMnemonic().also {
                        FirebaseCrashlytics.getInstance()
                            .recordException(it)
                    }
                }

            val success = prefs.transaction {
                putByteArray(pkKey(publicKey), fromMnemonic.key.toByteArray())
            }

            if (!success) {
                throw KeyHelperException.GetMnemonic().also {
                    FirebaseCrashlytics.getInstance()
                        .recordException(it)
                }
            }

            fromMnemonic
        } else {
            privateKey
        }
    }

    private fun getPrivateKeyFromMnemonic(publicKey: PublicKeyEd25519): PrivateKeyEd25519? {
        val mnemonic = getMnemonic(publicKey) ?: return null
        val privateKey = MnemonicHelper.privateKey(mnemonic.toList())
        return privateKey
    }

    private fun pkKey(publicKey: PublicKeyEd25519) = key(PRIVATE_KEY_PREFIX, publicKey)

    private fun mnemonicKey(publicKey: PublicKeyEd25519) = key(MNEMONIC_KEY_PREFIX, publicKey)

    private fun key(prefix: String, publicKey: PublicKeyEd25519): String {
        return "${prefix}_${publicKey.hex()}"
    }
}