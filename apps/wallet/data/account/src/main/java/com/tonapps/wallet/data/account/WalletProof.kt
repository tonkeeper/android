package com.tonapps.wallet.data.account

import com.tonapps.blockchain.ton.proof.TONProof
import com.tonapps.wallet.data.account.entities.ProofDomainEntity
import com.tonapps.wallet.data.account.entities.ProofEntity
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.block.AddrStd
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
        val result = TONProof.sign(address, secretKey, payload, domain.value)
        return ProofEntity(
            timestamp = result.timestamp,
            domain = domain,
            payload = payload,
            signature = result.signature,
            stateInit = stateInit
        )
    }

}