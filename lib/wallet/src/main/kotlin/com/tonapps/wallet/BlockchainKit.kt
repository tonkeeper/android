package com.tonapps.wallet

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.squareup.wire.Message
import com.tonapps.chainkit.ChainMediatorWrapper
import com.tonapps.chainkit.CryptoKitClient
import com.tonapps.chainkit.chain.bitcoin.BitcoinMediator
import com.tonapps.chainkit.chain.bitcoin.data.UnspentOutput
import com.tonapps.chainkit.core.chain.api.models.ChainError
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.api.models.NodeRes
import com.tonapps.chainkit.core.chain.api.models.SignRes
import com.tonapps.chainkit.core.chain.api.models.TxRes
import com.tonapps.chainkit.core.chain.model.MessageType
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.TonWallets
import com.tonapps.chainkit.core.chain.model.account.AccountBalance
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Hierarchy
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.chainkit.core.chain.model.account.PrivateKey
import com.tonapps.chainkit.core.chain.model.account.PubKey
import com.tonapps.chainkit.core.chain.model.account.WalletAuth
import com.tonapps.chainkit.core.chain.model.account.WalletKeyPair
import com.tonapps.chainkit.core.chain.model.signing.SigSet
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.chain.model.transaction.TransactionInfo
import com.tonapps.chainkit.core.op.Intent
import com.tonapps.chainkit.core.op.OpRes
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.chainkit.core.op.ValidationRes
import com.tonapps.chainkit.transaction.GasReservePolicy
import com.tonapps.chainkit.transaction.GasReserveResult
import com.tonapps.extensions.fromHex
import com.tonapps.chainkit.CryptoKitPlatforms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChainKitProvider(
    private val client: CryptoKitClient,
    private val txReporter: PendingTransactionReporter,
) {

    suspend fun estimateFee(transaction: Transaction): ChainRes<Fee> {
        val network = transaction.account.chain.network.type
        val mediator = client.blockchain.getMediator(network)
        return mediator.fee.calculateFee(transaction)
    }

    /** What [transaction] costs at the chain's default limit, without simulating it. */
    suspend fun defaultFee(transaction: Transaction): ChainRes<Fee> {
        val mediator = client.blockchain.getMediator(transaction.account.chain.network.type)
        return mediator.fee.getDefaultFee(transaction)
    }

    suspend fun estimateFee(
        payload: Payload,
        account: Account,
        priority: Fee.Priority = Fee.Priority.Standard,
    ): ChainRes<Fee> {
        val mediator = client.blockchain.getMediator(payload.chain.network.type)
        return mediator.operation.calculateFee(payload, account, priority)
    }

    /** The `main` payload meta that sends [amount] of [account]'s own asset to [to], as its chain signs it. */
    suspend fun transferPayload(
        account: Account,
        to: Address,
        amount: BigInteger,
        memo: String? = null,
        isMax: Boolean = false,
    ): OpRes<PayloadMeta> {
        val mediator = client.blockchain.getMediator(account.chain.network.type)
        return mediator.operation.transfer(account, to, amount, memo, isMax)
    }

    /** [operation] held to [intent], what the user agreed to: every finding the user should read before signing. */
    suspend fun validateOperation(operation: Operation, account: Account, intent: Intent): ValidationRes<Unit> {
        val mediator = client.blockchain.getMediator(account.chain.network.type)
        return mediator.operation.validate(operation, account, intent)
    }

    suspend fun signPayload(
        payload: Payload,
        account: Account,
        fee: Fee,
        nonce: BigInteger,
        wallet: MnemonicWallet,
    ): SignRes<SignedPayload> {
        val mediator = client.blockchain.getMediator(payload.chain.network.type)
        return mediator.operation.sign(payload, account, fee, nonce, wallet)
    }

    suspend fun reportPayloadSent(
        walletId: String,
        account: Account,
        payload: Payload,
        txHash: String,
        swap: PendingSwap? = null,
    ) {
        txReporter.onPayloadSent(walletId, account, payload, txHash, swap)
    }

    suspend fun sendRawTransaction(account: Account, transaction: String): NodeRes<String> {
        val mediator = client.blockchain.getMediator(account.chain.network.type)
        return mediator.transaction.sendRawTransaction(account, transaction)
    }

    suspend fun broadcastPayload(payload: SignedPayload, account: Account): NodeRes<String> {
        val mediator = client.blockchain.getMediator(account.chain.network.type)
        return mediator.broadcastAndReconcile(payload, account)
    }

    suspend fun transactionHash(payload: SignedPayload): String? {
        val mediator = client.blockchain.getMediator(payload.chain.network.type)
        return mediator.operation.transactionHash(payload).getOrNull()
    }

    suspend fun findTransaction(account: Account, hash: String): TxRes<TransactionInfo> {
        val mediator = client.blockchain.getMediator(account.chain.network.type)
        return mediator.transaction.findTransaction(account, hash)
    }

    suspend fun estimateBatchFee(batch: List<Transaction.Swap>): ChainRes<Fee> {
        val largest = batch.maxByOrNull { it.data?.length ?: 0 }
            ?: return ChainRes.err(ChainError.InternalError("Empty batch"))
        val result = estimateFee(largest)
        val fee = result.value
        if (batch.size == 1 || fee == null) {
            return result
        }
        return ChainRes.ok(Fee.Value(asset = fee.asset, amount = fee.amount * BigInteger.fromInt(batch.size)))
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
        wallet: MnemonicWallet,
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

    suspend fun tonEmulationPayload(
        memoByteSize: Int,
        account: Account,
        recipientPublicKey: PubKey,
    ): SignRes<String> = withContext(Dispatchers.Default) {
        client.tonMessageEncryptor.emulationPayload(memoByteSize, account, recipientPublicKey)
    }

    suspend fun encryptTonMemo(
        memo: String,
        account: Account,
        recipientPublicKey: PubKey,
        wallet: SigningWallet,
    ): SignRes<String> = withContext(Dispatchers.Default) {
        client.tonMessageEncryptor.encrypt(memo, account, recipientPublicKey, wallet.getPrivateKey(account.chain))
    }

    suspend fun signTransaction(
        tx: Transaction,
        fee: Fee,
        nonce: BigInteger,
        wallet: SigningWallet,
    ): SignRes<SigSet<ByteArray>> {
        val network = tx.account.chain.network.type
        val mediator = client.blockchain.getMediator(network)

        val signingResult = when (tx.account.chain) {
            is Chain.Bitcoin -> mediator.sign.transaction.signAndEncode(
                transaction = tx,
                fee = fee,
                nonce = nonce,
                wallet = wallet.requireHd(),
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

    /**
     * Signs [batch] as one transaction under one [fee] and [nonce]. On EVM an account not yet
     * delegated to Hermes is delegated by the same (type-4) transaction.
     */
    suspend fun signBatch(
        batch: List<Transaction>,
        fee: Fee,
        nonce: BigInteger,
        wallet: SigningWallet,
    ): SignRes<SigSet<ByteArray>> {
        val chain = batch.first().account.chain
        val mediator = client.blockchain.getMediator(chain.network.type)
        return mediator.sign.transaction.signAndEncode(
            batch = batch,
            fee = fee,
            nonce = nonce,
            privateKey = wallet.getPrivateKey(chain),
        )
    }

    /** [walletId] is the registered multichain wallet the send is reported for; null sends without a report. */
    suspend fun sendTransaction(
        walletId: String?,
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

        val txHash = result.getOrNull()
        if (txHash != null && walletId != null) {
            txReporter.onTransactionSent(walletId, tx, txHash, service, routeId, serviceTxId, sellBaseAmount)
        }

        return result
    }

    fun signedTransactionHex(chain: Chain, signingOutput: ByteArray): String {
        require(chain is Chain.Evm) { "A signed transaction is answered unsent on EVM only, not on $chain" }
        return evmSignedTransactionHex(signingOutput)
    }

    suspend fun buildUnsignedInputs(
        tx: Transaction,
        fee: Fee,
        nonce: BigInteger,
    ): SignRes<List<ByteArray>> {
        val mediator = client.blockchain.getMediator(tx.account.chain.network.type)
        return mediator.encodedUnsignedInputs(tx, fee, nonce)
    }

    suspend fun bitcoinUnlockedUtxos(account: Account): List<UnspentOutput> {
        val mediator = client.blockchain.getMediator(account.chain.network.type).mediator as? BitcoinMediator
            ?: throw IllegalArgumentException("Not a Bitcoin account")
        return mediator.utxoLockService.getUnlockedUtxos(account)
    }

    suspend fun findTransactionMeta(account: Account, hash: String): String? {
        val mediator = client.blockchain.getMediator(account.chain.network.type)
        return mediator.transaction.findTransaction(account, hash, BigInteger.ZERO).getOrNull()?.meta
    }

    private suspend fun <I : Message<I, *>, O : Message<O, *>> ChainMediatorWrapper<I, O>.encodedUnsignedInputs(
        tx: Transaction,
        fee: Fee,
        nonce: BigInteger,
    ): SignRes<List<ByteArray>> {
        val signer = sign.transaction
        val inputs = if (tx.account.chain is Chain.Bitcoin) {
            signer.buildSigningInput(tx, fee, nonce, null as Hierarchy?)
        } else {
            signer.buildSigningInput(tx, fee, nonce, null as PrivateKey?)
        }
        return inputs.map { set -> set.outputs.map { signer.inputAdapter.encode(it) } }
    }

    fun buildTonV5R1StateInit(publicKeyHex: String, testnet: Boolean = false): ByteArray? {
        val bytes = publicKeyHex
            .removePrefix("0x")
            .fromHex()

        return TonWallets.v5R1StateInit(bytes, testnet)
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

    companion object {
        // Loads ChainKit's native library; throws UnsatisfiedLinkError when the device cannot.
        fun init() {
            CryptoKitPlatforms().init()
        }
    }
}
