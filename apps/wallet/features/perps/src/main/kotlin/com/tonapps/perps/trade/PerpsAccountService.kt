package com.tonapps.perps.trade

import android.content.Context
import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.chainkit.CryptoKitClient
import com.tonapps.chainkit.core.chain.model.MessageType
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.chainkit.core.chain.model.account.WalletKind
import com.tonapps.chainkit.perpetual.PerpetualAccountKeySource
import com.tonapps.chainkit.perpetual.PerpetualChain
import com.tonapps.log.L
import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.data.LIGHTER_API_KEY_INDEX
import com.tonapps.perps.data.PerpsAccount
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsPortfolio
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.lighter.LighterSigningCredentials
import com.tonapps.wallet.data.multichain.lighter.PerpetualRepository
import com.tonapps.wallet.data.multichain.lighter.StoredLighterAccount
import com.tonapps.wallet.data.passcode.PasscodeManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

sealed interface PerpsActivation {
    data class Ready(val credentials: LighterSigningCredentials) : PerpsActivation
    data object NoAccount : PerpsActivation
    data object Failed : PerpsActivation
}

enum class PerpsUnlockFailure { COMING_SOON, CANCELLED, FAILED }

class PerpsAccountService(
    private val repository: PerpsRepository,
    private val perpetualRepository: PerpetualRepository,
    private val client: CryptoKitClient,
    private val mcAccountRepository: McAccountRepository,
    private val passcodeManager: PasscodeManager,
    private val tierUpgrader: PerpsAccountTierUpgrader,
    private val scope: CoroutineScope,
) {

    private val roTokenRefusedUntilMs = ConcurrentHashMap<String, Long>()

    private val accountCheckedAtMs = ConcurrentHashMap<String, Long>()

    private val roTokenMintedAtMs = ConcurrentHashMap<String, Long>()

    private val roTokenMints = ConcurrentHashMap<String, Deferred<Boolean>>()

    suspend fun account(walletId: String): KResult<PerpsAccount, PerpsError> {
        return repository.account(walletId)
    }

    suspend fun ensureBound(walletId: String, wallet: CryptoWallet): PerpsAccount? {
        val account = repository.account(walletId).getOrNull() ?: return null
        if (account.l1Address != null) {
            return account
        }
        return bind(walletId, wallet)
    }

    suspend fun signOwnerMessage(wallet: CryptoWallet, message: String): String? {
        return client.perpetual.getAccountMediator(PerpetualChain.Lighter).keySource(wallet).signOwnerMessage(message)
    }

    /** The public half of the wallet's L2 key, kept since its creation, import or last unlock: no passcode. */
    suspend fun keptTradingPublicKey(walletId: String): String? = perpetualRepository.tradingPublicKey(walletId)

    suspend fun isActivated(walletId: String): Boolean {
        return try {
            perpetualRepository.getAccount(walletId) != null
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }

            L.e(e, "perps: activation check failed for $walletId")
            false
        }
    }

    suspend fun ensureActivated(walletId: String, unlockWallet: suspend () -> CryptoWallet): PerpsActivation {
        val stored = perpetualRepository.signingCredentials(walletId)
        if (stored != null) {
            tierUpgrader.ensurePlusBeforeTrading(walletId)
            return PerpsActivation.Ready(stored)
        }

        var account = when (val result = repository.account(walletId)) {
            is KResult.Ok -> result.value
            is KResult.Err -> return PerpsActivation.Failed
        }

        // TODO remove the unlock: binding the L1 address and registering a key the venue does not hold
        //  belong to the deposit (change_pub_key); activation should only store what the venue has.
        var wallet: CryptoWallet? = null
        if (account.l1Address == null) {
            wallet = unlockWallet()
            account = bind(walletId, wallet) ?: return PerpsActivation.Failed
        }
        val accountIndex = account.accountIndex ?: return PerpsActivation.NoAccount
        val l1Address = account.l1Address ?: return PerpsActivation.Failed

        val keySource = if (holdsKeptKey(walletId, account)) {
            KeptKeySource(walletId, l1Address)
        } else {
            client.perpetual.getAccountMediator(PerpetualChain.Lighter).keySource(wallet ?: unlockWallet())
        }
        val credentials = register(walletId, accountIndex, keySource)
            ?: return PerpsActivation.Failed
        if (account.hasRoToken != true) {
            mintRoToken(walletId)
        }
        tierUpgrader.ensurePlusAfterActivation(walletId)

        return PerpsActivation.Ready(credentials)
    }

    suspend fun unlock(wallet: WalletEntity, context: Context): KResult<LighterSigningCredentials, PerpsUnlockFailure> {
        if (wallet.type != WalletType.Multichain) {
            return KResult.Err(PerpsUnlockFailure.COMING_SOON)
        }

        return withContext(Async.Io) {
            try {
                val outcome = ensureActivated(wallet.id) {
                    passcodeManager.unlockMultichainVault(context) { coder ->
                        mcAccountRepository.getCryptoWallet(wallet.id, coder)
                            ?: throw IllegalStateException("No multichain wallet for ${wallet.id}")
                    }
                }
                when (outcome) {
                    is PerpsActivation.Ready -> KResult.Ok(outcome.credentials)
                    PerpsActivation.NoAccount -> KResult.Err(PerpsUnlockFailure.COMING_SOON)
                    PerpsActivation.Failed -> KResult.Err(PerpsUnlockFailure.FAILED)
                }
            } catch (_: CancellationException) {
                KResult.Err(PerpsUnlockFailure.CANCELLED)
            } catch (e: Throwable) {
                L.e(e)
                KResult.Err(PerpsUnlockFailure.FAILED)
            }
        }
    }

    fun refreshRoToken(walletId: String, tokenRefused: Boolean) {
        val now = System.currentTimeMillis()
        if (now - (accountCheckedAtMs[walletId] ?: 0L) < ACCOUNT_CHECK_INTERVAL_MS ||
            now - (roTokenMintedAtMs[walletId] ?: 0L) < RO_TOKEN_REMINT_INTERVAL_MS
        ) {
            return
        }
        accountCheckedAtMs[walletId] = now
        scope.launch {
            if (perpetualRepository.getAccount(walletId) == null) {
                return@launch
            }
            // has_ro_token stays true for a stored token the backend can no longer decrypt.
            val account = if (tokenRefused) {
                null
            } else {
                repository.account(walletId).getOrNull() ?: return@launch
            }
            if (account?.hasRoToken != true) {
                mintRoToken(walletId)
            }
        }
    }

    suspend fun reissueRoToken(walletId: String): Boolean {
        return mintRoToken(walletId)
    }

    suspend fun activateWithKeptKey(walletId: String): Boolean {
        return try {
            ensureActivated(walletId) { throw PasscodeRequired() } is PerpsActivation.Ready
        } catch (_: PasscodeRequired) {
            false
        }
    }

    private suspend fun register(
        walletId: String,
        accountIndex: Long,
        keySource: PerpetualAccountKeySource,
    ): LighterSigningCredentials? {
        val mediator = client.perpetual.getAccountMediator(PerpetualChain.Lighter)
        try {
            val expectedPublicKey = keySource.deriveTradingKey().let { key ->
                try {
                    perpetualRepository.publicKeyOf(key)
                } finally {
                    key.fill(0)
                }
            }
            val state = mediator.activate(
                keySource = keySource,
                delegate = PerpsTkAccountDelegate(walletId, repository, expectedPublicKey),
                nowUnixMs = System.currentTimeMillis(),
            )
            if (state.hasL2Key != true) {
                return null
            }

            val privateKey = keySource.deriveTradingKey()
            try {
                perpetualRepository.saveActivation(
                    walletId = walletId,
                    account = StoredLighterAccount(
                        accountIndex = accountIndex,
                        apiKeyIndex = LIGHTER_API_KEY_INDEX,
                    ),
                    l2PrivateKey = privateKey,
                )
            } finally {
                privateKey.fill(0)
            }

            val credentials = perpetualRepository.signingCredentials(walletId)
            if (credentials != null) {
                return credentials
            }
            L.e("perps: Lighter credentials were not persisted for $walletId")

            return null
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e, "perps: Lighter activation failed for $walletId")
            return null
        }
    }

    // A key a deposit registered: on the venue in the app's slot, and the one kept here.
    private suspend fun holdsKeptKey(walletId: String, account: PerpsAccount): Boolean {
        val kept = perpetualRepository.tradingPublicKey(walletId) ?: return false
        return account.holdsKey(kept) == true
    }

    /**
     * The wallet's L2 key as kept since its creation, import or last unlock, for an account that
     * already holds it: activation then registers nothing, so nothing is signed with the L1 key.
     */
    private inner class KeptKeySource(
        private val walletId: String,
        private val l1Address: String,
    ) : PerpetualAccountKeySource {

        override suspend fun ownerAddress(): String = l1Address

        override suspend fun deriveTradingKey(): ByteArray {
            return checkNotNull(perpetualRepository.tradingKey(walletId)) { "perps: no trading key kept for $walletId" }
        }

        override suspend fun signOwnerMessage(message: String): String? = null
    }

    private suspend fun bind(walletId: String, wallet: CryptoWallet): PerpsAccount? {
        val chain = Chain.Ethereum.Mainnet
        try {
            val l1Address = PerpsAccountProofs.normalizeL1Address(wallet.getAddress(chain).display)
            val deadline = nowSeconds() + BIND_DEADLINE_SECONDS
            val signed = client.blockchain.getMediator(chain.network.type).sign.message?.sign(
                chain = chain,
                privateKey = wallet.getPrivateKey(chain),
                message = PerpsAccountProofs.linkWalletTypedData(walletId, deadline),
                messageType = MessageType.TypedMessage,
            )?.unwrap() ?: throw IllegalStateException("perps: no message signer for ${chain.network.type}")

            val proof = PerpsAccountProofs.bindProof(wallet.walletKeyPair(WalletKind.Multichain), walletId, l1Address)

            val signature = PerpsAccountProofs.normalizeHex(signed, withPrefix = true)
            return when (val result = repository.bindAccount(walletId, deadline, signature, proof)) {
                is KResult.Ok -> {
                    val bound = result.value.l1Address?.let {
                        runCatching { PerpsAccountProofs.normalizeL1Address(it) }.getOrNull()
                    }
                    if (bound == l1Address) {
                        result.value
                    } else {
                        L.e("perps: bind answered ${result.value.l1Address}, signed $l1Address")
                        null
                    }
                }

                is KResult.Err -> {
                    L.e("perps: bind refused for $walletId (${result.value})")
                    null
                }
            }
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e, "perps: bind failed for $walletId")
            return null
        }
    }

    private suspend fun mintRoToken(walletId: String): Boolean {
        if ((roTokenRefusedUntilMs[walletId] ?: 0L) > System.currentTimeMillis()) {
            return false
        }
        // Lazy so the mint can't finish and leave the map before it's put in.
        return roTokenMints.computeIfAbsent(walletId) {
            scope.async(start = CoroutineStart.LAZY) {
                try {
                    issueRoTokenWithRetries(walletId)
                } finally {
                    roTokenMints.remove(walletId)
                }
            }
        }.await()
    }

    private suspend fun issueRoTokenWithRetries(walletId: String): Boolean {
        return withTimeoutOrNull(RO_TOKEN_BUDGET_MS) {
            repeat(RO_TOKEN_ATTEMPTS) { attempt ->
                val result = try {
                    issueRoToken(walletId) ?: return@withTimeoutOrNull false
                } catch (e: Throwable) {
                    if (e is CancellationException) {
                        throw e
                    }
                    L.e(e, "perps: ro token mint failed for $walletId")
                    KResult.Err(PerpsError.Unknown)
                }
                when (result) {
                    is KResult.Ok -> return@withTimeoutOrNull true
                    is KResult.Err -> {
                        if (result.value in RO_TOKEN_REFUSALS) {
                            roTokenRefusedUntilMs[walletId] = System.currentTimeMillis() + RO_TOKEN_REFUSAL_COOLDOWN_MS
                            return@withTimeoutOrNull false
                        }
                        if (result.value !in RO_TOKEN_RETRYABLE || attempt == RO_TOKEN_ATTEMPTS - 1) {
                            L.e("perps: ro token mint failed for $walletId (${result.value})")
                            return@withTimeoutOrNull false
                        }
                        delay(RO_TOKEN_RETRY_DELAY_MS)
                    }
                }
            }
            false
        } ?: false
    }

    private suspend fun issueRoToken(walletId: String): KResult<Unit, PerpsError>? {
        val auth = perpetualRepository.authorization(walletId, nowSeconds() + RO_TOKEN_AUTH_SECONDS) ?: return null

        return when (val result = repository.issueRoToken(walletId, auth)) {
            is KResult.Ok -> {
                roTokenMintedAtMs[walletId] = System.currentTimeMillis()
                KResult.Ok(Unit)
            }

            is KResult.Err -> KResult.Err(result.value)
        }
    }

    private fun nowSeconds(): Long = System.currentTimeMillis() / 1_000

    private class PasscodeRequired : Exception()

    private companion object {
        const val BIND_DEADLINE_SECONDS = 300L
        const val RO_TOKEN_ATTEMPTS = 3
        const val RO_TOKEN_RETRY_DELAY_MS = 1_000L
        const val RO_TOKEN_BUDGET_MS = 8_000L
        const val RO_TOKEN_AUTH_SECONDS = 600L
        const val RO_TOKEN_REFUSAL_COOLDOWN_MS = 60_000L
        const val ACCOUNT_CHECK_INTERVAL_MS = 60_000L

        // Every mint opens a ~30 s window where the backend can't read auto-close state.
        const val RO_TOKEN_REMINT_INTERVAL_MS = 600_000L

        val RO_TOKEN_REFUSALS = setOf(PerpsError.Conflict, PerpsError.BadRequest, PerpsError.Forbidden)
        val RO_TOKEN_RETRYABLE = setOf(PerpsError.Network, PerpsError.ServiceUnavailable, PerpsError.Unknown)
    }
}

suspend fun <T> PerpsAccountService.reissuingRoToken(
    walletId: String,
    read: suspend () -> KResult<T, PerpsError>,
): KResult<T, PerpsError> {
    val result = read()
    if (result.isRoTokenUnavailable() && reissueRoToken(walletId)) {
        return read()
    }
    return result
}

suspend fun PerpsAccountService.readPortfolio(
    repository: PerpsRepository,
    walletId: String,
): KResult<PerpsPortfolio, PerpsError> {
    val result = reissuingRoToken(walletId) { repository.getPortfolio(walletId) }
    if (!result.isRoTokenUnavailable() || isActivated(walletId)) {
        return result
    }
    return repository.getPortfolioWithoutOrders(walletId)
}

fun KResult<*, PerpsError>.isRoTokenUnavailable(): Boolean =
    this is KResult.Err && value == PerpsError.RoTokenUnavailable
