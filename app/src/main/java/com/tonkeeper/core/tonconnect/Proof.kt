package com.tonkeeper.core.tonconnect

import com.tonkeeper.core.tonconnect.models.TCKeyPair
import com.tonkeeper.core.tonconnect.models.TCProofPayload
import com.tonkeeper.core.tonconnect.models.reply.TCProofItemReplySuccess
import core.extensions.toBase64
import org.ton.block.AddrStd
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

internal class Proof {

    companion object {
        const val tonProofPrefix = "ton-proof-item-v2/"
        const val tonConnectPrefix = "ton-connect"
    }

    private val sha256 = MessageDigest.getInstance("SHA-256")

    fun createProofItemReplySuccess(
        payload: String,
        host: String,
        accountId: String,
        keyPair: TCKeyPair
    ): TCProofItemReplySuccess {
        val proof = createProofPayload(payload, host, accountId)

        val bufferDigest = sha256.digest(proof.bufferToSign)

        val signature = keyPair.sing(bufferDigest)

        return TCProofItemReplySuccess(
            proof = TCProofItemReplySuccess.Proof(
                timestamp = proof.timestamp,
                domain = TCProofItemReplySuccess.Domain(
                    lengthBytes = proof.domainBuffer.size.toLong(),
                    value = proof.domainBuffer.toString(Charsets.UTF_8)
                ),
                payload = proof.payload,
                signature = signature.toBase64(),
            )
        )
    }

    private fun createProofPayload(
        payload: String,
        host: String,
        accountId: String
    ): TCProofPayload {
        val timestamp = (System.currentTimeMillis() / 1000L).toInt()
        val timestampBuffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
            putLong(timestamp.toLong())
        }

        val domainBuffer = host.toByteArray(Charsets.UTF_8)
        val domainLengthBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(domainBuffer.size)
        }


        val address = AddrStd.parse(accountId)
        val addressWorkchainBuffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(address.workchainId)
        }

        val addressBuffer = ByteBuffer.allocate(addressWorkchainBuffer.capacity() + address.address.size)
        addressBuffer.put(addressWorkchainBuffer.array())
        addressBuffer.put(address.address.toByteArray())

        val tonProofPrefix = Proof.tonProofPrefix.toByteArray(Charsets.UTF_8)
        val payloadBuffer = payload.toByteArray(Charsets.UTF_8)
        val messageBuffer = ByteBuffer.allocate(
            tonProofPrefix.size +
                    addressBuffer.array().size +
                    domainLengthBuffer.array().size +
                    domainBuffer.size +
                    timestampBuffer.array().size +
                    payloadBuffer.size
        )

        messageBuffer.put(tonProofPrefix)
        messageBuffer.put(addressBuffer.array())
        messageBuffer.put(domainLengthBuffer.array())
        messageBuffer.put(domainBuffer)
        messageBuffer.put(timestampBuffer.array())
        messageBuffer.put(payloadBuffer)


        val messageDigest = sha256.digest(messageBuffer.array())

        val tonConnectPrefix = Proof.tonConnectPrefix.toByteArray(Charsets.UTF_8)
        val bufferToSign = ByteBuffer.allocate(2 + tonConnectPrefix.size + messageDigest.size).apply {
            put(0xff.toByte())
            put(0xff.toByte())
            put(tonConnectPrefix)
            put(messageDigest)
        }

        return TCProofPayload(
            timestamp = timestamp,
            bufferToSign = bufferToSign.array(),
            domainBuffer = domainBuffer,
            payload = payload,
            origin = Proof.tonConnectPrefix
        )
    }
}
