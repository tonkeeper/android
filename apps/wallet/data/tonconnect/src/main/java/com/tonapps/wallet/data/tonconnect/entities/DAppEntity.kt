package com.tonapps.wallet.data.tonconnect.entities

import android.net.Uri
import android.os.Parcelable
import com.tonapps.security.CryptoBox
import com.tonapps.security.Sodium
import com.tonapps.security.hex
import com.tonapps.wallet.data.account.entities.ProofDomainEntity
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class DAppEntity(
    val url: String,
    val walletId: String,
    val accountId: String,
    val testnet: Boolean,
    val clientId: String,
    val keyPair: CryptoBox.KeyPair,
    val enablePush: Boolean = false,
    val manifest: DAppManifestEntity,
): Parcelable {

    @IgnoredOnParcel
    val uri: Uri = Uri.parse(url)

    @IgnoredOnParcel
    val domain = ProofDomainEntity(uri.host!!)

    @IgnoredOnParcel
    val publicKeyHex: String
        get() = hex(keyPair.publicKey)

    val uniqueId: String
        get() = "$walletId:$url"

    fun encrypt(body: String): ByteArray {
        return encrypt(body.toByteArray())
    }

    fun encrypt(body: ByteArray): ByteArray {
        val nonce = CryptoBox.nonce()
        val cipher = ByteArray(body.size + Sodium.cryptoBoxMacBytes())
        Sodium.cryptoBoxEasy(cipher, body, body.size, nonce, clientId.hex(), keyPair.privateKey)
        return nonce + cipher
    }

    fun decrypt(body: String): ByteArray {
        return decrypt(body.toByteArray())
    }

    fun decrypt(body: ByteArray): ByteArray {
        val nonce = body.sliceArray(0 until Sodium.cryptoBoxNonceBytes())
        val cipher = body.sliceArray(Sodium.cryptoBoxNonceBytes() until body.size)
        val message = ByteArray(cipher.size - Sodium.cryptoBoxMacBytes())
        Sodium.cryptoBoxOpenEasy(message, cipher, cipher.size, nonce, clientId.hex(), keyPair.privateKey)
        return message
    }

}