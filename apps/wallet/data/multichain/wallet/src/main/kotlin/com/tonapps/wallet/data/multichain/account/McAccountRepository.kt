package com.tonapps.wallet.data.multichain.account

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.tonapps.async.Async
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.tracer.AnalyticException
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.chainkit.core.chain.model.account.WalletKeyPair
import com.tonapps.chainkit.core.chain.model.account.WalletKind
import com.tonapps.chainkit.core.secure.mnemonic.EntropySize
import com.tonapps.chainkit.core.secure.mnemonic.Mnemonic
import com.tonapps.log.L
import com.tonapps.security.multichain.MnemonicCoder
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.withRetry
import com.tonapps.wallet.data.multichain.wallet.CredentialDao
import com.tonapps.wallet.data.multichain.wallet.CredentialEntity
import com.tonapps.wallet.data.multichain.wallet.McBackupSource
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletType
import com.tonapps.wallet.data.multichain.wallet.WalletBundleDao
import com.tonapps.wallet.data.multichain.wallet.WalletDao
import com.tonapps.wallet.data.cache.JsonResponseCache
import com.tonapps.wallet.data.cache.JsonCacheRepository
import com.tonapps.wallet.data.multichain.device.SecureDeviceRepository
import com.tonapps.wallet.data.multichain.vault.WalletKeyRepository
import com.tonapps.wallet.data.cache.JsonResponseCacheScope
import com.tonapps.wallet.data.settings.SettingsRepository
import io.walletapi.models.GetDeviceBindingsRequest
import io.walletapi.models.GetWalletAssets200Response
import io.walletapi.models.RegisterWalletsRequest
import io.walletapi.models.SaveWalletAssetsFiltersRequest
import io.walletapi.models.SyncStatus
import io.walletapi.models.SaveWalletAssetsFiltersRequestChangesInner
import io.walletapi.models.UnregisterWalletsRequest
import io.walletapi.models.WalletAccount
import io.walletapi.models.WalletRegisterItem
import io.walletapi.models.WalletRegisterResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private const val SYNC_POLL_INTERVAL_MS = 3_000L
private const val SYNC_MAX_ATTEMPTS = 10

private const val BINDINGS_BATCH_LIMIT = 32

const val WALLET_ASSETS_PAGE_SIZE = 25

class WalletRegistrationException(cause: Throwable) : Exception(cause)

interface McAccountRepository {

    val refreshTrigger: SharedFlow<Unit>

    fun refresh()

    suspend fun getAccounts(chain: Chain): List<AccountEntity>

    suspend fun findAccount(
        walletId: String,
        assetId: String,
        currencyOverride: String? = null,
        forceRefresh: Boolean = false,
    ): AccountWithDetails?

    fun createMnemonic(): Mnemonic

    suspend fun createWallet(
        mnemonic: Mnemonic,
        mnemonicCoder: MnemonicCoder,
        name: String,
        emoji: CharSequence,
        color: Int,
        tonWalletType: Address.Type = Address.Type.TonV5R1,
    ): McWalletEntity

    suspend fun getCryptoWallet(walletId: String, mnemonicCoder: MnemonicCoder): CryptoWallet?

    suspend fun getWalletKeyPair(walletId: String): WalletKeyPair?

    suspend fun getMnemonic(walletId: String, mnemonicCoder: MnemonicCoder): Array<String>?

    suspend fun syncAllWallets(mnemonicCoder: MnemonicCoder)

    suspend fun syncWalletBindings()

    // TODO remove
    suspend fun restoreMissingAppKeys(mnemonicCoder: MnemonicCoder)

    suspend fun syncChains(walletId: String, cryptoWallet: CryptoWallet)

    suspend fun getWallet(walletId: String): McWalletEntity?

    suspend fun walletExists(mnemonic: Mnemonic): Boolean

    suspend fun getWalletSyncStatus(walletId: String): SyncStatus

    suspend fun awaitWalletSync(walletId: String)

    suspend fun getWallets(): List<McWalletEntity>

    suspend fun getWalletsCount(): Int

    suspend fun getTonAccount(walletId: String): AccountEntity?

    suspend fun deleteWallet(walletId: String)

    suspend fun deleteAllWallets()

    suspend fun updateWalletLabel(walletId: String, name: String, emoji: String, color: Int)

    suspend fun updateBackup(walletId: String, backupType: McBackupSource?, backupTime: Long?)

    suspend fun fetchAccounts(
        walletId: String,
        currency: String,
        availableOnly: Boolean = false,
        showHidden: Boolean = false,
        showAll: Boolean = false,
        query: String? = null,
        network: String? = null,
        cursor: String? = null,
        limit: Int? = null,
        capabilities: List<AssetCapability>? = null,
        verifiedOnly: Boolean = false,
        hideDust: Boolean = false,
    ): AccountsWithTotal

    suspend fun getCachedAccounts(
        walletId: String,
        currency: String,
        availableOnly: Boolean = false,
        showHidden: Boolean = false,
        showAll: Boolean = false,
        query: String? = null,
        network: String? = null,
        capabilities: List<AssetCapability>? = null,
        verifiedOnly: Boolean = false,
        hideDust: Boolean = false,
    ): AccountsWithTotal?

    suspend fun getCoinAccounts(walletId: String): List<AccountEntity>

    suspend fun setAssetVisibility(walletId: String, assetId: String, visible: Boolean)

    suspend fun setAssetsVisibility(walletId: String, visibilityByAssetId: Map<String, Boolean>)
}

class AccountRepositoryImpl(
    private val accountDao: AccountDao,
    private val walletDao: WalletDao,
    private val credentialDao: CredentialDao,
    private val walletBundleDao: WalletBundleDao,
    private val jsonCacheRepository: JsonCacheRepository,
    private val appKeyRepository: WalletKeyRepository,
    private val deviceRepository: SecureDeviceRepository,
    private val provider: ChainKitProvider,
    private val settings: SettingsRepository,
    private val api: API,
) : McAccountRepository {

    private val _refreshTrigger = MutableSharedFlow<Unit>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val refreshTrigger: SharedFlow<Unit> = _refreshTrigger.asSharedFlow()

    init {
        _refreshTrigger.tryEmit(Unit)
    }

    private val mainnetChains: Set<Chain>
        get() = Chain.all.filter { it.network.mode == Network.Mode.Mainnet }.toSet()

    // v2 multichain registration parameters. The server binds these into the wallet_id and the
    // register proof, so the same values must feed both the local wallet_id derivation and the proof.
    private val walletKind = WalletKind.Multichain
    private var _accounts = ConcurrentHashMap<String, AccountWithDetails>()

    private val bindingsMutex = Mutex()

    override fun refresh() {
        _refreshTrigger.tryEmit(Unit)
    }

    override fun createMnemonic(): Mnemonic {
        return Mnemonic.create(EntropySize.B128)
    }

    // Materialize the mnemonic words only inside this call and zero the array afterwards (the joined
    // String passed to HDWallet is immutable and cannot be zeroed — confined here and short-lived).
    private fun cryptoWallet(mnemonic: Mnemonic): CryptoWallet {
        return CryptoWallet.fromMnemonic(mnemonic)
    }

    override suspend fun createWallet(
        mnemonic: Mnemonic,
        mnemonicCoder: MnemonicCoder,
        name: String,
        emoji: CharSequence,
        color: Int,
        tonWalletType: Address.Type,
    ): McWalletEntity = withContext(Dispatchers.IO) {
        val encryptedData = mnemonicCoder.encryptMnemonic(mnemonic)
        val credential = CredentialEntity(data = encryptedData)

        val cryptoWallet = cryptoWallet(mnemonic)

        val wallet = McWalletEntity(
            id = walletId(cryptoWallet),
            credentialId = credential.id,
            name = name,
            emoji = emoji.toString(),
            color = color,
            type = McWalletType.Multicoin,
            tonWalletType = tonWalletType,
        )

        val accounts = createAccountsForWallet(wallet, cryptoWallet)

        val keyPair = cryptoWallet.walletKeyPair(walletKind)
        try {
            bindingsMutex.withLock {
                registerWallet(WalletRegistration(wallet.id, keyPair, accounts))

                val walletAppCredential = appKeyRepository.sealedEntity(
                    walletId = wallet.id,
                    appPrivateKey = keyPair.privateKey.copyOf(),
                )
                walletBundleDao.insertNewWallet(credential, wallet, accounts, walletAppCredential)
                _refreshTrigger.tryEmit(Unit)
            }
        } finally {
            keyPair.privateKey.fill(0)
        }

        wallet
    }

    override suspend fun syncAllWallets(
        mnemonicCoder: MnemonicCoder,
    ) = withContext(Dispatchers.IO) {
        val wallets = walletDao.getWallets()
        for (wallet in wallets) {
            val credential = credentialDao.getCredential(wallet.credentialId) ?: continue
            val cryptoWallet = mnemonicCoder.decryptMnemonic(credential.data)
                .use { cryptoWallet(it) }

            val before = accountDao.getAccountsByWalletId(wallet.id).size
            syncChains(wallet.id, cryptoWallet)
            val accounts = accountDao.getAccountsByWalletId(wallet.id)

            if (accounts.size != before) {
                val keyPair = saveWalletAppKeyPair(wallet.id, cryptoWallet)

                try {
                    runCatching { registerWallet(WalletRegistration(wallet.id, keyPair, accounts)) }
                        .onFailure { L.e(it) }
                } finally {
                    keyPair.privateKey.fill(0)
                }
            }
        }
    }

    // TODO remove
    override suspend fun restoreMissingAppKeys(
        mnemonicCoder: MnemonicCoder,
    ) = withContext(Dispatchers.IO) {
        for (wallet in walletDao.getWallets()) {
            if (appKeyRepository.hasAppKey(wallet.id)) {
                continue
            }

            val credential = credentialDao.getCredential(wallet.credentialId) ?: continue
            val cryptoWallet = mnemonicCoder.decryptMnemonic(credential.data)
                .use { cryptoWallet(it) }

            val keyPair = saveWalletAppKeyPair(wallet.id, cryptoWallet)
            keyPair.privateKey.fill(0)
        }
    }

    override suspend fun syncChains(walletId: String, cryptoWallet: CryptoWallet) {
        withContext(Dispatchers.IO) {
            walletDao.getWallet(walletId) ?: return@withContext

            val existingChains =
                accountDao.getAccountsByWalletId(walletId).map { it.chain.coinAssetId }.toSet()
            val missingChains = mainnetChains.filter { chain ->
                chain.coinAssetId !in existingChains
            }

            if (missingChains.isEmpty()) {
                return@withContext
            }

            accountDao.insertAccounts(accountEntities(walletId, cryptoWallet, missingChains))
            _refreshTrigger.tryEmit(Unit)
        }
    }


    override suspend fun getCryptoWallet(
        walletId: String,
        mnemonicCoder: MnemonicCoder,
    ): CryptoWallet? {
        return withContext(Dispatchers.IO) {
            val wallet = walletDao.getWallet(walletId) ?: return@withContext null
            val credential =
                credentialDao.getCredential(wallet.credentialId) ?: return@withContext null
            val cryptoWallet = mnemonicCoder.decryptMnemonic(credential.data)
                .use { cryptoWallet(it) }

            if (!appKeyRepository.hasAppKey(walletId)) {
                appKeyRepository.save(
                    walletId,
                    cryptoWallet.walletKeyPair(walletKind).privateKey
                )
            }
            cryptoWallet
        }
    }

    override suspend fun getMnemonic(
        walletId: String,
        mnemonicCoder: MnemonicCoder,
    ): Array<String>? = withContext(Dispatchers.IO) {
        val w = walletDao.getWallet(walletId) ?: return@withContext null
        val credential = credentialDao.getCredential(w.credentialId) ?: return@withContext null
        mnemonicCoder.decryptMnemonic(credential.data)
            .use { it.toWordsUnsafe().toList().toTypedArray() } // TODO replace with indexes
    }

    override suspend fun getWallet(walletId: String): McWalletEntity? = withContext(Async.Io) {
        walletDao.getWallet(walletId)
    }

    override suspend fun walletExists(mnemonic: Mnemonic): Boolean = withContext(Async.Io) {
        walletDao.walletExists(walletId(cryptoWallet(mnemonic)))
    }

    private fun walletId(cryptoWallet: CryptoWallet): String {
        return cryptoWallet.walletIdV2(walletKind)
    }

    override suspend fun getWalletKeyPair(walletId: String): WalletKeyPair? {
        val appPrivateKey = appKeyRepository.getAppPrivateKey(walletId)
            ?: return null
        return WalletKeyPair.fromPrivateKey(appPrivateKey, walletKind)
    }

    private suspend fun getWalletAppKeyPair(walletId: String): WalletKeyPair {
        return getWalletKeyPair(walletId)
            ?: throw IllegalStateException("No app key available (walletId=$walletId)")
    }

    private suspend fun saveWalletAppKeyPair(walletId: String, cryptoWallet: CryptoWallet): WalletKeyPair {
        val keyPair = cryptoWallet.walletKeyPair(walletKind)
        appKeyRepository.save(walletId, keyPair.privateKey.copyOf())
        return keyPair
    }

    override suspend fun getWalletSyncStatus(walletId: String): SyncStatus = withContext(Dispatchers.IO) {
        api.multichain.wallets.getWalletSyncStatus(walletId, xWalletId = walletId)
    }

    override suspend fun awaitWalletSync(walletId: String) {
        repeat(SYNC_MAX_ATTEMPTS) {
            try {
                val syncStatus = getWalletSyncStatus(walletId)
                L.d("Wallet sync status: ${syncStatus.status}")
                when (syncStatus.status) {
                    SyncStatus.Status.ready -> return
                    SyncStatus.Status.failed -> return
                    SyncStatus.Status.in_progress -> delay(SYNC_POLL_INTERVAL_MS)
                }
            } catch (e: Throwable) {
                L.e(e)
                delay(SYNC_POLL_INTERVAL_MS)
            }
        }
    }

    override suspend fun getWallets(): List<McWalletEntity> = withContext(Async.Io) {
        walletDao.getWallets()
    }

    override suspend fun getWalletsCount(): Int = withContext(Async.Io) {
        walletDao.getWalletsCount()
    }

    // The ton/mainnet chain carries two variants (v4r2 + w5); resolve the one the wallet was created
    // with (its selected primary version). Callers (SignProof, unified lookup) expect that address.
    // Fall back to any TON account so wallets created before the multi-variant change (single TON
    // account, addressType=Default) still resolve.
    override suspend fun getTonAccount(walletId: String): AccountEntity? = withContext(Async.Io) {
        val wallet = walletDao.getWallet(walletId) ?: return@withContext null
        resolvePrimaryAccount(
            accounts = accountDao.getAccountsByWalletId(walletId),
            chain = Chain.Ton.Mainnet,
            tonWalletType = wallet.tonWalletType,
        )
    }

    private fun resolvePrimaryAccount(
        accounts: List<AccountEntity>,
        chain: Chain,
        tonWalletType: Address.Type?,
    ): AccountEntity? {
        val chainAccounts = accounts.filter { it.chain == chain }

        if (chain is Chain.Ton && tonWalletType != null) {
            return chainAccounts.firstOrNull { it.addressType == tonWalletType }
                ?: chainAccounts.firstOrNull()
        }

        return chainAccounts.firstOrNull()
    }

    override suspend fun deleteWallet(walletId: String) = withContext(Dispatchers.IO) {
        bindingsMutex.withLock {
            val wallet = walletDao.getWallet(walletId) ?: return@withLock
            unregisterWalletBindings(listOf(walletId))
            jsonCacheRepository.deleteByWalletId(walletId)
            walletBundleDao.deleteWallet(
                walletId = walletId,
                credentialIds = listOf(wallet.credentialId, appKeyRepository.credentialId(walletId)),
            )
        }
    }

    override suspend fun deleteAllWallets() = withContext(Dispatchers.IO) {
        bindingsMutex.withLock {
            val wallets = walletDao.getWallets()
            if (wallets.isEmpty()) {
                return@withLock
            }

            val walletIds = wallets.map { it.id }
            val credentialIds = wallets.flatMap {
                listOf(it.credentialId, appKeyRepository.credentialId(it.id))
            }

            unregisterWalletBindings(walletIds)
            jsonCacheRepository.deleteByWalletIds(walletIds)
            walletBundleDao.deleteWallets(walletIds, credentialIds)
        }
    }

    override suspend fun updateWalletLabel(
        walletId: String,
        name: String,
        emoji: String,
        color: Int,
    ) = withContext(Async.Io) {
        walletDao.updateLabel(walletId, name, emoji, color)
        _refreshTrigger.tryEmit(Unit)
        return@withContext
    }

    override suspend fun updateBackup(
        walletId: String,
        backupType: McBackupSource?,
        backupTime: Long?,
    ) = withContext(Async.Io) {
        walletDao.updateBackup(walletId, backupType, backupTime)
        _refreshTrigger.tryEmit(Unit)
        return@withContext
    }

    private class WalletRegistration(
        val walletId: String,
        val keyPair: WalletKeyPair,
        val accounts: List<AccountEntity>,
    )

    private suspend fun registerWallet(registration: WalletRegistration) {
        val result = registerWallets(listOf(registration))[registration.walletId]
        if (result?.status != WalletRegisterResult.Status.ok) {
            val error = WalletRegistrationException(
                IllegalStateException(
                    "Wallet registration rejected (walletId=${registration.walletId}, error=${result?.error})"
                )
            )
            AnalyticsHelper.Default.captureException(
                AnalyticException.WalletAuth("wallet registration rejected", error)
            )
            throw error
        }
    }

    private suspend fun registerWallets(
        registrations: List<WalletRegistration>,
    ): Map<String, WalletRegisterResult> = withContext(Dispatchers.IO) {
        registrations.chunked(BINDINGS_BATCH_LIMIT)
            .fold(emptyMap()) { results, batch -> results + registerBatch(batch) }
    }

    private suspend fun registerBatch(
        batch: List<WalletRegistration>,
    ): Map<String, WalletRegisterResult> {
        try {
            val deviceId = deviceRepository.requireDeviceId()

            val challenge = api.multichain.wallets.getWalletChallenge()
                .challenge

            val items = batch.map { registration ->
                val apiAccounts = registration.accounts.mapNotNull { entity ->
                    val apiChain = entity.chain.toApiChain() ?: return@mapNotNull null
                    entity to apiChain
                }

                val proof = provider.signWalletRegisterProof(
                    keyPair = registration.keyPair,
                    deviceId = deviceId,
                    challenge = challenge,
                    accounts = apiAccounts.map { (entity, _) -> entity.toProofAccount() },
                )

                WalletRegisterItem(
                    accounts = apiAccounts.map { (entity, apiChain) ->
                        WalletAccount(
                            chain = apiChain,
                            address = entity.displayAddress,
                            type = entity.addressType.toApiAccountType(),
                        )
                    },
                    walletId =  proof.walletId,
                    walletProof = proof.signature,
                )
            }

            val response = api.multichain.auth.registerWallets(
                RegisterWalletsRequest(challenge = challenge, wallets = items)
            )

            return response.results.mapNotNull { result ->
                batch.getOrNull(result.index)?.let { it.walletId to result }
            }.toMap()
        } catch (e: Throwable) {
            L.e(e)
            throw WalletRegistrationException(e)
        }
    }

    override suspend fun syncWalletBindings() = withContext(Dispatchers.IO) {
        bindingsMutex.withLock {
            val walletIds = walletDao.getWallets().map { it.id }
            if (walletIds.isEmpty()) {
                return@withLock
            }

            deviceRepository.requireDeviceId()

            val bindings = api.multichain.auth.getDeviceBindings(
                GetDeviceBindingsRequest(wallets = walletIds), F = settings.installId
            )

            registerWalletBindings(bindings.unknown)
            // TODO check backend also deleted it unregisterWalletBindings(bindings.extra.filterNot { walletDao.walletExists(it) })
        }
    }

    private suspend fun registerWalletBindings(
        walletIds: List<String>,
    ): Boolean = withContext(Dispatchers.IO) {
        if (walletIds.isEmpty()) {
            return@withContext true
        }

        val registrations = walletIds.mapNotNull { walletId -> walletRegistration(walletId) }

        try {
            val results = runCatching { registerWallets(registrations) }
                .onFailure { L.e(it) }
                .getOrNull()
                ?: return@withContext false

            var rejected = 0
            for (registration in registrations) {
                val result = results[registration.walletId]
                if (result?.status != WalletRegisterResult.Status.ok) {
                    L.e("Wallet binding failed (walletId=${registration.walletId}, error=${result?.error})")
                    rejected++
                }
            }

            if (rejected > 0) {
                AnalyticsHelper.Default.captureException(
                    AnalyticException.WalletAuth("wallet bindings rejected (count=$rejected)")
                )
            }

            registrations.size == walletIds.size && rejected == 0
        } finally {
            registrations.forEach { it.keyPair.privateKey.fill(0) }
        }
    }

    private suspend fun unregisterWalletBindings(
        walletIds: List<String>,
    ): Boolean = withContext(Dispatchers.IO) {
        if (walletIds.isEmpty()) {
            return@withContext true
        }

        try {
            deviceRepository.requireDeviceId()
            for (batch in walletIds.chunked(BINDINGS_BATCH_LIMIT)) {
                api.multichain.auth.unregisterWallets(
                    UnregisterWalletsRequest(walletIds = batch)
                )
            }
            true
        } catch (e: Throwable) {
            L.e(e)
            false
        }
    }

    private suspend fun walletRegistration(walletId: String): WalletRegistration? {
        val accounts = accountDao.getAccountsByWalletId(walletId)
        if (accounts.isEmpty()) {
            return null
        }

        val keyPair = try {
            getWalletAppKeyPair(walletId)
        } catch (e: Throwable) {
            L.e(e)
            AnalyticsHelper.Default.captureException(
                AnalyticException.WalletAuth("wallet registration skipped, app key unavailable", e)
            )
            return null
        }

        return WalletRegistration(walletId, keyPair, accounts)
    }

    private fun AccountEntity.toProofAccount(): Account {
        return Account.watch(
            address = Address.force(displayAddress, chain, addressType),
            asset = chain.toAsset(),
        )
    }

    private fun Address.Type.toApiAccountType(): String? = when (this) {
        Address.Type.Default -> null
        else -> id
    }

    private fun String?.toAddressType(): Address.Type {
        return Address.Type.entries.firstOrNull { it.id == this }
            ?: Address.Type.Default
    }

    override suspend fun getAccounts(chain: Chain): List<AccountEntity> {
        return accountDao.getAccounts(chain)
    }

    override suspend fun findAccount(
        walletId: String,
        assetId: String,
        currencyOverride: String?,
        forceRefresh: Boolean,
    ): AccountWithDetails? = withContext(Dispatchers.IO) {
        val currencyCode = currencyOverride ?: settings.currency.code
        val cacheKey = accountCacheKey(walletId, assetId, currencyCode)
        val cached = _accounts[cacheKey]
        if (cached != null && !forceRefresh) {
            return@withContext cached
        }
        try {
            val item = api.multichain.wallets.getWalletAsset(
                walletId,
                assetId,
                currencies = listOf(currencyCode),
            )
            val asset = item.asset.toAssetEntity()
            val wallet = walletDao.getWallet(walletId)
            val account = resolvePrimaryAccount(
                accounts = accountDao.getAccountsByWalletId(walletId),
                chain = asset.value.chain,
                tonWalletType = wallet?.tonWalletType,
            ) ?: run {
                _accounts.remove(cacheKey)
                return@withContext null
            }

            AccountWithDetails(
                data = account,
                balance = AccountBalanceEntity(available = item.balance),
                asset = asset,
                rate = item.price.toAssetRateEntity(currencyCode),
                isHidden = item.isHidden,
            ).also { _accounts[cacheKey] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cached ?: throw e
        }
    }

    private fun accountCacheKey(walletId: String, assetId: String, currency: String): String =
        "$walletId|$assetId|$currency"

    override suspend fun fetchAccounts(
        walletId: String,
        currency: String,
        availableOnly: Boolean,
        showHidden: Boolean,
        showAll: Boolean,
        query: String?,
        network: String?,
        cursor: String?,
        limit: Int?,
        capabilities: List<AssetCapability>?,
        verifiedOnly: Boolean,
        hideDust: Boolean,
    ) = withContext(Dispatchers.IO) {
        val response = withRetry {
            api.multichain.wallets.getWalletAssets(
                walletId = walletId,
                currencies = listOf(currency),
                search = query,
                chain = network?.let { io.walletapi.models.Chain.valueOf(network) },
                cursor = cursor,
                limit = limit,
                availableOnly = availableOnly,
                showHidden = showHidden,
                showAll = showAll,
                verifiedOnly = verifiedOnly,
                hideDust = hideDust,
                capabilities = capabilities?.map { it.toApi() },
                xWalletId = walletId,
            )
        } ?: throw Exception("API error")

        if (cursor == null) {
            saveWalletAssetsResponseCache(
                walletId = walletId,
                currency = currency,
                availableOnly = availableOnly,
                showHidden = showHidden,
                showAll = showAll,
                query = query,
                network = network,
                capabilities = capabilities,
                verifiedOnly = verifiedOnly,
                hideDust = hideDust,
                response = response,
            )
        }

        mapWalletAssetsResponse(
            walletId = walletId,
            currency = currency,
            response = response,
        )
    }

    override suspend fun getCachedAccounts(
        walletId: String,
        currency: String,
        availableOnly: Boolean,
        showHidden: Boolean,
        showAll: Boolean,
        query: String?,
        network: String?,
        capabilities: List<AssetCapability>?,
        verifiedOnly: Boolean,
        hideDust: Boolean,
    ): AccountsWithTotal? = withContext(Dispatchers.IO) {
        val cacheKey = walletAssetsCacheKey(
            walletId = walletId,
            currency = currency,
            availableOnly = availableOnly,
            showHidden = showHidden,
            showAll = showAll,
            query = query,
            network = network,
            capabilities = capabilities,
            verifiedOnly = verifiedOnly,
            hideDust = hideDust,
        )
        val json = jsonCacheRepository.getJson(
            scope = JsonResponseCacheScope.WALLET_ASSETS,
            cacheKey = cacheKey,
            walletId = walletId,
        ) ?: return@withContext null
        val response = JsonResponseCache.decode<GetWalletAssets200Response>(json)
            ?: return@withContext null
        mapWalletAssetsResponse(
            walletId = walletId,
            currency = currency,
            response = response,
        )
    }

    private suspend fun saveWalletAssetsResponseCache(
        walletId: String,
        currency: String,
        availableOnly: Boolean,
        showHidden: Boolean,
        showAll: Boolean,
        query: String?,
        network: String?,
        capabilities: List<AssetCapability>?,
        verifiedOnly: Boolean,
        hideDust: Boolean,
        response: GetWalletAssets200Response,
    ) {
        jsonCacheRepository.save(
            scope = JsonResponseCacheScope.WALLET_ASSETS,
            cacheKey = walletAssetsCacheKey(
                walletId = walletId,
                currency = currency,
                availableOnly = availableOnly,
                showHidden = showHidden,
                showAll = showAll,
                query = query,
                network = network,
                capabilities = capabilities,
                verifiedOnly = verifiedOnly,
                hideDust = hideDust,
            ),
            json = JsonResponseCache.encode(response),
            walletId = walletId,
        )
    }

    private suspend fun mapWalletAssetsResponse(
        walletId: String,
        currency: String,
        response: GetWalletAssets200Response,
    ): AccountsWithTotal {
        val wallet = walletDao.getWallet(walletId)
        val walletAccounts = accountDao.getAccountsByWalletId(walletId)

        // The backend returns one balance row per registered account, so a multichain wallet that
        // registers both TON contract variants (v4r2 + v5r1) receives every TON asset twice — once
        // per variant, each with its own balance and the same asset id. Match each row to its local
        // account by sub-type (item.type -> addressType; matching on address is unreliable because we
        // register TON addresses user-friendly but the backend echoes them raw), then drop TON rows
        // that aren't held on the variant the user picked as primary. This dedupes the list (one item
        // per asset id) and keeps the balance for the account actually used.
        val primaryTonAccountId = resolvePrimaryAccount(
            accounts = walletAccounts,
            chain = Chain.Ton.Mainnet,
            tonWalletType = wallet?.tonWalletType,
        )?.id

        val accounts = response.assets.mapNotNull { item ->
            val asset = item.asset.toAssetEntity()
            val chain = asset.value.chain
            val addressType = item.type.toAddressType()
            val account = walletAccounts.firstOrNull { it.chain == chain && it.addressType == addressType }
                ?: walletAccounts.firstOrNull { it.chain == chain }
                ?: return@mapNotNull null
            if (account.chain is Chain.Ton && account.id != primaryTonAccountId) {
                return@mapNotNull null
            }

            AccountWithDetails(
                data = account,
                balance = AccountBalanceEntity(available = item.balance),
                asset = asset,
                rate = item.price.toAssetRateEntity(currency),
                isHidden = item.isHidden,
            )
        }

        accounts.forEach {
            _accounts[accountCacheKey(walletId, it.asset.id, currency)] = it
        }

        return AccountsWithTotal(
            accounts = accounts,
            total = BigDecimal.parseString(response.fiatPrice[currency] ?: "0"),
            nextCursor = response.nextCursor.takeIf { it.isNotBlank() },
        )
    }


    override suspend fun getCoinAccounts(walletId: String) = withContext(Dispatchers.IO) {
        val wallet = walletDao.getWallet(walletId)
        val walletAccounts = accountDao.getAccountsByWalletId(walletId)

        // A multichain wallet registers both TON contract variants (v4r2 + v5r1) as separate
        // account rows that share the same asset id. Keep only the variant the user picked as
        // primary so the list has a single account per asset id (callers key UI by asset.id).
        val primaryTonAccountId = resolvePrimaryAccount(
            accounts = walletAccounts,
            chain = Chain.Ton.Mainnet,
            tonWalletType = wallet?.tonWalletType,
        )?.id

        walletAccounts.filterNot { account ->
            account.chain is Chain.Ton && account.id != primaryTonAccountId
        }
    }

    override suspend fun setAssetVisibility(
        walletId: String,
        assetId: String,
        visible: Boolean,
    ) = setAssetsVisibility(walletId, mapOf(assetId to visible))

    override suspend fun setAssetsVisibility(
        walletId: String,
        visibilityByAssetId: Map<String, Boolean>,
    ) = withContext(Dispatchers.IO) {
        if (visibilityByAssetId.isEmpty()) {
            return@withContext Unit
        }
        val changes = visibilityByAssetId.map { (assetId, visible) ->
            val action = if (visible) {
                SaveWalletAssetsFiltersRequestChangesInner.Action.show
            } else {
                SaveWalletAssetsFiltersRequestChangesInner.Action.hide
            }
            SaveWalletAssetsFiltersRequestChangesInner(assetId = assetId, action = action)
        }
        api.multichain.wallets.saveWalletAssetsFilters(
            walletId,
            SaveWalletAssetsFiltersRequest(changes = changes),
            xWalletId = walletId,
        )
        _refreshTrigger.tryEmit(Unit)
        Unit
    }

    private suspend fun createAccountsForWallet(
        wallet: McWalletEntity,
        cryptoWallet: CryptoWallet,
    ): List<AccountEntity> = withContext(Dispatchers.IO) {
        accountEntities(wallet.id, cryptoWallet, mainnetChains)
    }

    // TON and BTC each register two wallet variants per chain; every other chain has a single
    // default account. Each variant becomes its own AccountEntity carrying its addressType, and the
    // address is derived per type via CryptoWallet.getAddress(chain, type).
    private fun accountEntities(
        walletId: String,
        cryptoWallet: CryptoWallet,
        chains: Collection<Chain>,
    ): List<AccountEntity> = chains.flatMap { chain ->
        val pubKey = cryptoWallet.getPublicKey(chain)
        addressTypes(chain).map { type ->
            AccountEntity(
                walletId = walletId,
                network = chain.network.type.id,
                mode = chain.network.mode.id,
                displayAddress = cryptoWallet.getAddress(chain, type).display,
                publicKey = pubKey.defaultHex,
                segwitPublicKey = pubKey.segWit,
                addressType = type,
            )
        }
    }

    private fun addressTypes(chain: Chain): List<Address.Type> = when (chain) {
        is Chain.Ton -> listOf(Address.Type.TonV4R2, Address.Type.TonV5R1)
        is Chain.Bitcoin -> listOf(Address.Type.BtcSegwit) // Address.Type.BtcSTaproot
        else -> listOf(Address.Type.Default)
    }
}
