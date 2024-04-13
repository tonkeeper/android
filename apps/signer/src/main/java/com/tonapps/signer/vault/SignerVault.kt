package com.tonapps.signer.vault

import android.content.Context
import com.tonapps.signer.extensions.securePrefs
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.mnemonic.Mnemonic
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
): Vault(context.securePrefs(name)) {

    constructor(context: Context): this(context, "signer")

    fun setMnemonic(secret: SecretKey, id: Long, mnemonic: List<String>) {
        putString(secret, id, mnemonic.joinToString(","))
        secret.safeDestroy()
    }

    fun getMnemonic(secret: SecretKey, id: Long): List<String> {
        val list = getString(secret, id).split(",")
        secret.safeDestroy()
        return list
    }

    fun getPrivateKey(secret: SecretKey, id: Long): PrivateKeyEd25519 {
        val mnemonic = getMnemonic(secret, id)
        val seed = Mnemonic.toSeed(mnemonic)
        val privateKey = PrivateKeyEd25519(seed)
        seed.clear()
        tryCallGC()
        return privateKey
    }
}