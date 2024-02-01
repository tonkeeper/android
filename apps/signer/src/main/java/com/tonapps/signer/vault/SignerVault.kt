package com.tonapps.signer.vault

import android.content.Context
import android.util.Log
import com.tonapps.signer.extensions.securePrefs
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.cell.Cell
import org.ton.mnemonic.Mnemonic
import security.clear
import security.decrypt
import security.safeDestroy
import security.vault.Vault
import security.vault.getString
import security.vault.putString
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
        return privateKey
    }
}