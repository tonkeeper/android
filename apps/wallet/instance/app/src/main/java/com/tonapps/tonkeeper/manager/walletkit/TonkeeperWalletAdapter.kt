package com.tonapps.tonkeeper.manager.walletkit

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.walletkit.add0x
import com.tonapps.blockchain.ton.TonNetwork
import io.ton.walletkit.api.ChainIds
import io.ton.walletkit.api.generated.TONNetwork
import io.ton.walletkit.api.generated.TONPreparedSignData
import io.ton.walletkit.api.generated.TONProofMessage
import io.ton.walletkit.api.generated.TONTransactionRequest
import io.ton.walletkit.config.TONWalletKitConfiguration
import io.ton.walletkit.config.SignDataType
import io.ton.walletkit.model.TONBase64
import io.ton.walletkit.model.TONHex
import io.ton.walletkit.model.TONUserFriendlyAddress
import io.ton.walletkit.model.TONWalletAdapter
import org.ton.crypto.hex

/**
 * TONWalletAdapter implementation that wraps WalletEntity.
 */
class TonkeeperWalletAdapter(
    private val wallet: WalletEntity,
) : TONWalletAdapter {

    override fun identifier(): String = wallet.id

    override fun publicKey(): TONHex {
        val publicKeyBytes = wallet.publicKey.key.toByteArray()
        return TONHex(hex(publicKeyBytes).add0x())
    }

    override fun network(): TONNetwork {
        return TONNetwork(wallet.network.value.toString())
    }

    override fun address(testnet: Boolean): TONUserFriendlyAddress {
        return TONUserFriendlyAddress(wallet.address)
    }

    override suspend fun stateInit(): TONBase64 {
        val stateInitCell = wallet.contract.stateInitCell()
        val bocBytes = org.ton.boc.BagOfCells(stateInitCell).toByteArray()
        return TONBase64.fromData(bocBytes)
    }

    override suspend fun signedSendTransaction(
        input: TONTransactionRequest,
        fakeSignature: Boolean?,
    ): TONBase64 {
        throw UnsupportedOperationException("Transaction signing handled by Tonkeeper flow")
    }

    override suspend fun signedSignMessage(
        input: TONTransactionRequest,
        fakeSignature: Boolean?,
    ): TONBase64 {
        throw UnsupportedOperationException("Message signing handled by Tonkeeper flow")
    }

    override suspend fun signedSignData(
        input: TONPreparedSignData,
        fakeSignature: Boolean?,
    ): TONHex {
        throw UnsupportedOperationException("Data signing handled by Tonkeeper flow")
    }

    override suspend fun signedTonProof(
        input: TONProofMessage,
        fakeSignature: Boolean?,
    ): TONHex {
        throw UnsupportedOperationException("Proof signing handled by Tonkeeper flow")
    }

    override fun supportedFeatures(): List<TONWalletKitConfiguration.Feature> {
        return listOf(
            TONWalletKitConfiguration.SendTransactionFeature(maxMessages = wallet.maxMessages),
            TONWalletKitConfiguration.SignDataFeature(
                types = listOf(
                    SignDataType.TEXT,
                    SignDataType.BINARY,
                    SignDataType.CELL,
                ),
            ),
        )
    }
}
