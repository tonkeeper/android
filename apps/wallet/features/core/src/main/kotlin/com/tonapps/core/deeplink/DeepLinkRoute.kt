package com.tonapps.core.deeplink

import android.net.Uri
import androidx.core.net.toUri
import com.tonapps.blockchain.ton.extensions.cellFromBase64
import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import com.tonapps.blockchain.ton.extensions.isValidTonDomain
import com.tonapps.blockchain.ton.extensions.publicKeyFromHex
import com.tonapps.blockchain.tron.isValidTronAddress
import com.tonapps.bus.generated.Events.Perps.PerpsEntryPoint
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.chainkit.core.chain.model.account.TokenType
import com.tonapps.core.components.tokenAssetId
import com.tonapps.extensions.fullPathOrNull
import com.tonapps.extensions.hasUnsupportedQuery
import com.tonapps.extensions.hostOrNull
import com.tonapps.extensions.pathOrNull
import com.tonapps.extensions.query
import com.tonapps.extensions.queryBoolean
import com.tonapps.extensions.queryLong
import com.tonapps.extensions.toUriOrNull
import com.tonapps.wallet.data.multichain.account.jettonAssetId
import com.tonapps.wallet.data.multichain.account.tonCoinAssetId
import org.ton.cell.Cell
import org.ton.kotlin.crypto.PublicKeyEd25519
import java.math.BigDecimal
import java.math.BigInteger

private const val MYSTERY_RAFFLE_SLUG = "mystery_raffle"
private const val DINNER_WITH_TRUMP_SLUG = "dinner_with_trump"
private const val TRUMP_DAPP_URL = "https://trump.keeperwallet.com/"

sealed class DeepLinkRoute {

    data class Unknown(val uri: Uri): DeepLinkRoute()

    sealed class Tabs(val tabUri: String, open val from: String): DeepLinkRoute() {

        data class Main(
            override val from: String
        ): Tabs("tonkeeper://wallet", from)

        data class Activity(
            override val from: String
        ): Tabs("tonkeeper://activity", from)

        data class Browser(
            override val from: String,
            val category: String?,
            val network: String?,
        ): Tabs(buildBrowserUri(category, network), from) {

            private companion object {

                private fun buildBrowserUri(category: String?, network: String?): String {
                    val builder = "tonkeeper://browser".toUri().buildUpon()
                    if (!category.isNullOrBlank()) {
                        builder.appendQueryParameter("category", category)
                    }
                    if (!network.isNullOrBlank()) {
                        builder.appendQueryParameter("network", network)
                    }
                    return builder.build().toString()
                }
            }
        }

        data class Collectibles(
            override val from: String
        ): Tabs("tonkeeper://collectibles", from)

        data class Trading(
            override val from: String,
            val shelfKey: String?,
        ): Tabs(buildTradingUri(shelfKey), from) {

            private companion object {

                private fun buildTradingUri(shelfKey: String?): String {
                    val builder = "tonkeeper://trading".toUri().buildUpon()
                    if (!shelfKey.isNullOrBlank()) {
                        builder.appendQueryParameter("shelf", shelfKey)
                    }
                    return builder.build().toString()
                }
            }
        }
    }

    sealed class Internal: DeepLinkRoute()

    data object Backups: Internal()
    data object Staking: DeepLinkRoute()
    data object Purchase: DeepLinkRoute()
    data class Send(val address: String?): DeepLinkRoute() {
        constructor(uri: Uri) : this(address = uri.query("address"))
    }
    data class Migrate(val from: String): DeepLinkRoute()

    data class Deposit(
        val fromToken: String?,
        val toToken: String?,
        val fromNetwork: String?,
        val toNetwork: String?,
        val cashMethod: String?,
    ): DeepLinkRoute() {

        constructor(uri: Uri) : this(
            fromToken = uri.query("ft"),
            toToken = uri.query("tt"),
            fromNetwork = uri.query("fn"),
            toNetwork = uri.query("tn"),
            cashMethod = uri.query("cm"),
        )
    }

    data class Withdraw(
        val fromToken: String?,
        val toToken: String?,
        val fromNetwork: String?,
        val toNetwork: String?,
        val cashMethod: String?,
    ): DeepLinkRoute() {

        constructor(uri: Uri) : this(
            fromToken = uri.query("ft"),
            toToken = uri.query("tt"),
            fromNetwork = uri.query("fn"),
            toNetwork = uri.query("tn"),
            cashMethod = uri.query("cm"),
        )
    }
    data object Settings: Internal()
    data object SettingsSecurity: Internal()
    data object SettingsCurrency: Internal()
    data object SettingsLanguage: Internal()
    data object SettingsExtensions: Internal()
    data object SettingsNotifications: Internal()
    data object EditWalletLabel: Internal()
    data object Camera: DeepLinkRoute()
    data object Receive: DeepLinkRoute()
    data object ManageAssets: Internal()
    data object WalletPicker: Internal()

    data class StakingPool(val poolAddress: String): DeepLinkRoute() {

        constructor(uri: Uri) : this(
            poolAddress = uri.pathOrNull ?: throw IllegalArgumentException("Pool address is required")
        )
    }

    data class Swap(
        val from: String,
        val to: String?
    ): DeepLinkRoute() {

        val fromAssetId: String?
            get() = from.takeIf { it.contains('/') }

        val toAssetId: String?
            get() = to?.takeIf { it.contains('/') }

        constructor(uri: Uri) : this(
            from = uri.query("ft") ?: "TON",
            to = uri.query("tt")
        )
    }

    data object DnsRenew: DeepLinkRoute()

    data class Transfer(
        // As the link sets it: see validUntil.
        val exp: Long?,
        val address: String,
        val amount: Long?,
        val text: String?,
        val assetId: String?,
        val jettonAddress: String?,
        val bin: Cell?,
        val initStateBase64: String?
    ): DeepLinkRoute() {

        constructor(uri: Uri) : this(
            exp = uri.queryLong("exp"),
            address = uri.pathOrNull ?: throw IllegalArgumentException("Address is required"),
            amount = uri.queryLong("amount"),
            text = uri.query("text"),
            assetId = uri.query("asset_id")?.takeIf { it.contains('/') },
            jettonAddress = uri.query("jettonAddress") ?: uri.query("jetton"),
            bin = uri.query("bin")?.cellFromBase64(),
            initStateBase64 = uri.query("init")
        ) {
            if (uri.hasUnsupportedQuery(true, "exp", "amount", "text", "asset_id", "jettonAddress", "jetton", "bin", "init")) {
                throw IllegalArgumentException("Unsupported query parameters")
            }

            if (address.isNotBlank() && (!address.isValidTonAddress() && !address.isValidTronAddress() && !address.isValidTonDomain())) {
                throw IllegalArgumentException("Invalid address")
            }

            if (!jettonAddress.isNullOrBlank() && !jettonAddress.isValidTonAddress() && !jettonAddress.isValidTronAddress()) {
                throw IllegalArgumentException("Invalid jetton address")
            }

            if (text != null && bin != null) {
                throw IllegalArgumentException("Text and bin are mutually exclusive")
            }

            amount?.let {
                if (0 > it) {
                    throw IllegalArgumentException("Amount must be positive")
                }
            }

            if (amount == null && (bin != null || initStateBase64 != null)) {
                throw IllegalArgumentException("Amount is required for bin or init")
            }
        }

        /** The link's deadline in seconds: see [Companion.validUntil]. */
        fun validUntil(now: Long): Long = validUntil(exp, now)

        fun isExpired(now: Long): Boolean = isExpired(validUntil(now), now)

        fun targetAssetId(testnet: Boolean): String? {
            if (assetId != null) {
                return assetId
            }
            val isTonRecipient = address.isValidTonAddress() || address.isValidTonDomain()
            if (!isTonRecipient) {
                return null
            }
            if (jettonAddress.isNullOrBlank()) {
                return tonCoinAssetId(testnet)
            }
            if (jettonAddress.isValidTonAddress()) {
                return jettonAssetId(jettonAddress, testnet)
            }
            return null
        }

        fun jettonMaster(): String? {
            if (assetId != null) {
                return assetId.takeIf { it.contains("/jetton/") }?.substringAfterLast('/')
            }
            return jettonAddress
        }

        companion object {
            const val MAX_EXP = 10 * 60L
            private const val EXPIRY_LEAD_SECONDS = 15L

            /**
             * The deadline in seconds a transfer link or a TON Connect request sets with [exp]: at most
             * [MAX_EXP] after [now] (server time), or [MAX_EXP] after it when it sets none.
             */
            fun validUntil(exp: Long?, now: Long): Long {
                val maxExp = now + MAX_EXP
                val own = exp?.takeIf { it > 0 } ?: return maxExp
                return minOf(own, maxExp)
            }

            fun isExpired(validUntil: Long, now: Long): Boolean {
                return now > validUntil - EXPIRY_LEAD_SECONDS
            }
        }
    }

    data class EvmTransfer(
        val recipient: String,
        val contract: String?,
        val chain: Chain.Evm?,
        val amount: BigInteger?,
    ): DeepLinkRoute() {

        fun assetId(chain: Chain.Evm): String {
            return if (contract == null) {
                chain.coinAssetId
            } else {
                chain.tokenAssetId(contract)
            }
        }
    }

    data class SolanaTransfer(
        val recipient: String,
        val mint: String?,
        val amount: BigDecimal?,
    ): DeepLinkRoute() {

        val assetId: String
            get() {
                val chain = Chain.Solana.Mainnet
                // A mint is base58, so unlike an EVM contract its case is part of it.
                return if (mint == null) {
                    chain.coinAssetId
                } else {
                    "${chain.network.type.id}/${chain.network.mode.id}/${TokenType.SPL.id}/$mint"
                }
            }

        /** [amount] in base units, or null when it has more fraction digits than the asset carries. */
        fun baseUnits(decimals: Int): BigInteger? {
            val value = amount ?: return null
            return try {
                value.movePointRight(decimals).toBigIntegerExact()
            } catch (e: ArithmeticException) {
                null
            }
        }
    }

    data class PickWallet(val walletId: String): DeepLinkRoute() {

        constructor(uri: Uri) : this(
            walletId = uri.pathOrNull ?: throw IllegalArgumentException("Wallet id is required")
        )
    }

    data class Battery(
        val jetton: String?,
        val promocode: String?
    ): DeepLinkRoute() {

        constructor(uri: Uri) : this(
            jetton = uri.query("jetton"),
            promocode = uri.query("promocode"),
        )
    }

    data class Raffle(val id: String?, val source: String? = null): DeepLinkRoute() {

        constructor(uri: Uri) : this(
            id = uri.pathOrNull?.takeIf { it != MYSTERY_RAFFLE_SLUG },
            source = uri.query("source"),
        )

        companion object {

            fun of(uri: Uri): DeepLinkRoute {
                return if (uri.pathOrNull == DINNER_WITH_TRUMP_SLUG) {
                    DApp(TRUMP_DAPP_URL)
                } else {
                    Raffle(uri)
                }
            }
        }
    }

    data class Story(val id: String): DeepLinkRoute() {

        constructor(uri: Uri) : this(
            id = uri.pathOrNull ?: throw IllegalArgumentException("Story id is required")
        )

    }

    sealed class Perps : DeepLinkRoute() {

        abstract val entryPoint: PerpsEntryPoint?

        data class Home(override val entryPoint: PerpsEntryPoint? = null) : Perps()

        data class Market(val marketIndex: Int, override val entryPoint: PerpsEntryPoint? = null) : Perps()

        data class AutoClose(val marketIndex: Int, override val entryPoint: PerpsEntryPoint? = null) : Perps()

        data class MarketSymbol(val symbol: String, override val entryPoint: PerpsEntryPoint? = null) : Perps()

        companion object {

            const val ENTRY_POINT_QUERY = "entry_point"

            private val LINK_ENTRY_POINTS = listOf(PerpsEntryPoint.MainBanner, PerpsEntryPoint.Loyalty)

            private val MARKET_ID = Regex("[0-9]+")

            private val MARKET_SYMBOL = Regex("[A-Za-z0-9]+")

            fun of(uri: Uri): Perps = of(
                segments = uri.pathSegments,
                entryPoint = uri.query(ENTRY_POINT_QUERY)?.let { key -> LINK_ENTRY_POINTS.firstOrNull { it.key == key } },
            )

            fun of(segments: List<String>, entryPoint: PerpsEntryPoint?): Perps {
                if (segments.isEmpty()) {
                    return Home(entryPoint)
                }
                require(segments.first() == "market" && segments.size >= 2) { "Unknown perps path" }
                val market = segments[1]
                val autoClose = segments.getOrNull(2) == "auto-close"
                if (!MARKET_ID.matches(market)) {
                    require(!autoClose && MARKET_SYMBOL.matches(market)) { "Invalid market id" }
                    return MarketSymbol(market, entryPoint)
                }
                val marketIndex = market.toIntOrNull() ?: throw IllegalArgumentException("Invalid market id")
                return if (autoClose) {
                    AutoClose(marketIndex, entryPoint)
                } else {
                    Market(marketIndex, entryPoint)
                }
            }
        }
    }

    sealed class Earn : DeepLinkRoute() {

        data object Home : Earn()

        /** [network] names the chain as the Earn page's routes do: `mainnet`, `base`, `bsc` or `arbitrum`. */
        data class Vault(val network: String, val vaultId: String) : Earn()

        companion object {

            private val NETWORKS = setOf("mainnet", "base", "bsc", "arbitrum")

            // A vault's contract address, optionally with a "-<suffix>" of letters, digits and dashes,
            // such as "-1", "-0x<token>" or "-usdc-v2".
            private val VAULT_ID = Regex("0[xX][0-9a-fA-F]{40}(-[A-Za-z0-9-]+)?")

            fun of(uri: Uri): Earn = of(network = uri.query("network"), vaultId = uri.query("vault"))

            fun of(network: String?, vaultId: String?): Earn {
                if (network == null && vaultId == null) {
                    return Home
                }
                val earnNetwork = network?.lowercase()
                require(earnNetwork != null && earnNetwork in NETWORKS) { "Unknown Earn network: $network" }
                require(vaultId != null && VAULT_ID.matches(vaultId)) { "Invalid Earn vault id: $vaultId" }
                return Vault(earnNetwork, vaultId)
            }
        }
    }

    data class AddWallet(val raffleSourceWalletId: String? = null): DeepLinkRoute() {

        constructor(uri: Uri) : this(
            raffleSourceWalletId = uri.query(RAFFLE_SOURCE_WALLET_ID_QUERY),
        )

        companion object {
            const val RAFFLE_SOURCE_WALLET_ID_QUERY = "raffle_source_wallet_id"
        }
    }

    data class AccountEvent(
        val eventId: String,
        val address: String?
    ): DeepLinkRoute() {

        constructor(uri: Uri) : this(
            eventId = uri.pathOrNull ?: throw IllegalArgumentException("Event id is required"),
            address = uri.query("address")
        )
    }

    data class Exchange(val methodName: String): DeepLinkRoute() {

        constructor(uri: Uri) : this(
            methodName = uri.pathOrNull ?: throw IllegalArgumentException("Method name is required")
        )
    }

    data class DApp(val url: String): DeepLinkRoute() {

        constructor(uri: Uri) : this(
            url = parseLink(uri)
        )

        private companion object {

            private fun parseLink(uri: Uri): String {
                var path = uri.toString().replace("tonkeeper://dapp/", "").toUriOrNull()
                if (path == null) {
                    path = uri.lastPathSegment?.toUriOrNull()
                }
                return path?.toString()?.let {
                    if (it.startsWith("http")) {
                        Uri.decode(it)
                    } else {
                        "https://${Uri.decode(it)}"
                    }
                } ?: throw IllegalArgumentException("DApp url is required")
            }
        }
    }

    data class MultichainDApp(val url: String): DeepLinkRoute()

    data class Signer(
        val publicKey: PublicKeyEd25519,
        val name: String?,
        val local: Boolean,
    ): DeepLinkRoute() {

        constructor(uri: Uri) : this(
            publicKey = uri.query("pk")?.publicKeyFromHex() ?: throw IllegalArgumentException("Public key is required"),
            name = uri.query("name"),
            local = uri.queryBoolean("local")
        )
    }

    data class TonConnect(val uri: Uri): DeepLinkRoute()

    data class WalletConnect(val uri: Uri): DeepLinkRoute()

    data class Jetton(val address: String): DeepLinkRoute() {

        constructor(uri: Uri) : this(
            address = uri.pathOrNull ?: uri.query("jetton") ?: "TON"
        )
    }

    data class Asset(val assetId: String): DeepLinkRoute() {

        constructor(uri: Uri) : this(
            assetId = uri.fullPathOrNull
                ?: uri.query("asset_id")
                ?: uri.query("id")
                ?: throw IllegalArgumentException("Asset id is required")
        )
    }

    data class Loyalty(val referralCode: String?): DeepLinkRoute() {

        constructor(uri: Uri) : this(
            referralCode = referralCodeOf(uri.query(REFERRAL_CODE_QUERY)),
        )

        companion object {

            const val REFERRAL_CODE_QUERY = "referral_code"

            private val REFERRAL_CODE = Regex("[A-Za-z0-9_-]{1,64}")

            fun referralCodeOf(value: String?): String? {
                return value?.trim()?.takeIf(REFERRAL_CODE::matches)
            }

            fun ofReferralPath(segments: List<String>): Loyalty {
                return Loyalty(referralCodeOf(segments.singleOrNull()))
            }
        }
    }

    companion object {

        private const val PREFIX = "tonkeeper://"
        private const val KEEPER_PREFIX = "keeper://"
        private val UNIVERSAL_HOSTS = setOf("app.tonkeeper.com", "app.keeperwallet.com")
        private val IN_APP_SCHEMES = setOf("tonkeeper", "keeper", "ton", "tc")

        fun resolve(input: Uri): DeepLinkRoute {
            if (input.scheme.equals("wc", ignoreCase = true)) {
                return WalletConnect(input)
            }

            EthereumTransferLink.parse(input.toString())?.let { return it }
            SolanaTransferLink.parse(input.toString())?.let { return it }

            val uri = normalize(input)
            val from = input.query("from") ?: "deep-link"
            val domain = uri.hostOrNull ?: return Unknown(uri)
            if (domain == "dns" && uri.pathOrNull?.startsWith("expiring") == true) {
                return DnsRenew
            }
            try {
                return when (domain) {
                    "backup", "backups" -> Backups
                    "staking" -> Staking
                    "buy-ton" -> Purchase
                    "deposit" -> Deposit(uri)
                    "send" -> Send(uri)
                    "migrate", "migration" -> Migrate(from)
                    "withdraw" -> Withdraw(uri)
                    "wallet", "main" -> Tabs.Main(from)
                    "activity", "history" -> Tabs.Activity(from)
                    "browser" -> Tabs.Browser(
                        from = from,
                        category = uri.query("category") ?: uri.lastPathSegment,
                        network = normalizeNetwork(uri.query("network")),
                    )
                    "collectibles" -> Tabs.Collectibles(from)
                    "trading" -> Tabs.Trading(from, uri.query("shelf") ?: uri.lastPathSegment)
                    "settings" -> Settings
                    "pool" -> StakingPool(uri)
                    "swap" -> Swap(uri)
                    "transfer" -> Transfer(uri)
                    "pick" -> PickWallet(uri)
                    "battery" -> Battery(uri)
                    "action" -> AccountEvent(uri)
                    "exchange" -> try {
                        Exchange(uri)
                    } catch (e: Throwable) {
                        Purchase
                    }
                    "dapp" -> DApp(uri)
                    "ton-connect" -> TonConnect(uri)
                    "wc" -> WalletConnect(uri)
                    "signer" -> Signer(uri)
                    "security" -> SettingsSecurity
                    "currency" -> SettingsCurrency
                    "language" -> SettingsLanguage
                    "extensions" -> SettingsExtensions
                    "notifications", "push" -> SettingsNotifications
                    "edit", "customization" -> EditWalletLabel
                    "camera", "scan", "scanner" -> Camera
                    "qr", "receive" -> Receive
                    "manage" -> ManageAssets
                    "picker", "wallets" -> WalletPicker
                    "jetton", "token" -> Jetton(uri)
                    "asset", "assets" -> Asset(uri)
                    "story", "stories" -> Story(uri)
                    "raffle", "raffles" -> Raffle.of(uri)
                    "trump-multichain" -> MultichainDApp(TRUMP_DAPP_URL)
                    "perps" -> Perps.of(uri)
                    "earn" -> Earn.of(uri)
                    "migrate" -> Migrate(from)
                    "add-wallet" -> AddWallet(uri)
                    "loyalty" -> Loyalty(uri)
                    "r" -> Loyalty.ofReferralPath(uri.pathSegments)
                    else -> throw IllegalArgumentException("Unknown domain: $domain")
                }
            } catch (e: Throwable) {
                return Unknown(uri)
            }
        }

        fun normalize(uri: Uri): Uri {
            val raw = uri.toString()
            val tonkeeperValue = if (raw.startsWith(KEEPER_PREFIX, ignoreCase = true)) {
                PREFIX + raw.substring(KEEPER_PREFIX.length)
            } else {
                raw
            }
            return UNIVERSAL_HOSTS.fold(tonkeeperValue.replace("ton://", PREFIX)) { value, host ->
                value
                    .replace("https://$host/", PREFIX)
                    .replace("http://$host/", PREFIX)
                    .replace("app://${host.removePrefix("app.")}/", PREFIX)
            }
                .replace("tc://", "${PREFIX}/ton-connect")
                .replace("///", "//")
                .toUri()
        }

        fun isAppLink(url: String): Boolean {
            return url.startsWith(PREFIX) ||
                url.startsWith(KEEPER_PREFIX) ||
                url.startsWith("ton://") ||
                url.startsWith("wc:") ||
                UNIVERSAL_HOSTS.any { url.startsWith("https://$it") }
        }

        fun isUniversalHost(host: String?): Boolean {
            return host != null && host.lowercase() in UNIVERSAL_HOSTS
        }

        fun isInApp(uri: Uri): Boolean {
            return uri.scheme in IN_APP_SCHEMES || isUniversalHost(uri.host)
        }

        // Perps pushes (TK-4064) send a bare path such as "/perps/market/1".
        fun pushLink(data: (String) -> String?): Uri? {
            return listOfNotNull(data("link"), data("deeplink"))
                .map { link ->
                    if (link.startsWith("/")) {
                        PREFIX + link.removePrefix("/")
                    } else {
                        link
                    }
                }
                .firstOrNull(::isAppLink)
                ?.toUriOrNull()
        }

        private fun normalizeNetwork(value: String?): String? {
            val normalized = value?.trim()?.lowercase() ?: return null
            val canonical = if (normalized == "trx") {
                "tron"
            } else {
                normalized
            }
            return Network.Type.find(canonical)?.id
        }
    }
}
