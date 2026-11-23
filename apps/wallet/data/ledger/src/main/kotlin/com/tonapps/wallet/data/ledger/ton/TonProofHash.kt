package com.tonapps.wallet.data.ledger.ton

import com.tonapps.blockchain.ton.contract.WalletV4R2Contract
import kotlinx.io.bytestring.ByteString
import org.ton.kotlin.crypto.PublicKeyEd25519
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

internal object TonProofHash {

    private val ITEM_PREFIX = "ton-proof-item-v2/".toByteArray(Charsets.US_ASCII)
    private val CONNECT_PREFIX = byteArrayOf(0xff.toByte(), 0xff.toByte()) + "ton-connect".toByteArray(Charsets.US_ASCII)

    fun compute(publicKey: ByteArray, domain: String, timestamp: Long, payload: String): ByteArray {
        val address = WalletV4R2Contract(publicKey = PublicKeyEd25519(ByteString(publicKey))).address
        val domainBytes = domain.toByteArray(Charsets.UTF_8)
        val message = ByteArrayOutputStream().apply {
            write(ITEM_PREFIX)
            write(ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).putInt(address.workchainId).array())
            write(address.address.toByteArray())
            write(ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(domainBytes.size).array())
            write(domainBytes)
            write(ByteBuffer.allocate(Long.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(timestamp).array())
            write(payload.toByteArray(Charsets.UTF_8))
        }.toByteArray()
        val inner = sha256(message)
        return sha256(CONNECT_PREFIX + inner)
    }

    fun verify(publicKey: ByteArray, hash: ByteArray, signature: ByteArray): Boolean =
        PublicKeyEd25519(ByteString(publicKey)).verifySignature(hash, signature)

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
}
