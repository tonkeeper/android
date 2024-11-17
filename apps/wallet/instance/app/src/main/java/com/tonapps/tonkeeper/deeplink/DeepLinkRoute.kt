package com.tonapps.tonkeeper.deeplink

import android.net.Uri
import androidx.core.net.toUri
import com.tonapps.blockchain.ton.extensions.cellFromBase64
import com.tonapps.blockchain.ton.extensions.publicKeyFromHex
import com.tonapps.extensions.hasUnsupportedQuery
import com.tonapps.extensions.hostOrNull
import com.tonapps.extensions.pathOrNull
import com.tonapps.extensions.query
import com.tonapps.extensions.queryBoolean
import com.tonapps.extensions.queryPositiveLong
import org.ton.api.pub.PublicKeyEd25519
import org.ton.cell.Cell

sealed class DeepLinkRoute {

    data class Unknown(val uri: Uri): DeepLinkRoute()

    sealed class Tabs(val tabUri: String): DeepLinkRoute() {
        data object Main: Tabs("tonkeeper://wallet")
        data object Activity: Tabs("tonkeeper://activity")
        data object Browser: Tabs("tonkeeper://browser")
        data object Collectibles: Tabs("tonkeeper://collectibles")
    }

    sealed class Internal: DeepLinkRoute()

    data object Backups: Internal()
    data object Staking: DeepLinkRoute()
    data object Purchase: DeepLinkRoute()
    data object Send: DeepLinkRoute()
    data object Settings: Internal()
    data object SettingsSecurity: Internal()
    data object SettingsCurrency: Internal()
    data object SettingsLanguage: Internal()
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

        constructor(uri: Uri) : this(
            from = uri.query("ft") ?: "TON",
            to = uri.query("tt")
        )
    }

    data class Transfer(
        val exp: Long,
        val address: String,
        val amount: Long?,
        val text: String?,
        val jettonAddress: String?,
        val bin: Cell?
    ): DeepLinkRoute() {

        val isExpired: Boolean
            get() = exp > 0 && exp < (System.currentTimeMillis() / 1000)

        constructor(uri: Uri) : this(
            exp = uri.queryPositiveLong("exp") ?: 0,
            address = uri.pathOrNull ?: throw IllegalArgumentException("Address is required"),
            amount = uri.queryPositiveLong("amount"),
            text = uri.query("text"),
            jettonAddress = uri.query("jettonAddress") ?: uri.query("jetton"),
            bin = uri.query("bin")?.cellFromBase64()
        ) {
            if (uri.hasUnsupportedQuery(true, "exp", "amount", "text", "jettonAddress", "jetton", "bin")) {
                throw IllegalArgumentException("Unsupported query parameters")
            }
        }
    }


    data class PickWallet(val walletId: String): DeepLinkRoute() {

        constructor(uri: Uri) : this(
            walletId = uri.pathOrNull ?: throw IllegalArgumentException("Wallet id is required")
        )
    }

    data class Battery(val promocode: String?): DeepLinkRoute() {

        constructor(uri: Uri) : this(
            promocode = uri.query("promocode")
        )
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
            url = uri.pathOrNull?.let {
                "https://$it"
            } ?: throw IllegalArgumentException("DApp url is required")
        )
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

    data class Jetton(val address: String): DeepLinkRoute() {

        constructor(uri: Uri) : this(
            address = uri.pathOrNull ?: uri.query("jetton") ?: "TON"
        )
    }

    companion object {

        private const val PREFIX = "tonkeeper://"

        fun resolve(input: Uri): DeepLinkRoute {
            val uri = normalize(input)
            val domain = uri.hostOrNull ?: return Unknown(uri)
            try {
                return when (domain) {
                    "backup", "backups" -> Backups
                    "staking" -> Staking
                    "buy-ton" -> Purchase
                    "send" -> Send
                    "wallet", "main" -> Tabs.Main
                    "activity", "history" -> Tabs.Activity
                    "browser" -> Tabs.Browser
                    "collectibles" -> Tabs.Collectibles
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
                    "signer" -> Signer(uri)
                    "security" -> SettingsSecurity
                    "currency" -> SettingsCurrency
                    "language" -> SettingsLanguage
                    "notifications", "push" -> SettingsNotifications
                    "edit", "customization" -> EditWalletLabel
                    "camera", "scan", "scanner" -> Camera
                    "qr", "receive" -> Receive
                    "manage" -> ManageAssets
                    "picker", "wallets" -> WalletPicker
                    "jetton", "token" -> Jetton(uri)
                    else -> throw IllegalArgumentException("Unknown domain: $domain")
                }
            } catch (e: Throwable) {
                return Unknown(uri)
            }
        }

        private fun normalize(uri: Uri): Uri {
            return uri.toString()
                .replace("ton://", PREFIX)
                .replace("https://app.tonkeeper.com/", PREFIX)
                .replace("http://app.tonkeeper.com/", PREFIX)
                .replace("app://tonkeeper.com/", PREFIX)
                .replace("tc://", "${PREFIX}/ton-connect")
                .replace("///", "//")
                .toUri()
        }
    }
}