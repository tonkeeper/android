package com.tonapps.wallet.data.multichain.lighter

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.async.Async
import com.tonapps.chainkit.CryptoKitClient
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.chainkit.core.common.getOrElse
import com.tonapps.chainkit.core.common.hexWithPrefix
import com.tonapps.chainkit.core.op.OpError
import com.tonapps.chainkit.core.op.OpRes
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.lighter.LighterClient
import com.tonapps.chainkit.lighter.LighterConstants
import com.tonapps.chainkit.lighter.model.L2ApproveIntegratorTxInfo
import com.tonapps.chainkit.lighter.model.LighterApproveIntegratorTxReq
import com.tonapps.chainkit.lighter.model.LighterTransactOpts
import com.tonapps.chainkit.lighter.model.LighterTransferTxReq
import com.tonapps.chainkit.lighter.model.LighterWithdrawTxReq
import com.tonapps.chainkit.perpetual.PerpetualChain
import com.tonapps.mvi.graph.KResult
import com.tonapps.wallet.data.multichain.perps.PerpsDepositRoute
import com.tonapps.wallet.data.multichain.wallet.CredentialIds
import io.perpsapi.models.DepositQuote
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class PerpetualRepository internal constructor(
    private val credentialsStore: LighterCredentialsStore,
    private val client: CryptoKitClient,
) {

    private val lighter = LighterClient()

    suspend fun getAccount(walletId: String): StoredLighterAccount? {
        return credentialsStore.loadAccount(walletId)
    }

    suspend fun signingCredentials(walletId: String): LighterSigningCredentials? = withContext(Async.Io) {
        val account = credentialsStore.loadAccount(walletId) ?: return@withContext null
        val privateKey = credentialsStore.loadL2PrivateKey(walletId) ?: return@withContext null

        try {
            val key = client.perpetual.lighter.signingKey(
                privateKeyHex = privateKey.hexWithPrefix(),
                accountIndex = account.accountIndex,
                chainId = LighterConstants.ChainId,
            ).getOrElse { throw it }

            LighterSigningCredentials(
                key = key,
                accountIndex = account.accountIndex,
                apiKeyIndex = account.apiKeyIndex,
                chainId = LighterConstants.ChainId,
                environment = CredentialIds.LIGHTER_MAINNET,
            )
        } finally {
            privateKey.fill(0)
        }
    }

    suspend fun saveActivation(walletId: String, account: StoredLighterAccount, l2PrivateKey: ByteArray) {
        credentialsStore.saveActivation(walletId, l2PrivateKey, account)
    }

    suspend fun authorization(walletId: String, deadlineUnixSeconds: Long): String? = withContext(Async.Io) {
        val account = credentialsStore.loadAccount(walletId) ?: return@withContext null
        val privateKey = credentialsStore.loadL2PrivateKey(walletId) ?: return@withContext null

        try {
            lighter.initialize()
            lighter.authToken(
                privateKeyHex = privateKey.hexWithPrefix(),
                accountIndex = account.accountIndex,
                apiKeyIndex = account.apiKeyIndex,
                deadlineUnixSeconds = deadlineUnixSeconds,
            )
        } finally {
            privateKey.fill(0)
        }
    }

    suspend fun approveIntegratorTransaction(
        walletId: String,
        request: LighterApproveIntegratorTxReq,
        nonce: Long,
        expiredAtMs: Long,
    ): L2ApproveIntegratorTxInfo? = withContext(Async.Io) {
        val account = credentialsStore.loadAccount(walletId) ?: return@withContext null
        val privateKey = credentialsStore.loadL2PrivateKey(walletId) ?: return@withContext null

        try {
            lighter.initialize()
            lighter.newTxClient(
                privateKeyHex = privateKey.hexWithPrefix(),
                accountIndex = account.accountIndex,
                apiKeyIndex = account.apiKeyIndex,
                chainId = LighterConstants.ChainId,
            ).getApproveIntegratorTransaction(
                req = request,
                ops = LighterTransactOpts(expiredAt = expiredAtMs, nonce = nonce),
            )
        } finally {
            privateKey.fill(0)
        }
    }

    suspend fun signWithdraw(
        walletId: String,
        assetIndex: Short,
        routeType: Int,
        amountUnits: Long,
        nonce: Long,
        expiredAtMs: Long,
    ): SignedLighterTransaction? = withContext(Async.Io) {
        val account = credentialsStore.loadAccount(walletId) ?: return@withContext null
        val privateKey = credentialsStore.loadL2PrivateKey(walletId) ?: return@withContext null

        try {
            lighter.initialize()
            val txClient = lighter.newTxClient(
                privateKeyHex = privateKey.hexWithPrefix(),
                accountIndex = account.accountIndex,
                apiKeyIndex = account.apiKeyIndex,
                chainId = LighterConstants.ChainId,
            )
            val txInfo = txClient.getWithdrawTransaction(
                req = LighterWithdrawTxReq(assetIndex = assetIndex, routeType = routeType, amount = amountUnits),
                ops = LighterTransactOpts(nonce = nonce, expiredAt = expiredAtMs),
            )
            SignedLighterTransaction(
                txType = txInfo.txType,
                txInfo = txInfo.getTxInfo(),
                txHash = txInfo.getTxHash(),
                nonce = nonce,
                expiredAtMs = expiredAtMs,
            )
        } finally {
            privateKey.fill(0)
        }
    }

    suspend fun signTransfer(
        walletId: String,
        request: LighterTransferTxReq,
        nonce: Long,
        expiredAtMs: Long,
        signOwnerMessage: suspend (String) -> String?,
    ): SignedLighterTransaction? = withContext(Async.Io) {
        val account = credentialsStore.loadAccount(walletId) ?: return@withContext null
        val privateKey = credentialsStore.loadL2PrivateKey(walletId) ?: return@withContext null

        try {
            lighter.initialize()
            val txClient = lighter.newTxClient(
                privateKeyHex = privateKey.hexWithPrefix(),
                accountIndex = account.accountIndex,
                apiKeyIndex = account.apiKeyIndex,
                chainId = LighterConstants.ChainId,
            )
            val txInfo = txClient.getTransferTransaction(
                request,
                LighterTransactOpts(nonce = nonce, expiredAt = expiredAtMs),
                signOwnerMessage,
            )
            SignedLighterTransaction(
                txType = txInfo.txType,
                txInfo = txInfo.getTxInfo(),
                txHash = txInfo.getTxHash(),
                nonce = nonce,
                expiredAtMs = expiredAtMs,
            )
        } finally {
            privateKey.fill(0)
        }
    }

    /**
     * Derives the wallet's L2 key from its mnemonic and keeps it under the session key, so a quote
     * can name it without asking for the passcode. A slot that holds a key already is left as it is:
     * the key an account is registered with is replaced only by [saveActivation].
     */
    suspend fun saveTradingKey(walletId: String, wallet: CryptoWallet) = withContext(Async.Io) {
        val key = client.perpetual.getAccountMediator(PerpetualChain.Lighter).keySource(wallet).deriveTradingKey()
        try {
            credentialsStore.keepL2Key(walletId, key)
        } finally {
            key.fill(0)
        }
    }

    /** Which of [walletIds] keep no L2 key yet: a wallet from before keys were kept, or whose keeping failed. */
    suspend fun walletsWithoutTradingKey(walletIds: List<String>): List<String> = withContext(Async.Io) {
        walletIds.filterNot { credentialsStore.hasL2Key(it) }
    }

    /**
     * The wallet's L2 key, caller-owned: zero it when done. Null until [saveTradingKey] or an
     * activation kept one; once an account is stored, the key it is registered with.
     */
    suspend fun tradingKey(walletId: String): ByteArray? = credentialsStore.loadL2PrivateKey(walletId)

    /** The public half of [tradingKey]; null until one is kept for [walletId]. */
    suspend fun tradingPublicKey(walletId: String): String? = withContext(Async.Io) {
        val key = credentialsStore.loadL2PrivateKey(walletId) ?: return@withContext null
        try {
            publicKeyOf(key)
        } finally {
            key.fill(0)
        }
    }

    /** Drops the account kept outside the `credential` table, whose rows go with the wallet. */
    fun forgetAccount(walletId: String) = credentialsStore.forgetAccount(walletId)

    suspend fun publicKeyOf(privateKey: ByteArray): String = withContext(Async.Io) {
        lighter.initialize()
        lighter.derivePublicKey(privateKey.hexWithPrefix())
    }

    fun depositQuote(quote: DepositQuote): KResult<PerpetualDepositQuote, PerpsQuoteError> {
        val sourceAmount = quote.input.amount.toUnits()
            ?: return KResult.Err(PerpsQuoteError.MalformedAmount("input.amount"))
        val depositAmount = quote.output.minAmount.toUnits()
            ?: return KResult.Err(PerpsQuoteError.MalformedAmount("output.min_amount"))
        val operation = operation(quote.operation).getOrElse { return KResult.Err(PerpsQuoteError.InvalidOperation(it)) }
        val route = if (quote.provider == PROVIDER_DIRECT) {
            PerpsDepositRoute.DIRECT_GATEWAY
        } else {
            PerpsDepositRoute.CROSSCHAIN_SWAP
        }
        return KResult.Ok(
            PerpetualDepositQuote(
                quoteId = quote.quoteId,
                executionId = quote.tracking?.executionId,
                route = route,
                expiresAtMs = quote.expiresAt.toInstant().toEpochMilli(),
                sourceAmount = sourceAmount,
                depositAmount = depositAmount,
                operation = operation,
                preview = quote.preview,
                estimatedDurationSeconds = quote.estimatedDurationSeconds?.takeIf { it >= 0 }?.toLong(),
            ),
        )
    }

    // The operation comes as the envelope the service sent: chainkit reads it as it is.
    private fun operation(envelope: JsonObject): OpRes<Operation> {
        val id = envelope.string(OPERATION_ID) ?: return OpRes.err(OpError.MissingField(OPERATION_ID))
        return Operation.parse(id, envelope.toString())
    }

    private fun JsonObject.string(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull

    private fun String.toUnits(): BigInteger? {
        return takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.let(BigInteger::parseString)
    }

    private companion object {
        const val PROVIDER_DIRECT = "direct"
        const val OPERATION_ID = "id"
    }
}
