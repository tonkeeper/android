package com.tonkeeper.core.tonconnect

import com.tonkeeper.core.tonconnect.models.TCDomain
import com.tonkeeper.core.tonconnect.models.reply.TCProofItemReplySuccess
import core.extensions.toByteArray
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.block.AddrStd
import org.ton.crypto.base64
import org.ton.crypto.digest.sha256
import org.ton.crypto.hex

internal class Proof {

    companion object {
        const val tonProofPrefix = "ton-proof-item-v2/"
        const val tonConnectPrefix = "ton-connect"

        private val prefixMessage = hex("ffff") + tonConnectPrefix.toByteArray()

        private val prefixItem = tonProofPrefix.toByteArray()
    }

    fun createProofItemReplySuccess(
        payload: String?,
        domain: TCDomain,
        address: AddrStd,
        privateWalletKey: PrivateKeyEd25519
    ): TCProofItemReplySuccess {
        val timestamp = System.currentTimeMillis() / 1000L
        val message = createMessage(timestamp, payload?.toByteArray() ?: byteArrayOf(), domain, address)
        val signatureMessage = sha256(message)

        val body = sha256(prefixMessage + signatureMessage)
        val signature = privateWalletKey.sign(body)

        return TCProofItemReplySuccess(
            proof = TCProofItemReplySuccess.Proof(
                timestamp = timestamp,
                domain = TCProofItemReplySuccess.Domain(
                    lengthBytes = domain.size,
                    value = domain.domain,
                ),
                payload = payload,
                signature = base64(signature),
            )
        )
    }

    private fun createMessage(
        timestamp: Long,
        payload: ByteArray,
        domain: TCDomain,
        address: AddrStd
    ): ByteArray {
        val prefix = prefixItem

        val addressWorkchainBuffer = address.workchainId.toByteArray()
        val addressHashBuffer = address.address.toByteArray()

        val domainLengthBuffer = domain.size.toByteArray()
        val domainBuffer = domain.domain.toByteArray()

        val timestampBuffer = timestamp.toByteArray()

        return prefix + addressWorkchainBuffer + addressHashBuffer + domainLengthBuffer + domainBuffer + timestampBuffer + payload
    }
}
