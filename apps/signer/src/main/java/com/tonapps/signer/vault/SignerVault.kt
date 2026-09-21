package com.tonapps.signer.vault

import android.content.Context
import com.tonapps.security.Security
import org.ton.kotlin.crypto.PrivateKeyEd25519
import org.ton.kotlin.crypto.mnemonic.Mnemonic
import com.tonapps.security.clear
import com.tonapps.security.safeDestroy
import com.tonapps.security.tryCallGC
import com.tonapps.security.vault.Vault
import com.tonapps.security.vault.getString
import com.tonapps.security.vault.putString
import javax.crypto.SecretKey

class SignerVault(
    context: Context,
    name: String,
): Vault(Security.pref(context, name, name)) {

    constructor(context: Context): this(context, "signer")

    suspend fun setMnemonic(secret: SecretKey, id: Long, mnemonic: List<String>) {
        putString(secret, id, mnemonic.joinToString(","))
        secret.safeDestroy()
    }

    suspend fun getMnemonic(secret: SecretKey, id: Long): List<String> {
        val list = getString(secret, id).split(",")
        secret.safeDestroy()
        return list
    }

    suspend fun getPrivateKey(secret: SecretKey, id: Long): PrivateKeyEd25519 {
        val mnemonic = getMnemonic(secret, id)
        val seed = Mnemonic(mnemonic).toSeed()
        val privateKey = PrivateKeyEd25519(seed)
        seed.clear()
        tryCallGC()
        return privateKey
    }
}