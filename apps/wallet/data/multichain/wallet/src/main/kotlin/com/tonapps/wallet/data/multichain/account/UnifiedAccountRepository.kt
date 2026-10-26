package com.tonapps.wallet.data.multichain.account

import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.chainkit.core.chain.model.account.WalletKeyPair
import com.tonapps.wallet.api.AuthorizationProvider
import com.tonapps.wallet.api.entity.Authorization
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.device.SecureDeviceRepository
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

    suspend fun getAccountAddress(wallet: WalletEntity, chain: Chain): Address?

    suspend fun buildAuth(wallet: WalletEntity?): Authorization

    suspend fun deleteWallet(id: String)

    suspend fun hasAnyWallet(): Boolean
}

class UnifiedAccountRepositoryImpl(
    private val accountRepository: AccountRepository,
    private val mcAccountRepository: McAccountRepository,
    private val tonWalletResolver: McTonWalletResolver,
    private val deviceRepository: SecureDeviceRepository,
) : UnifiedAccountRepository {

    // Combined with the MC store's write signal: the selected MC wallet is a snapshot,
    // so a rename in the MC store must re-resolve it; the legacy state never re-emits.
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
            val mc = mcAccountRepository.getWallets().mapNotNull { tonWalletResolver.toTonWalletEntity(it) }
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
            tonWalletResolver.getTonWallet(id)
        }
    }

    override suspend fun getTonWalletById(id: String): WalletEntity? {
        return withContext(Async.Io) {
            accountRepository.getWalletById(id) ?: tonWalletResolver.getTonWallet(id)
        }
    }

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
                val entity = tonWalletResolver.toTonWalletEntity(mc) ?: continue
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
                accountRepository.selectDefaultWallet()
            }

            mcAccountRepository.refresh()
        }
    }

    override suspend fun hasAnyWallet(): Boolean {
        return withContext(Async.Io) {
            accountRepository.getWalletsCount() > 0 || mcAccountRepository.getWalletsCount() > 0
        }
    }

    private suspend fun resolveSelected(wallet: WalletEntity): WalletEntity {
        if (wallet.type != WalletType.Multichain) {
            return wallet
        }

        return tonWalletResolver.getTonWallet(wallet.id) ?: wallet
    }
}
