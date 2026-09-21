package com.tonapps.wallet

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.CryptoKitClient
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.api.models.NodeRes
import com.tonapps.chainkit.core.chain.api.models.SignRes
import com.tonapps.chainkit.core.chain.model.MessageType
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.AccountBalance
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.chainkit.core.chain.model.account.WalletAuth
import com.tonapps.chainkit.core.chain.model.account.WalletKeyPair
import com.tonapps.chainkit.core.chain.model.signing.SigSet
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.transaction.GasReservePolicy
import com.tonapps.chainkit.transaction.GasReserveResult
import com.tonapps.extensions.fromHex
import com.trustwallet.core.PublicKey
import com.trustwallet.core.PublicKeyType
import com.trustwallet.core.TONWallet

class ApprovalTransaction(
    val txExact: Transaction.Call,
    val txMax: Transaction.Call,
    val fee: Fee,
    val isUnlimited: Boolean,
) {
    fun signableTx(unlimited: Boolean): Transaction.Call {
        return if (unlimited) {
            txMax
        } else {
            txExact
        }
    }
}

class ChainKitProvider(
    private val client: CryptoKitClient,
    private val txReporter: PendingTransactionReporter,
) {

    suspend fun estimateFee(transaction: Transaction): ChainRes<Fee> {
        val network = transaction.account.chain.network.type
        val mediator = client.blockchain.getMediator(network)
        return mediator.fee.calculateFee(transaction)
    }

    suspend fun getBalance(account: Account): NodeRes<AccountBalance> {
        val mediator = client.blockchain.getMediator(account.chain.network.type)
        return mediator.account.loadBalance(account)
    }

    suspend fun getNonce(account: Account): NodeRes<BigInteger> {
        val mediator = client.blockchain.getMediator(account.chain.network.type)
        return mediator.account.estimateNonce(account)
    }

    suspend fun signMessage(
        data: String,
        type: MessageType,
        account: Account,
        wallet: CryptoWallet,
    ): SignRes<String>? {
        val network = account.chain.network.type
        val mediator = client.blockchain.getMediator(network)

        return mediator.sign.message?.sign(
            chain = account.chain,
            privateKey = wallet.getPrivateKey(account.chain),
            messageType = type,
            message = data,
        )
    }

    suspend fun signTransaction(
        tx: Transaction,
        fee: Fee,
        nonce: BigInteger,
        wallet: CryptoWallet,
    ): SignRes<SigSet<ByteArray>> {
        val network = tx.account.chain.network.type
        val mediator = client.blockchain.getMediator(network)

        val signingResult = when (tx.account.chain) {
            is Chain.Bitcoin -> mediator.sign.transaction.signAndEncode(
                transaction = tx,
                fee = fee,
                nonce = nonce,
                wallet = wallet,
            )
            else -> mediator.sign.transaction.signAndEncode(
                transaction = tx,
                fee = fee,
                nonce = nonce,
                privateKey = wallet.getPrivateKey(tx.account.chain),
            )
        }

        return signingResult
    }

    suspend fun sendTransaction(
        walletId: String,
        tx: Transaction,
        data: ByteArray,
        service: String? = null,
        routeId: String? = null,
        serviceTxId: String? = null,
        sellBaseAmount: BigInteger? = null,
    ): NodeRes<String> {
        val network = tx.account.chain.network.type
        val mediator = client.blockchain.getMediator(network)
        val result = mediator.transaction.sendEncodedTransaction(tx.account, data)

        result.getOrNull()?.let { txHash ->
            txReporter.onTransactionSent(walletId, tx, txHash, service, routeId, serviceTxId, sellBaseAmount)
        }

        return result
    }

    fun buildTonV5R1StateInit(publicKeyHex: String, testnet: Boolean = false): ByteArray? {
        val bytes = publicKeyHex
            .removePrefix("0x")
            .fromHex()

        val publicKey = PublicKey(bytes, PublicKeyType.ED25519)
        val walletId = if (testnet) {
            TON_V5R1_WALLET_ID_TESTNET
        } else {
            TON_V5R1_WALLET_ID_MAINNET
        }
        val base64Boc = TONWallet.buildV5R1StateInit(publicKey, TON_WORKCHAIN, walletId) ?: return null
        return java.util.Base64.getDecoder().decode(base64Boc)
    }

    fun signWalletRegisterProof(
        keyPair: WalletKeyPair,
        deviceId: String,
        challenge: String,
        accounts: List<Account>,
    ): WalletAuth.RegisterProof {
        return client.auth.signWalletRegisterProofV2(
            keyPair = keyPair,
            deviceId = deviceId,
            challenge = challenge,
            accounts = accounts,
        )
    }

    // Standard base64 of the V5R1 state init BoC — matches Cell.base64() (used elsewhere for
    // walletStateInit) and the format TON Connect dApps parse when verifying ton_proof.
    fun buildTonV5R1StateInitBase64(publicKeyHex: String, testnet: Boolean = false): String? {
        val boc = buildTonV5R1StateInit(publicKeyHex, testnet) ?: return null
        return java.util.Base64.getEncoder().encodeToString(boc)
    }

    suspend fun calculateGasReserve(
        account: Account,
        energy: Account,
        amount: BigInteger,
        isMax: Boolean,
        fee: Fee?,
        policy: GasReservePolicy,
    ): NodeRes<GasReserveResult> {
        return client.tx.calculateGasReserve(account, energy, amount, isMax, fee, policy)
    }

    fun buildApproval(
        tx: Transaction.Swap,
        approvalData: String,
        approvalFee: Fee,
    ): ApprovalTransaction? {
        val result = client.tx.buildApproval(tx, approvalData)
            ?: return null

        return ApprovalTransaction(
            txExact = result.txExact,
            txMax = result.txMax,
            fee = approvalFee,
            isUnlimited = false,
        )
    }

    companion object {
        private const val TON_WORKCHAIN: Int = 0
        private const val TON_V5R1_WALLET_ID_MAINNET: Int = 2147483409
        private const val TON_V5R1_WALLET_ID_TESTNET: Int = 0x7FFFFFFD

        fun init() {
            System.loadLibrary("TrustWalletCore")
        }
    }
}
