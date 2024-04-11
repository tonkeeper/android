package com.tonapps.wallet.data.account

import com.tonapps.extensions.toByteArray
import com.tonapps.wallet.data.account.entities.ProofDomainEntity
import com.tonapps.wallet.data.account.entities.ProofEntity
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.block.AddrStd
import org.ton.crypto.base64
import org.ton.crypto.digest.sha256
import org.ton.crypto.hex

object WalletProof {

    private const val tonProofPrefix = "ton-proof-item-v2/"
    private const val tonConnectPrefix = "ton-connect"
    private val prefixMessage = hex("ffff") + tonConnectPrefix.toByteArray()
    private val prefixItem = tonProofPrefix.toByteArray()

    fun signTonkeeper(
        address: AddrStd,
        secretKey: PrivateKeyEd25519,
        payload: String,
        stateInit: String,
    ): ProofEntity {
        val domain = ProofDomainEntity("tonkeeper.com")
        return sign(address, secretKey, payload, domain, stateInit)
    }

    fun sign(
        address: AddrStd,
        secretKey: PrivateKeyEd25519,
        payload: String,
        domain: ProofDomainEntity,
        stateInit: String,
    ): ProofEntity {
        val timestamp = System.currentTimeMillis() / 1000L
        val message = createMessage(timestamp, payload.toByteArray(), domain, address)
        val signatureMessage = sha256(message)

        val body = sha256(prefixMessage + signatureMessage)
        val signature = secretKey.sign(body)

        return ProofEntity(
            timestamp = timestamp,
            domain = domain,
            payload = payload,
            signature = base64(signature),
            stateInit = stateInit,
        )
    }

    private fun createMessage(
        timestamp: Long,
        payload: ByteArray,
        domain: ProofDomainEntity,
        address: AddrStd
    ): ByteArray {
        val prefix = prefixItem

        val addressWorkchainBuffer = address.workchainId.toByteArray()
        val addressHashBuffer = address.address.toByteArray()

        val domainLengthBuffer = domain.lengthBytes.toByteArray()
        val domainBuffer = domain.value.toByteArray()

        val timestampBuffer = timestamp.toByteArray()

        return prefix + addressWorkchainBuffer + addressHashBuffer + domainLengthBuffer + domainBuffer + timestampBuffer + payload
    }
}