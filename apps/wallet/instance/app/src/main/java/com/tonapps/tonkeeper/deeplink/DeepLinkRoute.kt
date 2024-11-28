package com.tonapps.tonkeeper.deeplink

import android.net.Uri
import android.util.Log
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
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.tlb.CellRef
import org.ton.tlb.asRef
import java.io.File

sealed class DeepLinkRoute {

    data class Unknown(val uri: Uri): DeepLinkRoute()

    sealed class Tabs(val tabUri: String, open val from: String): DeepLinkRoute() {
        data class Main(override val from: String): Tabs("tonkeeper://wallet", from)
        data class Activity(override val from: String): Tabs("tonkeeper://activity", from)
        data class Browser(override val from: String): Tabs("tonkeeper://browser", from)
        data class Collectibles(override val from: String): Tabs("tonkeeper://collectibles", from)
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

    data class Install(
        val file: File
    ): DeepLinkRoute() {

        constructor(uri: Uri) : this(
            file = uri.query("file")?.let {
                File(it)
            } ?: throw IllegalArgumentException("\"file\" query parameter is required")
        )
    }

    data class Transfer(
        val exp: Long,
        val address: String,
        val amount: Long?,
        val text: String?,
        val jettonAddress: String?,
        val bin: Cell?,
        val initStateBase64: String?
    ): DeepLinkRoute() {

        val isExpired: Boolean
            get() = exp > 0 && exp < (System.currentTimeMillis() / 1000)

        constructor(uri: Uri) : this(
            exp = uri.queryPositiveLong("exp") ?: 0,
            address = uri.pathOrNull ?: throw IllegalArgumentException("Address is required"),
            amount = uri.queryPositiveLong("amount"),
            text = uri.query("text"),
            jettonAddress = uri.query("jettonAddress") ?: uri.query("jetton"),
            bin = uri.query("bin")?.cellFromBase64(),
            initStateBase64 = uri.query("init")
        ) {
            if (uri.hasUnsupportedQuery(true, "exp", "amount", "text", "jettonAddress", "jetton", "bin", "init")) {
                throw IllegalArgumentException("Unsupported query parameters")
            }

            if (text != null && bin != null) {
                throw IllegalArgumentException("Text and bin are mutually exclusive")
            }

            if (amount == null && (bin != null || initStateBase64 != null)) {
                throw IllegalArgumentException("Amount is required for bin or init")
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

    data class Story(val id: String): DeepLinkRoute() {

        constructor(uri: Uri) : this(
            id = uri.pathOrNull ?: throw IllegalArgumentException("Story id is required")
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
            val from = input.query("from") ?: "deep-link"
            val domain = uri.hostOrNull ?: return Unknown(uri)
            try {
                return when (domain) {
                    "backup", "backups" -> Backups
                    "staking" -> Staking
                    "buy-ton" -> Purchase
                    "send" -> Send
                    "wallet", "main" -> Tabs.Main(from)
                    "activity", "history" -> Tabs.Activity(from)
                    "browser" -> Tabs.Browser(from)
                    "collectibles" -> Tabs.Collectibles(from)
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
                    "story", "stories" -> Story(uri)
                    "install" -> Install(uri)
                    else -> throw IllegalArgumentException("Unknown domain: $domain")
                }
            } catch (e: Throwable) {
                Log.e("ApkDownloadWorker", "Failed to resolve deep link: $uri", e)
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