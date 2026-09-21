package com.tonapps.tonkeeper.manager.walletkit

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.log.L
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.multichain.account.AccountEntity
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

/**
 * Multichain-aware adapter: address comes from MC's stored displayAddress,
 * state init is computed via TW Core (matching the same V5R1 derivation MC used),
 * so the dApp can verify ton_proof against the address MC actually holds.
 */
class MultichainWalletAdapter(
    private val wallet: WalletEntity,
    private val account: AccountEntity,
    private val chainKitProvider: ChainKitProvider,
) : TONWalletAdapter {

    override fun identifier(): String = wallet.id

    override fun publicKey(): TONHex = TONHex(account.publicKey)

    override fun network(): TONNetwork {
        return TONNetwork(wallet.network.value.toString())
    }

    override fun address(testnet: Boolean): TONUserFriendlyAddress {
        L.d("[MultichainWalletAdapter] address() requested testnet=$testnet -> ${account.displayAddress}")
        return TONUserFriendlyAddress(account.displayAddress)
    }

    override suspend fun stateInit(): TONBase64 {
        val boc = chainKitProvider.buildTonV5R1StateInit(account.publicKey, testnet = false)
            ?: throw IllegalStateException("Failed to build multichain V5R1 state init for wallet ${wallet.id}")
        L.d("[MultichainWalletAdapter] stateInit() built ${boc.size} bytes for ${account.displayAddress}")
        return TONBase64.fromData(boc)
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
