package com.tonapps.core.deeplink

import android.net.Uri
import androidx.core.net.toUri
import com.tonapps.blockchain.ton.extensions.cellFromBase64
import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import com.tonapps.blockchain.ton.extensions.isValidTonDomain
import com.tonapps.blockchain.ton.extensions.publicKeyFromHex
import com.tonapps.blockchain.tron.isValidTronAddress
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.core.components.tokenAssetId
import com.tonapps.extensions.currentTimeSeconds
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
import java.math.BigInteger

private const val MYSTERY_RAFFLE_SLUG = "mystery_raffle"

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
        val exp: Long?,
        val address: String,
        val amount: Long?,
        val text: String?,
        val assetId: String?,
        val jettonAddress: String?,
        val bin: Cell?,
        val initStateBase64: String?
    ): DeepLinkRoute() {

        val isExpired: Boolean
            get() {
                if (exp == null || 0 >= exp) {
                    return false
                }
                val now = currentTimeSeconds()
                val fixedExp = exp - 15
                return now > fixedExp
            }

        constructor(uri: Uri) : this(
            exp = uri.queryLong("exp")?.let { parsedExp ->
                val maxExp = currentTimeSeconds() + MAX_EXP
                val validUntil = minOf(parsedExp, maxExp)
                if (0 >= validUntil) {
                    currentTimeSeconds() + MAX_EXP
                } else {
                    validUntil
                }
            },
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
    }

    data class Story(val id: String): DeepLinkRoute() {

        constructor(uri: Uri) : this(
            id = uri.pathOrNull ?: throw IllegalArgumentException("Story id is required")
        )

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

    companion object {

        private const val PREFIX = "tonkeeper://"

        fun resolve(input: Uri): DeepLinkRoute {
            if (input.scheme.equals("wc", ignoreCase = true)) {
                return WalletConnect(input)
            }

            EthereumTransferLink.parse(input.toString())?.let { return it }

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
                    "raffle", "raffles" -> Raffle(uri)
                    "migrate" -> Migrate(from)
                    "add-wallet" -> AddWallet(uri)
                    else -> throw IllegalArgumentException("Unknown domain: $domain")
                }
            } catch (e: Throwable) {
                return Unknown(uri)
            }
        }

        fun normalize(uri: Uri): Uri {
            return uri.toString()
                .replace("ton://", PREFIX)
                .replace("https://app.tonkeeper.com/", PREFIX)
                .replace("http://app.tonkeeper.com/", PREFIX)
                .replace("app://tonkeeper.com/", PREFIX)
                .replace("tc://", "${PREFIX}/ton-connect")
                .replace("///", "//")
                .toUri()
        }

        fun isAppLink(url: String): Boolean {
            return url.startsWith(PREFIX) ||
                url.startsWith("ton://") ||
                url.startsWith("wc:") ||
                url.startsWith("https://app.tonkeeper.com")
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
