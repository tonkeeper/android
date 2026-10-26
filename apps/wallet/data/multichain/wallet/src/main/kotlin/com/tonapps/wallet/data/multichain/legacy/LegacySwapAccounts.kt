package com.tonapps.wallet.data.multichain.legacy

import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.wallet.SigningWallet
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.AccountBalanceEntity
import com.tonapps.wallet.data.multichain.account.AccountEntity
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.asset.AssetRateEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletType
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger
import kotlin.time.Duration.Companion.seconds

/**
 * A legacy wallet the multichain swap can trade GRAM ↔ USDT TRC20 for. The backend has never
 * registered it, so the swap runs on its own TON and TRON addresses and signs with their keys.
 */
data class LegacySwapWallet(
    val id: String,
    val accountId: String,
    val name: String,
    val emoji: String,
    val tonAddress: String,
    // Hex, 0x-prefixed, as multichain accounts store it.
    val tonPublicKey: String,
    // The contract version ChainKit signs the TON side with; it must not probe an undeployed wallet.
    val tonAddressType: Address.Type,
    val tronAddress: String,
) {

    // The wallet entity the confirmation screen works with. Only its id and TON contract matter there.
    fun toMcWallet(): McWalletEntity = McWalletEntity(
        credentialId = "",
        name = name,
        emoji = emoji,
        id = id,
        type = McWalletType.Multicoin,
        tonWalletType = tonAddressType,
    )

    /**
     * The swap's view of [assetId] in this wallet, built from the legacy balance list [tokens]; a
     * token that is not on the list has no balance. Null for an asset the legacy swap does not move.
     */
    fun account(assetId: String, tokens: List<AccountTokenEntity>, currency: WalletCurrency): AccountWithDetails? {
        val token = LegacySwapAssets.token(assetId) ?: return null
        val chain = LegacySwapAssets.chain(assetId) ?: return null
        val position = tokens.firstOrNull { it.address == token.address }

        val data = when (chain) {
            is Chain.Ton -> AccountEntity(
                walletId = id,
                network = chain.network.type.id,
                mode = chain.network.mode.id,
                displayAddress = tonAddress,
                publicKey = tonPublicKey,
                segwitPublicKey = "",
                addressType = tonAddressType,
            )

            else -> AccountEntity(
                walletId = id,
                network = chain.network.type.id,
                mode = chain.network.mode.id,
                displayAddress = tronAddress,
                publicKey = "",
                segwitPublicKey = "",
            )
        }

        val rate = position
            ?.takeIf { it.rateNow.isPositive }
            ?.let {
                AssetRateEntity(
                    price = it.rateNow.value.toPlainString(),
                    percentChange24h = it.rateDiff24h,
                    currencyCode = currency.code,
                )
            }

        return AccountWithDetails(
            data = data,
            balance = AccountBalanceEntity(
                available = position?.balance?.value?.toNano(token.decimals) ?: "0",
            ),
            asset = LegacySwapAssets.asset(assetId) ?: return null,
            rate = rate,
        )
    }

    companion object {

        private val tonAddressTypes = mapOf(
            WalletVersion.V4R2 to Address.Type.TonV4R2,
            WalletVersion.V5R1 to Address.Type.TonV5R1,
        )

        /**
         * The rule iOS applies too: a regular mainnet wallet on a contract ChainKit can sign for, with
         * a TRON address, while TRON is enabled. Watch-only, Ledger, signer and lockup wallets have no
         * keys to sign with here, and multichain wallets swap as themselves.
         */
        fun of(wallet: WalletEntity, tronAddress: String?, tronDisabled: Boolean): LegacySwapWallet? {
            if (wallet.type != WalletType.Default || wallet.testnet || tronDisabled) {
                return null
            }

            val addressType = tonAddressTypes[wallet.version] ?: return null
            val tron = tronAddress?.takeIf { it.isNotBlank() } ?: return null

            return LegacySwapWallet(
                id = wallet.id,
                accountId = wallet.accountId,
                name = wallet.label.accountName,
                emoji = wallet.label.emoji.toString(),
                tonAddress = wallet.address,
                tonPublicKey = "0x${wallet.publicKey.hex()}",
                tonAddressType = addressType,
                tronAddress = tron,
            )
        }
    }
}

/** The assets a legacy swap moves: GRAM and USDT TRC20, plus TRX, which the TRON side pays its fee in. */
object LegacySwapAssets {

    val TON_ASSET_ID = Chain.Ton.Mainnet.coinAssetId
    val TRX_ASSET_ID = Chain.Tron.Mainnet.coinAssetId
    val USDT_TRC20_ASSET_ID = "${Chain.Tron.Mainnet.network.type.id}/${Chain.Tron.Mainnet.network.mode.id}/trc20/${TokenEntity.TRC20_USDT}"

    // The pair a legacy swap is locked to.
    val pair: Set<String> = setOf(TON_ASSET_ID, USDT_TRC20_ASSET_ID)

    fun token(assetId: String): TokenEntity? = when (assetId) {
        TON_ASSET_ID -> TokenEntity.TON
        TRX_ASSET_ID -> TokenEntity.TRX
        USDT_TRC20_ASSET_ID -> TokenEntity.TRON_USDT
        else -> null
    }

    fun chain(assetId: String): Chain? = when (assetId) {
        TON_ASSET_ID -> Chain.Ton.Mainnet
        TRX_ASSET_ID, USDT_TRC20_ASSET_ID -> Chain.Tron.Mainnet
        else -> null
    }

    fun asset(assetId: String): AssetEntity? {
        val token = token(assetId) ?: return null
        return AssetEntity(
            id = assetId,
            name = token.name,
            symbol = token.symbol,
            decimals = token.decimals,
            imageUrl = token.imageUri.toString(),
        )
    }
}

/** The pair a legacy swap opens on: GRAM and USDT TRC20, in the order the caller asked for. */
data class LegacySwapPair(val sell: AssetEntity, val buy: AssetEntity)

interface LegacySwapAccounts {

    /** The legacy wallet [walletId] names, when the multichain swap can trade for it; null otherwise. */
    suspend fun find(walletId: String): LegacySwapWallet?

    suspend fun findAccount(wallet: LegacySwapWallet, assetId: String, refresh: Boolean = false): AccountWithDetails?

    /**
     * The fixed pair with the side the caller opened kept where it put it; with neither side asked
     * for, GRAM is sold.
     */
    fun pair(sellAssetId: String?, buyAssetId: String?): LegacySwapPair

    /** The raw keys of the wallet's TON and TRON addresses. The caller has the passcode confirmed. */
    suspend fun signingKeys(wallet: LegacySwapWallet): SigningWallet.Keys

    /** Reloads the legacy balances the sent swap changed. */
    fun onTransactionSent(wallet: LegacySwapWallet)
}

class LegacySwapAccountsImpl(
    private val api: API,
    private val accountRepository: AccountRepository,
    private val tokenRepository: TokenRepository,
    private val settingsRepository: SettingsRepository,
) : LegacySwapAccounts {

    override suspend fun find(walletId: String): LegacySwapWallet? = withContext(Async.Io) {
        val wallet = accountRepository.getWalletById(walletId) ?: return@withContext null
        if (wallet.type != WalletType.Default) {
            return@withContext null
        }

        LegacySwapWallet.of(
            wallet = wallet,
            tronAddress = accountRepository.getTronAddressOrNull(walletId),
            tronDisabled = api.getConfig(wallet.network).flags.disableTron,
        )
    }

    override suspend fun findAccount(
        wallet: LegacySwapWallet,
        assetId: String,
        refresh: Boolean,
    ): AccountWithDetails? = withContext(Async.Io) {
        val currency = settingsRepository.currency
        // A load that fails answers null: the cached balances stand in, as for a multichain account,
        // rather than a zero that reads as an empty wallet.
        val tokens = tokenRepository.get(
            currency = currency,
            accountId = wallet.accountId,
            network = TonNetwork.MAINNET,
            refresh = refresh,
            tronAddress = wallet.tronAddress,
        ) ?: tokenRepository.getLocal(currency, wallet.accountId, TonNetwork.MAINNET)
        // A real list always has its TON row: an empty one means no balances are known at all.
        if (tokens.isEmpty()) {
            return@withContext null
        }

        wallet.account(assetId, tokens, currency)
    }

    override fun pair(sellAssetId: String?, buyAssetId: String?): LegacySwapPair {
        val pair = LegacySwapAssets.pair
        val sellId = sellAssetId?.takeIf { it in pair }
            ?: pair.first { it != buyAssetId }
        val buyId = pair.first { it != sellId }

        return LegacySwapPair(
            sell = LegacySwapAssets.asset(sellId) ?: error("No legacy swap asset $sellId"),
            buy = LegacySwapAssets.asset(buyId) ?: error("No legacy swap asset $buyId"),
        )
    }

    override suspend fun signingKeys(wallet: LegacySwapWallet): SigningWallet.Keys = withContext(Async.Io) {
        val ton = accountRepository.getPrivateKey(wallet.id)
            ?: throw IllegalStateException("No TON key for ${wallet.id}")
        val tron = accountRepository.getTronPrivateKey(wallet.id)
            ?: throw IllegalStateException("No TRON key for ${wallet.id}")

        SigningWallet.Keys(
            mapOf(
                Chain.Ton.Mainnet to ton.key.toByteArray(),
                Chain.Tron.Mainnet to tron.toKeyBytes(),
            )
        )
    }

    override fun onTransactionSent(wallet: LegacySwapWallet) {
        Async.globalScope().launch {
            for (tick in REFRESH_TICKS) {
                delay(tick)
                try {
                    findAccount(wallet, LegacySwapAssets.TON_ASSET_ID, refresh = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }
    }

    private companion object {
        // TON lands in seconds, a TRON transfer and the swap's other side take longer.
        val REFRESH_TICKS = listOf(3.seconds, 10.seconds, 30.seconds)
    }
}

// The 32-byte big-endian secp256k1 scalar: BigInteger.toByteArray() adds a sign byte to a key with
// its top bit set and drops leading zero bytes.
internal fun BigInteger.toKeyBytes(): ByteArray {
    val raw = toByteArray()
    return when {
        raw.size == KEY_SIZE -> raw
        raw.size > KEY_SIZE -> raw.copyOfRange(raw.size - KEY_SIZE, raw.size)
        else -> ByteArray(KEY_SIZE - raw.size) + raw
    }
}

private const val KEY_SIZE = 32