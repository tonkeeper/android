package com.tonapps.wallet.data.multichain.account

import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.Wallet
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.blockchain.ton.extensions.publicKeyFromHex
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.chainkit.core.chain.model.account.WalletKeyPair
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.api.AuthorizationProvider
import com.tonapps.wallet.api.entity.Authorization
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.device.SecureDeviceRepository
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

interface UnifiedAccountRepository : AuthorizationProvider {

    val selectedTonWalletFlow: Flow<WalletEntity?>

    suspend fun getTonWallets(): List<WalletEntity>

    suspend fun getWalletLabels(): List<String>

    suspend fun getWalletsCount(): Int

    fun getSelectedWalletId(): String?

    suspend fun getSelectedWallet(): WalletEntity?

    suspend fun getTonWalletById(id: String): WalletEntity?

    suspend fun getTonWalletByAccountId(
        accountId: String,
        network: TonNetwork = TonNetwork.MAINNET,
    ): WalletEntity?

    suspend fun getMultichainTonAccount(walletId: String): AccountEntity?

    suspend fun getAccountAddress(wallet: WalletEntity, chain: Chain): Address?

    suspend fun buildAuth(wallet: WalletEntity?): Authorization

    suspend fun deleteWallet(id: String)

    suspend fun deleteAllWallets()

    suspend fun hasAnyWallet(): Boolean
}

class UnifiedAccountRepositoryImpl(
    private val accountRepository: AccountRepository,
    private val mcAccountRepository: McAccountRepository,
    private val chainKitProvider: ChainKitProvider,
    private val deviceRepository: SecureDeviceRepository,
) : UnifiedAccountRepository {

    // Combined with the MC store's write signal: at cold start the selection can
    // resolve to the legacy placeholder before the MC rows are readable, and the
    // legacy state alone never re-emits.
    @OptIn(ExperimentalCoroutinesApi::class)
    override val selectedTonWalletFlow: Flow<WalletEntity?> = combine(
        accountRepository.selectedStateFlow
            .filter { it !is AccountRepository.SelectedState.Initialization },
        mcAccountRepository.refreshTrigger,
    ) { state, _ -> state }
        .mapLatest { state ->
            when (state) {
                is AccountRepository.SelectedState.Wallet -> resolveSelected(state.wallet)
                is AccountRepository.SelectedState.Empty -> null
                is AccountRepository.SelectedState.Initialization -> null
            }
        }
        .distinctUntilChanged()
        .flowOn(Async.Io)

    override suspend fun getTonWallets(): List<WalletEntity> {
        return withContext(Async.Io) {
            val legacy = accountRepository.getWallets()
            val mc = mcAccountRepository.getWallets().mapNotNull { toTonWalletEntity(it) }
            legacy + mc
        }
    }

    override suspend fun getWalletLabels(): List<String> = withContext(Async.Io) {
        accountRepository.getWallets().map { it.label.name } +
                mcAccountRepository.getWallets().map { it.name }
    }

    override suspend fun getWalletsCount(): Int = withContext(Async.Io) {
        accountRepository.getWalletsCount() + mcAccountRepository.getWalletsCount()
    }

    override fun getSelectedWalletId(): String? {
        return accountRepository.getSelectedWalletId()
    }

    override suspend fun getSelectedWallet(): WalletEntity? {
        return withContext(Async.Io) {
            accountRepository.getSelectedWallet()?.let { return@withContext resolveSelected(it) }
            val id = accountRepository.getSelectedWalletId() ?: return@withContext null
            mcAccountRepository.getWallet(id)?.let { toTonWalletEntity(it) }
        }
    }

    override suspend fun getTonWalletById(id: String): WalletEntity? {
        return withContext(Async.Io) {
            accountRepository.getWalletById(id) ?: mcAccountRepository.getWallet(id)?.let {
                toTonWalletEntity(it)
            }
        }
    }

    override suspend fun getMultichainTonAccount(walletId: String): AccountEntity? =
        mcAccountRepository.getTonAccount(walletId)

    override suspend fun getAccountAddress(wallet: WalletEntity, chain: Chain): Address? {
        return withContext(Async.Io) {
            if (wallet.isMultichain) {
                return@withContext mcAccountRepository.getCoinAccounts(wallet.id) // TODO optimize
                    .firstOrNull { it.chain == chain }
                    ?.chainAccount
                    ?.address
            }

            val display = when (chain.network.type) {
                Network.Type.Ton -> wallet.address
                Network.Type.Tron -> accountRepository.getTronAddress(wallet.id)
                else -> null
            } ?: return@withContext null

            Address.from(display, chain)
        }
    }

    override suspend fun getAuthBy(walletId: String?): Authorization = withContext(Async.Io) {
        buildAuth(walletId?.let { getTonWalletById(it) })
    }

    override suspend fun getWalletKeyPair(walletId: String): WalletKeyPair? = withContext(Async.Io) {
        mcAccountRepository.getWalletKeyPair(walletId)
    }

    override suspend fun buildAuth(wallet: WalletEntity?): Authorization {
        if (wallet == null) {
            return Authorization.Empty
        }
        if (wallet.type == WalletType.Multichain) {
            return Authorization(
                walletId = wallet.id,
                deviceToken = deviceRepository.loadAccessToken(),
            )
        }

        return Authorization(tonProof = accountRepository.requestTonProofToken(wallet))
    }

    override suspend fun getTonWalletByAccountId(
        accountId: String,
        network: TonNetwork,
    ): WalletEntity? {
        return withContext(Async.Io) {
            accountRepository.getWalletByAccountId(accountId, network)
                ?.let { return@withContext it }

            if (network != TonNetwork.MAINNET) {
                return@withContext null
            }

            for (mc in mcAccountRepository.getWallets()) {
                val entity = toTonWalletEntity(mc) ?: continue
                if (entity.accountId.equalsAddress(accountId)) {
                    return@withContext entity
                }
            }

            null
        }
    }

    override suspend fun deleteWallet(id: String) {
        withContext(Async.Io) {
            val selectedId = accountRepository.getSelectedWalletId()

            if (mcAccountRepository.getWallet(id) != null) {
                mcAccountRepository.deleteWallet(id)
            } else {
                val legacy = accountRepository.getWalletById(id) ?: return@withContext
                accountRepository.deleteAccount(legacy.id)
            }

            if (selectedId == null || selectedId == id) {
                val nextId = mcAccountRepository.getWallets().firstOrNull()?.id
                    ?: accountRepository.getWallets().firstOrNull()?.id

                accountRepository.setSelectedWallet(nextId)
            }

            mcAccountRepository.refresh()
        }
    }

    override suspend fun deleteAllWallets() {
        withContext(Async.Io) {
            mcAccountRepository.deleteAllWallets()
            accountRepository.logout()
            mcAccountRepository.refresh()
        }
    }

    override suspend fun hasAnyWallet(): Boolean {
        return withContext(Async.Io) {
            accountRepository.getWalletsCount() > 0 || mcAccountRepository.getWalletsCount() > 0
        }
    }

    private suspend fun resolveSelected(legacy: WalletEntity): WalletEntity {
        if (!legacy.isMcPlaceholder()) {
            return legacy
        }

        return mcAccountRepository.getWallet(legacy.id)
            ?.let { toTonWalletEntity(it) }
            ?: legacy
    }

    private fun WalletEntity.isMcPlaceholder(): Boolean {
        return publicKey == EmptyPrivateKeyEd25519.publicKey()
    }

    private suspend fun toTonWalletEntity(mc: McWalletEntity): WalletEntity? {
        val account = mcAccountRepository.getTonAccount(mc.id) ?: return null
        val hex = account.publicKey.removePrefix("0x")
        val publicKey = hex.publicKeyFromHex()
    // TON Connect ton_addr must send a state init that hashes to addressOverride (the TW Core
    // address MC actually holds; org.ton's V5R1 differs, so `contract` would send the wrong one).
    // For V5R1 the address is hash(buildV5R1StateInit), so build the matching state init the same
        // way. V4R2 has no TW Core builder here → leave null and fall back to the V4R2 `contract`.
        val stateInitOverride = if (mc.tonWalletType == Address.Type.TonV4R2) {
            null
        } else {
            chainKitProvider.buildTonV5R1StateInitBase64(account.publicKey)
        }
        return WalletEntity(
            id = mc.id,
            publicKey = publicKey,
            type = WalletType.Multichain,
            version = when (mc.tonWalletType) {
                Address.Type.TonV4R2 -> WalletVersion.V4R2
                else -> WalletVersion.V5R1
            },
            label = Wallet.Label(mc.name, mc.emoji, mc.color),
            initialized = true,
            addressOverride = account.displayAddress,
            stateInitOverride = stateInitOverride,
        )
    }
}
