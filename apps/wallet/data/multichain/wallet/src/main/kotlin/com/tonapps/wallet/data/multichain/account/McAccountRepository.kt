package com.tonapps.wallet.data.multichain.account

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.tonapps.async.Async
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.tracer.AnalyticException
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.chainkit.core.chain.model.account.LedgerWallet
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.chainkit.core.chain.model.account.WalletKeyPair
import com.tonapps.chainkit.core.common.getOrElse
import com.tonapps.chainkit.core.secure.mnemonic.EntropySize
import com.tonapps.chainkit.core.secure.mnemonic.Mnemonic
import com.tonapps.extensions.lazyUnsafe
import com.tonapps.log.L
import com.tonapps.security.multichain.MnemonicCoder
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.withRetry
import com.tonapps.wallet.data.multichain.wallet.CredentialDao
import com.tonapps.wallet.data.multichain.wallet.CredentialEntity
import com.tonapps.wallet.data.multichain.wallet.CredentialIds
import com.tonapps.wallet.data.multichain.wallet.McBackupSource
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletType
import com.tonapps.wallet.data.multichain.wallet.WalletBundleDao
import com.tonapps.wallet.data.multichain.wallet.WalletDao
import com.tonapps.wallet.data.cache.JsonResponseCache
import com.tonapps.wallet.data.cache.JsonCacheRepository
import com.tonapps.wallet.data.multichain.device.SecureDeviceRepository
import com.tonapps.wallet.data.multichain.hardware.HardwareAccountDraft
import com.tonapps.wallet.data.multichain.hardware.HardwareWalletDescriptor
import com.tonapps.wallet.data.multichain.lighter.PerpetualRepository
import com.tonapps.wallet.data.multichain.vault.WalletKeyRepository
import com.tonapps.wallet.data.cache.JsonResponseCacheScope
import com.tonapps.wallet.data.earn.EarnRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import io.walletapi.models.Asset
import io.walletapi.models.GetDeviceBindingsRequest
import io.walletapi.models.GetWalletAssets200Response
import io.walletapi.models.RegisterWalletsRequest
import io.walletapi.models.SaveWalletAssetsFiltersRequest
import io.walletapi.models.SyncStatus
import io.walletapi.models.SaveWalletAssetsFiltersRequestChangesInner
import io.walletapi.models.UnregisterWalletsRequest
import io.walletapi.models.WalletAccount
import io.walletapi.models.WalletPerps
import io.walletapi.models.WalletRegisterItem
import io.walletapi.models.WalletRegisterResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private const val SYNC_POLL_INTERVAL_MS = 3_000L
private const val SYNC_MAX_ATTEMPTS = 10

private const val BINDINGS_BATCH_LIMIT = 32

private const val DISK_SNAPSHOT_REQUEST_NUMBER = 0L

const val WALLET_ASSETS_PAGE_SIZE = 25

class WalletRegistrationException(cause: Throwable) : Exception(cause)

interface McAccountRepository {

    val refreshTrigger: SharedFlow<Unit>

    val walletsBoundTrigger: SharedFlow<Unit>

    fun refresh()

    suspend fun getAccounts(chain: Chain): List<AccountEntity>

    suspend fun findAccount(
        walletId: String,
        assetId: String,
        currencyOverride: String? = null,
        forceRefresh: Boolean = false,
    ): AccountWithDetails?

    suspend fun fetchAccount(
        walletId: String,
        assetId: String,
        currencyOverride: String? = null,
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

    suspend fun createHardwareWallet(
        descriptor: HardwareWalletDescriptor,
        accounts: List<HardwareAccountDraft>,
        walletSeed: ByteArray,
        mnemonicCoder: MnemonicCoder,
        name: String,
        emoji: CharSequence,
        color: Int,
    ): McWalletEntity

    suspend fun addHardwareAccounts(
        walletId: String,
        descriptor: HardwareWalletDescriptor,
        accounts: List<HardwareAccountDraft>,
    )

    suspend fun getHardwareDescriptor(walletId: String): HardwareWalletDescriptor?

    suspend fun updateHardwareDescriptor(walletId: String, descriptor: HardwareWalletDescriptor)

    suspend fun findWalletIdsByAddress(chain: Chain, address: String): List<String>

    suspend fun hasHardwareWalletsWithoutReadableAppKey(): Boolean

    suspend fun restoreHardwareAppKeys(mnemonicCoder: MnemonicCoder)

    suspend fun getCryptoWallet(walletId: String, mnemonicCoder: MnemonicCoder): MnemonicWallet?

    suspend fun getWalletKeyPair(walletId: String): WalletKeyPair?

    suspend fun getMnemonic(walletId: String, mnemonicCoder: MnemonicCoder): Array<String>?

    suspend fun hasMissingChains(): Boolean

    /** Mainnet chains at least one wallet has no account on yet, in [Chain.displayOrder]. */
    suspend fun missingChains(): List<Chain>

    /**
     * Registers this device, in one request, for the wallets it misses chains of (with
     * [mnemonicCoder] only) and the ones the backend doesn't bind to it; [firstWalletId] goes first.
     * Throws when the bindings could not be read, after registering what it could.
     */
    suspend fun syncWallets(mnemonicCoder: MnemonicCoder?, firstWalletId: String?)

    /**
     * QA tool: re-registers [walletId] without [chains] and deletes its accounts on them, so they
     * count as missing again and the next unlock adds them back through the new networks screen.
     */
    suspend fun removeChains(walletId: String, chains: Collection<Chain>)

    suspend fun getWallet(walletId: String): McWalletEntity?

    suspend fun walletExists(mnemonic: Mnemonic): Boolean

    suspend fun getWalletSyncStatus(walletId: String): SyncStatus

    suspend fun awaitWalletSync(walletId: String)

    suspend fun getWallets(): List<McWalletEntity>

    suspend fun getWalletsCount(): Int

    suspend fun getTonAccount(walletId: String): AccountEntity?

    suspend fun deleteWallet(walletId: String)

    suspend fun updateWalletLabel(walletId: String, name: String, emoji: String, color: Int)

    suspend fun updateBackup(walletId: String, backupType: McBackupSource?, backupTime: Long?)

    suspend fun fetchAccounts(
        walletId: String,
        currency: String? = null,
        availableOnly: Boolean = false,
        showHidden: Boolean = false,
        showAll: Boolean = false,
        query: String? = null,
        network: String? = null,
        cursor: String? = null,
        limit: Int? = null,
        capabilities: List<AssetCapability>? = null,
        verifiedOnly: Boolean? = null,
        hideDust: Boolean? = null,
    ): AccountsWithTotal

    suspend fun fetchWalletTotals(walletId: String, currency: String): WalletTotals

    fun observeCachedAccounts(query: WalletAssetsQuery): Flow<List<AccountWithDetails>>

    suspend fun getCachedAccounts(
        walletId: String,
        currency: String? = null,
        availableOnly: Boolean = false,
        showHidden: Boolean = false,
        showAll: Boolean = false,
        query: String? = null,
        network: String? = null,
        capabilities: List<AssetCapability>? = null,
        verifiedOnly: Boolean? = null,
        hideDust: Boolean? = null,
        preferFetchedAccounts: Boolean = false,
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
    private val perpetualRepository: PerpetualRepository,
    private val earnRepository: EarnRepository,
) : McAccountRepository {

    private val _refreshTrigger = MutableSharedFlow<Unit>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val refreshTrigger: SharedFlow<Unit> = _refreshTrigger.asSharedFlow()

    private val _walletsBoundTrigger = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val walletsBoundTrigger: SharedFlow<Unit> = _walletsBoundTrigger.asSharedFlow()

    init {
        _refreshTrigger.tryEmit(Unit)
    }

    private val mainnetChains: Set<Chain> by lazyUnsafe {
        Chain.all
            .filter { it.network.mode == Network.Mode.Mainnet }
            .toSet()
    }

    private var _accounts = ConcurrentHashMap<String, CachedAccount>()
    private val requestCounter = AtomicLong()

    private val bindingsMutex = Mutex()

    override fun refresh() {
        _refreshTrigger.tryEmit(Unit)
    }

    override fun createMnemonic(): Mnemonic {
        return Mnemonic.create(EntropySize.B128)
    }

    // Materialize the mnemonic words only inside this call and zero the array afterwards (the joined
    // String passed to HDWallet is immutable and cannot be zeroed — confined here and short-lived).
    private fun cryptoWallet(mnemonic: Mnemonic): MnemonicWallet {
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

        val keyPair = cryptoWallet.walletKeyPair()
        try {
            bindingsMutex.withLock {
                registerWallet(WalletRegistration(wallet.id, keyPair, accounts, isHardware = false))

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
        // Kept from the start: a deposit names it before the next passcode entry would keep it.
        try {
            perpetualRepository.saveTradingKey(wallet.id, cryptoWallet)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e(e)
        }

        wallet
    }

    override suspend fun createHardwareWallet(
        descriptor: HardwareWalletDescriptor,
        accounts: List<HardwareAccountDraft>,
        walletSeed: ByteArray,
        mnemonicCoder: MnemonicCoder,
        name: String,
        emoji: CharSequence,
        color: Int,
    ): McWalletEntity = withContext(Dispatchers.IO) {
        try {
            require(accounts.any { it.chain is Chain.Ton }) { "A hardware wallet needs a TON account" }
            val ledgerWallet = LedgerWallet.fromSeed(walletSeed).getOrElse { throw it }
            storeHardwareWallet(descriptor, accounts, ledgerWallet.walletKeyPair(), walletSeed, mnemonicCoder, name, emoji, color)
        } finally {
            walletSeed.fill(0)
        }
    }

    private suspend fun storeHardwareWallet(
        descriptor: HardwareWalletDescriptor,
        accounts: List<HardwareAccountDraft>,
        keyPair: WalletKeyPair,
        walletSeed: ByteArray,
        mnemonicCoder: MnemonicCoder,
        name: String,
        emoji: CharSequence,
        color: Int,
    ): McWalletEntity {
        try {
            val walletId = keyPair.walletIdV2()
            val descriptorCredential = CredentialEntity(
                data = descriptor.encode(),
                id = CredentialIds.hardwareDescriptor(walletId),
            )
            val recoveryCredential = CredentialEntity(
                data = mnemonicCoder.encryptBytes(walletSeed),
                id = CredentialIds.hardwareAppKey(walletId),
            )
            val wallet = McWalletEntity(
                id = walletId,
                credentialId = descriptorCredential.id,
                name = name,
                emoji = emoji.toString(),
                color = color,
                type = McWalletType.Multicoin,
                tonWalletType = Address.Type.TonV4R2,
            )
            val entities = accounts.map { it.toEntity(walletId) }
            bindingsMutex.withLock {
                registerWallet(WalletRegistration(walletId, keyPair, entities, isHardware = true))
                val sessionCredential = appKeyRepository.sealedEntity(walletId, keyPair.privateKey.copyOf())
                    ?: throw IllegalStateException("Session is locked, the hardware wallet app key cannot be stored")
                walletBundleDao.insertNewHardwareWallet(
                    credentials = listOf(descriptorCredential, recoveryCredential, sessionCredential),
                    wallet = wallet,
                    accounts = entities,
                )
                _refreshTrigger.tryEmit(Unit)
            }
            return wallet
        } finally {
            keyPair.privateKey.fill(0)
        }
    }

    override suspend fun addHardwareAccounts(
        walletId: String,
        descriptor: HardwareWalletDescriptor,
        accounts: List<HardwareAccountDraft>,
    ) = withContext(Dispatchers.IO) {
        val wallet = walletDao.getWallet(walletId)?.takeIf { it.isHardware }
            ?: throw IllegalStateException("Hardware wallet not found (walletId=$walletId)")
        bindingsMutex.withLock {
            val existing = accountDao.getAccountsByWalletId(walletId)
            val added = accounts
                .filterNot { draft -> existing.any { it.chain == draft.chain && it.addressType == draft.addressType } }
                .map { it.toEntity(walletId) }
            val keyPair = getWalletAppKeyPair(walletId)
            try {
                registerWallet(WalletRegistration(walletId, keyPair, existing + added, isHardware = true))
            } finally {
                keyPair.privateKey.fill(0)
            }
            credentialDao.insertCredential(CredentialEntity(data = descriptor.encode(), id = wallet.credentialId))
            accountDao.insertAccounts(added)
            _refreshTrigger.tryEmit(Unit)
        }
        Unit
    }

    override suspend fun getHardwareDescriptor(walletId: String): HardwareWalletDescriptor? = withContext(Dispatchers.IO) {
        val wallet = walletDao.getWallet(walletId)?.takeIf { it.isHardware } ?: return@withContext null
        val data = credentialDao.getData(wallet.credentialId) ?: return@withContext null
        try {
            HardwareWalletDescriptor.decode(data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e(e)
            null
        }
    }

    override suspend fun updateHardwareDescriptor(
        walletId: String,
        descriptor: HardwareWalletDescriptor,
    ) = withContext(Dispatchers.IO) {
        val wallet = walletDao.getWallet(walletId)?.takeIf { it.isHardware } ?: return@withContext
        credentialDao.insertCredential(CredentialEntity(data = descriptor.encode(), id = wallet.credentialId))
        _refreshTrigger.tryEmit(Unit)
        Unit
    }

    override suspend fun findWalletIdsByAddress(chain: Chain, address: String): List<String> = withContext(Dispatchers.IO) {
        accountDao.getWalletIdsByAddress(chain.network.type.id, chain.network.mode.id, address)
    }

    override suspend fun hasHardwareWalletsWithoutReadableAppKey(): Boolean = withContext(Dispatchers.IO) {
        walletDao.getWallets().any { it.isHardware && !hasReadableAppKey(it.id) }
    }

    override suspend fun restoreHardwareAppKeys(mnemonicCoder: MnemonicCoder) = withContext(Dispatchers.IO) {
        for (wallet in walletDao.getWallets()) {
            if (!wallet.isHardware || hasReadableAppKey(wallet.id)) {
                continue
            }
            val sealed = credentialDao.getData(CredentialIds.hardwareAppKey(wallet.id)) ?: continue
            try {
                val walletSeed = mnemonicCoder.decryptBytes(sealed)
                val ledgerWallet = try {
                    LedgerWallet.fromSeed(walletSeed).getOrElse { throw it }
                } finally {
                    walletSeed.fill(0)
                }
                appKeyRepository.save(wallet.id, ledgerWallet.walletKeyPair().privateKey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                L.e(e)
            }
        }
    }

    private suspend fun hasReadableAppKey(walletId: String): Boolean {
        if (!appKeyRepository.hasAppKey(walletId)) {
            return false
        }
        val key = appKeyRepository.getAppPrivateKey(walletId) ?: return false
        key.fill(0)
        return true
    }

    private fun HardwareAccountDraft.toEntity(walletId: String): AccountEntity = AccountEntity(
        walletId = walletId,
        network = chain.network.type.id,
        mode = chain.network.mode.id,
        displayAddress = displayAddress,
        publicKey = publicKeyHex,
        segwitPublicKey = extendedPublicKey,
        addressType = addressType,
    )

    override suspend fun hasMissingChains(): Boolean = withContext(Dispatchers.IO) {
        val accounts = accountDao.getAllAccounts()
            .groupBy { it.walletId }

        walletDao.getWallets().any {
            !it.isHardware && missingChains(accounts[it.id].orEmpty()).isNotEmpty()
        }
    }

    override suspend fun missingChains(): List<Chain> = withContext(Dispatchers.IO) {
        val accounts = accountDao.getAllAccounts()
            .groupBy { it.walletId }

        val missing = walletDao.getWallets()
            .filterNot { it.isHardware }
            .flatMap { missingChains(accounts[it.id].orEmpty()) }
            .toSet()
        Chain.displayOrder.filter { it in missing }
    }

    // New accounts are stored only after the backend accepted them, so a failed registration leaves
    // the chains missing and the next unlock retries it.
    override suspend fun syncWallets(
        mnemonicCoder: MnemonicCoder?,
        firstWalletId: String?,
    ) = withContext(Dispatchers.IO) {
        bindingsMutex.withLock {
            val wallets = walletDao.getWallets()
                .sortedByDescending { it.id == firstWalletId }

            if (wallets.isEmpty()) {
                return@withLock
            }

            deviceRepository.requireDeviceId()

            val bindings = runCatching {
                api.multichain.auth.getDeviceBindings(
                    GetDeviceBindingsRequest(wallets = wallets.map { it.id }), F = settings.installId
                )
            }.onFailure { L.e(it) }

            val unknown = bindings.getOrNull()?.unknown.orEmpty().toSet()

            val accounts = accountDao.getAllAccounts().groupBy { it.walletId }
            val credentials = if (mnemonicCoder != null) {
                credentialDao.getCredentials(wallets.map { it.credentialId })
                    .associateBy { it.id }
            } else {
                emptyMap()
            }

            val syncs = ArrayList<WalletSync>()
            try {
                for (wallet in wallets) {
                    val existing = accounts[wallet.id].orEmpty()
                    val chainSync = mnemonicCoder?.takeUnless { wallet.isHardware }?.let { coder ->
                        chainSync(wallet.id, existing, credentials[wallet.credentialId], coder)
                    }
                    val sync = when {
                        chainSync != null -> chainSync
                        wallet.id in unknown -> bindingSync(wallet.id, existing)
                        else -> null
                    }
                    sync?.let(syncs::add)
                }

                register(syncs)
            } finally {
                syncs.forEach { it.registration.keyPair.privateKey.fill(0) }
            }

            val bindingsError = bindings.exceptionOrNull()
            if (bindingsError != null) {
                throw bindingsError
            }
        }
    }

    override suspend fun removeChains(
        walletId: String,
        chains: Collection<Chain>,
    ): Unit = withContext(Dispatchers.IO) {
        bindingsMutex.withLock {
            val remaining = accountDao.getAccountsByWalletId(walletId).filter { it.chain !in chains }
            val registration = walletRegistration(walletId, remaining)
                ?: throw IllegalStateException("No accounts or app key (walletId=$walletId)")
            try {
                registerWallet(registration)
            } finally {
                registration.keyPair.privateKey.fill(0)
            }
            chains.forEach { accountDao.deleteAccounts(walletId, it) }
            _refreshTrigger.tryEmit(Unit)
        }
    }

    private suspend fun register(syncs: List<WalletSync>) {
        if (syncs.isEmpty()) {
            return
        }
        val results = runCatching { registerWallets(syncs.map { it.registration }) }
            .onFailure { L.e(it) }
            .getOrNull()
            ?: return

        val accepted = syncs.filter { results[it.registration.walletId]?.status == WalletRegisterResult.Status.ok }
        val rejected = syncs.size - accepted.size
        if (rejected > 0) {
            AnalyticsHelper.Default.captureException(
                AnalyticException.WalletAuth("wallet registration rejected (count=$rejected)")
            )
        }
        if (accepted.isNotEmpty()) {
            settings.multichainPushSyncState = null
            _walletsBoundTrigger.tryEmit(Unit)
        }
        val added = accepted.flatMap { it.added }
        if (added.isNotEmpty()) {
            accountDao.insertAccounts(added)
            _refreshTrigger.tryEmit(Unit)
        }
    }

    private fun chainSync(
        walletId: String,
        existing: List<AccountEntity>,
        credential: CredentialEntity?,
        mnemonicCoder: MnemonicCoder,
    ): WalletSync? {
        val missing = missingChains(existing)
        if (missing.isEmpty() || credential == null) {
            return null
        }
        return try {
            val cryptoWallet = mnemonicCoder.decryptMnemonic(credential.data)
                .use { cryptoWallet(it) }
            val added = accountEntities(walletId, cryptoWallet, missing)
            WalletSync(
                registration = WalletRegistration(walletId, cryptoWallet.walletKeyPair(), existing + added, isHardware = false),
                added = added,
            )
        } catch (e: Throwable) {
            L.e(e)
            null
        }
    }

    private suspend fun bindingSync(walletId: String, existing: List<AccountEntity>): WalletSync? {
        return walletRegistration(walletId, existing)?.let { WalletSync(it, added = emptyList()) }
    }

    private fun missingChains(existing: List<AccountEntity>): List<Chain> {
        val assetIds = existing.map { it.chain.coinAssetId }.toSet()
        return mainnetChains.filter { it.coinAssetId !in assetIds }
    }


    override suspend fun getCryptoWallet(
        walletId: String,
        mnemonicCoder: MnemonicCoder,
    ): MnemonicWallet? {
        return withContext(Dispatchers.IO) {
            val wallet = walletDao.getWallet(walletId)?.takeUnless { it.isHardware } ?: return@withContext null
            val credential =
                credentialDao.getCredential(wallet.credentialId) ?: return@withContext null
            val cryptoWallet = mnemonicCoder.decryptMnemonic(credential.data)
                .use { cryptoWallet(it) }

            if (!appKeyRepository.hasAppKey(walletId)) {
                appKeyRepository.save(
                    walletId,
                    cryptoWallet.walletKeyPair().privateKey
                )
            }
            cryptoWallet
        }
    }

    override suspend fun getMnemonic(
        walletId: String,
        mnemonicCoder: MnemonicCoder,
    ): Array<String>? = withContext(Dispatchers.IO) {
        val w = walletDao.getWallet(walletId)?.takeUnless { it.isHardware } ?: return@withContext null
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

    private fun walletId(cryptoWallet: MnemonicWallet): String {
        return cryptoWallet.walletIdV2()
    }

    override suspend fun getWalletKeyPair(walletId: String): WalletKeyPair? {
        val appPrivateKey = appKeyRepository.getAppPrivateKey(walletId)
            ?: return null
        return WalletKeyRepository.keyPair(walletId, appPrivateKey)
    }

    private suspend fun getWalletAppKeyPair(walletId: String): WalletKeyPair {
        return getWalletKeyPair(walletId)
            ?: throw IllegalStateException("No app key available (walletId=$walletId)")
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
                    SyncStatus.Status.in_progress, SyncStatus.Status.unknown -> delay(SYNC_POLL_INTERVAL_MS)
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
            earnRepository.forgetWallet(walletId)
            jsonCacheRepository.deleteByWalletId(walletId)
            walletBundleDao.deleteWallet(
                walletId = walletId,
                credentialIds = listOf(wallet.credentialId) + CredentialIds.derivedIds(walletId),
            )
            perpetualRepository.forgetAccount(walletId)
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
        val isHardware: Boolean,
    )

    /** [added] are the accounts to store once [registration] is accepted. */
    private class WalletSync(
        val registration: WalletRegistration,
        val added: List<AccountEntity>,
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

            val signed = batch.mapNotNull { registration ->
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
                if (registration.isHardware && proof.walletId != registration.walletId) {
                    L.e(IllegalStateException("Register proof wallet id does not match (walletId=${registration.walletId})"))
                    return@mapNotNull null
                }

                registration to WalletRegisterItem(
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
            if (signed.isEmpty()) {
                return emptyMap()
            }

            val response = api.multichain.auth.registerWallets(
                RegisterWalletsRequest(challenge = challenge, wallets = signed.map { it.second })
            )

            return response.results.mapNotNull { result ->
                signed.getOrNull(result.index)?.let { it.first.walletId to result }
            }.toMap()
        } catch (e: Throwable) {
            L.e(e)
            throw WalletRegistrationException(e)
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

    private suspend fun walletRegistration(walletId: String, accounts: List<AccountEntity>): WalletRegistration? {
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

        return WalletRegistration(walletId, keyPair, accounts, isHardware = walletDao.getWallet(walletId)?.isHardware == true)
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
        val cached = _accounts[accountCacheKey(walletId, assetId, currencyCode)]?.value
        if (cached != null && !forceRefresh) {
            return@withContext cached
        }
        try {
            fetchAccount(walletId, assetId, currencyCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cached ?: throw e
        }
    }

    override suspend fun fetchAccount(
        walletId: String,
        assetId: String,
        currencyOverride: String?,
    ): AccountWithDetails? = withContext(Dispatchers.IO) {
        val currencyCode = currencyOverride ?: settings.currency.code
        val cacheKey = accountCacheKey(walletId, assetId, currencyCode)
        val requestNumber = requestCounter.incrementAndGet()
        val item = api.multichain.wallets.getWalletAsset(
            walletId,
            assetId,
            currencies = listOf(currencyCode),
        )
        val asset = item.asset.toAssetEntity()
        val chain = asset.valueOrNull?.chain ?: run {
            _accounts.remove(cacheKey)
            return@withContext null
        }
        val wallet = walletDao.getWallet(walletId)
        val account = resolvePrimaryAccount(
            accounts = accountDao.getAccountsByWalletId(walletId),
            chain = chain,
            tonWalletType = wallet?.tonWalletType,
        ) ?: run {
            _accounts.remove(cacheKey)
            return@withContext null
        }

        // A wallet may register more than one account on a chain — both TON contract variants, a second
        // Solana address — and this endpoint answers with one of them, not necessarily the primary one
        // the account stands for. The list carries a row per account, so the primary's balance is read
        // there, the way the asset list reads it; a primary missing from the list holds none of the asset.
        val balance = if (item.belongsTo(account)) {
            item.balance
        } else {
            fetchPrimaryBalance(walletId, assetId, currencyCode, account) ?: "0"
        }

        cacheAccount(
            cacheKey = cacheKey,
            account = AccountWithDetails(
                data = account,
                balance = AccountBalanceEntity(available = balance),
                asset = asset,
                rate = item.price.toAssetRateEntity(currencyCode),
                isHidden = item.isHidden,
            ),
            requestNumber = requestNumber,
        )
    }

    // The balance [account] holds of [assetId], or null when the list has no row of its.
    private fun fetchPrimaryBalance(
        walletId: String,
        assetId: String,
        currency: String,
        account: AccountEntity,
    ): String? {
        val response = api.multichain.wallets.getWalletAssets(
            walletId = walletId,
            currencies = listOf(currency),
            assetIds = listOf(assetId),
            showHidden = true,
            showAll = true,
            xWalletId = walletId,
        )
        val row = response.assets.firstOrNull { it.asset.assetId == assetId && it.belongsTo(account) }
        return row?.balance
    }

    // A row of the wallet's assets is [account]'s by its contract variant on TON, by its address elsewhere.
    // TODO: compare addresses as chainkit normalizes them (Address.force(…, chain)): a backend echoing a
    //  non-TON address in another form than the one registered would drop that chain's rows from the list.
    private fun Asset.belongsTo(account: AccountEntity): Boolean = when {
        account.chain !is Chain.Ton -> address.equals(account.displayAddress, ignoreCase = account.chain.isEvm)
        // Wallets from before the two variants have one untyped TON account: nothing to choose.
        account.addressType == Address.Type.Default -> true
        else -> type.toAddressType() == account.addressType
    }

    private class CachedAccount(
        val value: AccountWithDetails,
        val requestNumber: Long,
    )

    private fun accountCacheKey(walletId: String, assetId: String, currency: String): String =
        "$walletId|$assetId|$currency"

    private fun cacheAccount(cacheKey: String, account: AccountWithDetails, requestNumber: Long): AccountWithDetails {
        val entry = _accounts.merge(cacheKey, CachedAccount(account, requestNumber)) { current, candidate ->
            when {
                candidate.requestNumber > current.requestNumber -> candidate
                else -> current
            }
        }
        return entry?.value ?: account
    }

    override suspend fun fetchAccounts(
        walletId: String,
        currency: String?,
        availableOnly: Boolean,
        showHidden: Boolean,
        showAll: Boolean,
        query: String?,
        network: String?,
        cursor: String?,
        limit: Int?,
        capabilities: List<AssetCapability>?,
        verifiedOnly: Boolean?,
        hideDust: Boolean?,
    ) = withContext(Dispatchers.IO) {
        val filters = settings.walletAssetsFilters(walletId)
        val currencyCode = currency ?: filters.currency
        val verifiedOnlyFilter = verifiedOnly ?: filters.verifiedOnly
        val hideDustFilter = hideDust ?: filters.hideDust
        val requestNumber = requestCounter.incrementAndGet()
        val response = withRetry {
            api.multichain.wallets.getWalletAssets(
                walletId = walletId,
                currencies = listOf(currencyCode),
                search = query,
                chain = network?.let { io.walletapi.models.Chain.valueOf(network) },
                cursor = cursor,
                limit = limit,
                availableOnly = availableOnly,
                showHidden = showHidden,
                showAll = showAll,
                verifiedOnly = verifiedOnlyFilter,
                hideDust = hideDustFilter,
                capabilities = capabilities?.map { it.toApi() },
                xWalletId = walletId,
            )
        } ?: throw Exception("API error")

        val accounts = mapWalletAssetsResponse(
            walletId = walletId,
            currency = currencyCode,
            response = response,
            requestNumber = requestNumber,
        )

        if (cursor == null) {
            saveWalletAssetsResponseCache(
                walletId = walletId,
                currency = currencyCode,
                availableOnly = availableOnly,
                showHidden = showHidden,
                showAll = showAll,
                query = query,
                network = network,
                capabilities = capabilities,
                verifiedOnly = verifiedOnlyFilter,
                hideDust = hideDustFilter,
                response = response,
            )
        }

        accounts.copy(accounts = accounts.accounts.map { latestFetchedOr(walletId, currencyCode, it) })
    }

    override suspend fun fetchWalletTotals(walletId: String, currency: String) = withContext(Dispatchers.IO) {
        val filters = settings.walletAssetsFilters(walletId)
        val response = withRetry {
            api.multichain.wallets.getWalletAssets(
                walletId = walletId,
                currencies = listOf(currency),
                limit = 1,
                verifiedOnly = filters.verifiedOnly,
                hideDust = filters.hideDust,
                xWalletId = walletId,
            )
        } ?: throw Exception("API error")
        WalletTotals(
            total = response.totalIn(currency),
            perpsTotal = response.perps.toWalletPerpsTotal(currency),
        )
    }

    override suspend fun getCachedAccounts(
        walletId: String,
        currency: String?,
        availableOnly: Boolean,
        showHidden: Boolean,
        showAll: Boolean,
        query: String?,
        network: String?,
        capabilities: List<AssetCapability>?,
        verifiedOnly: Boolean?,
        hideDust: Boolean?,
        preferFetchedAccounts: Boolean,
    ): AccountsWithTotal? = withContext(Dispatchers.IO) {
        val filters = settings.walletAssetsFilters(walletId)
        val currencyCode = currency ?: filters.currency
        val cacheKey = walletAssetsCacheKey(
            walletId = walletId,
            currency = currencyCode,
            availableOnly = availableOnly,
            showHidden = showHidden,
            showAll = showAll,
            query = query,
            network = network,
            capabilities = capabilities,
            verifiedOnly = verifiedOnly ?: filters.verifiedOnly,
            hideDust = hideDust ?: filters.hideDust,
        )
        val json = jsonCacheRepository.getJson(
            scope = JsonResponseCacheScope.WALLET_ASSETS,
            cacheKey = cacheKey,
            walletId = walletId,
        ) ?: return@withContext null
        val response = JsonResponseCache.decode<GetWalletAssets200Response>(json)
            ?: return@withContext null
        val snapshot = mapWalletAssetsResponse(
            walletId = walletId,
            currency = currencyCode,
            response = response,
            requestNumber = DISK_SNAPSHOT_REQUEST_NUMBER,
        )
        when {
            preferFetchedAccounts -> snapshot.copy(
                accounts = snapshot.accounts.map { latestFetchedOr(walletId, currencyCode, it) },
            )
            else -> snapshot
        }
    }

    override fun observeCachedAccounts(query: WalletAssetsQuery): Flow<List<AccountWithDetails>> = jsonCacheRepository.observeJson(
        scope = JsonResponseCacheScope.WALLET_ASSETS,
        cacheKey = walletAssetsCacheKey(
            walletId = query.walletId,
            currency = query.currency,
            availableOnly = query.availableOnly,
            showHidden = query.showHidden,
            showAll = query.showAll,
            query = query.query,
            network = query.network,
            capabilities = query.capabilities,
            verifiedOnly = query.verifiedOnly,
            hideDust = query.hideDust,
        ),
        walletId = query.walletId,
    ).mapNotNull { json ->
        if (json == null) {
            return@mapNotNull emptyList()
        }
        try {
            val response = JsonResponseCache.decode<GetWalletAssets200Response>(json)
                ?: return@mapNotNull null
            mapWalletAssetsResponse(
                walletId = query.walletId,
                currency = query.currency,
                response = response,
                requestNumber = DISK_SNAPSHOT_REQUEST_NUMBER,
            ).accounts
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e)
            null
        }
    }.catch { e ->
        L.e(e)
    }.flowOn(Dispatchers.IO)

    private fun latestFetchedOr(
        walletId: String,
        currency: String,
        account: AccountWithDetails,
    ): AccountWithDetails {
        val fetched = _accounts[accountCacheKey(walletId, account.asset.id, currency)]
            ?.takeIf { it.requestNumber > DISK_SNAPSHOT_REQUEST_NUMBER }
        return fetched?.value ?: account
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
        requestNumber: Long,
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
            // Skip assets on chains this build doesn't know (e.g. a chain added in a newer app
            // version and registered for this wallet there) instead of failing the whole list.
            val chain = asset.valueOrNull?.chain ?: return@mapNotNull null
            val addressType = item.type.toAddressType()
            val account = walletAccounts.firstOrNull { it.chain == chain && it.addressType == addressType }
                ?: walletAccounts.firstOrNull { it.chain == chain }
                ?: return@mapNotNull null
            if (account.chain is Chain.Ton && account.id != primaryTonAccountId) {
                return@mapNotNull null
            }
            // Another address of the chain's, a second Solana one, is not the account the list shows.
            if (account.chain !is Chain.Ton && !item.belongsTo(account)) {
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
            cacheAccount(accountCacheKey(walletId, it.asset.id, currency), it, requestNumber)
        }

        return AccountsWithTotal(
            accounts = accounts,
            total = response.totalIn(currency),
            nextCursor = response.nextCursor.takeIf { it.isNotBlank() },
            perpsTotal = response.perps.toWalletPerpsTotal(currency),
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
        cryptoWallet: MnemonicWallet,
    ): List<AccountEntity> = withContext(Dispatchers.IO) {
        accountEntities(wallet.id, cryptoWallet, mainnetChains)
    }

    // TON and BTC each register two wallet variants per chain; every other chain has a single
    // default account. Each variant becomes its own AccountEntity carrying its addressType, and the
    // address is derived per type via MnemonicWallet.getAddress(chain, type).
    private fun accountEntities(
        walletId: String,
        cryptoWallet: MnemonicWallet,
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

private fun GetWalletAssets200Response.totalIn(currency: String): BigDecimal =
    BigDecimal.parseString(fiatPrice[currency] ?: "0")

private fun WalletPerps?.toWalletPerpsTotal(currency: String): WalletPerpsTotal = when (this?.status) {
    null, WalletPerps.Status.unavailable, WalletPerps.Status.unknown -> WalletPerpsTotal.Unavailable
    WalletPerps.Status.none -> WalletPerpsTotal.Empty
    WalletPerps.Status.ok -> fiatPrice?.get(currency)
        ?.let { WalletPerpsTotal.Amount(BigDecimal.parseString(it)) }
        ?: WalletPerpsTotal.Empty
}
