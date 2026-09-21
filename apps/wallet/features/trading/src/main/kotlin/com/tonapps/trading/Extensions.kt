package com.tonapps.trading

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.tonapps.blockchain.contract.Blockchain
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.extensions.toUriOrNull
import com.tonapps.uikit.icon.UIKitIcon
import io.tradingapi.models.AssetRef
import io.tradingapi.models.AssetType
import io.tradingapi.models.LinkType
import ui.theme.UIKit
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun Long.formatChartTime(
    locale: Locale,
    includeTime: Boolean,
    includeYear: Boolean,
): String {
    if (this <= 0L) {
        return ""
    }

    val zdt = Instant.ofEpochSecond(this)
        .atZone(ZoneId.systemDefault())

    val pattern = buildString {
        if (includeYear) {
            append("yyyy ")
        }
        append("EEE, d MMM")
        if (includeTime) {
            append(" HH:mm")
        }
    }

    return DateTimeFormatter.ofPattern(pattern, locale)
        .format(zdt)
}

// The backend sends the TON chain name as "Ton".
fun String.displayChainName(): String =
    if (equals("ton", ignoreCase = true)) {
        "TON"
    } else {
        this
    }

@Composable
fun String.percentDiffColor(): Color {
    return when {
        startsWith("-") -> UIKit.colorScheme.accent.red
        startsWith("+") -> UIKit.colorScheme.accent.green
        else -> UIKit.colorScheme.text.secondary
    }
}

val AssetRef.asTokenEntity: TokenEntity
    get() = assetTokenEntity(
        assetId = id,
        name = name,
        symbol = symbol,
        imageUrl = imageUrl,
        decimals = decimals,
    )

fun String.isTonOrTronAssetId(): Boolean = startsWith("ton/") || startsWith("tron/")

fun AssetRef.isTonOrTronChain(): Boolean = id.isTonOrTronAssetId()

private fun assetTokenEntity(
    assetId: String,
    name: String,
    symbol: String,
    imageUrl: String,
    decimals: Int,
): TokenEntity {
    val chain = assetId.substringBefore("/")
    val rawAddress = assetId.substringAfterLast("/")
    val isNativeCoin = rawAddress == "coin"
    val addr = if (isNativeCoin) {
        WalletCurrency.TON_CHAIN_KEY
    } else {
        rawAddress
    }
    return when (chain.lowercase()) {
        "ton" -> {
            if (addr.equals(WalletCurrency.TON_CHAIN_KEY, ignoreCase = true)) {
                TokenEntity.TON
            } else {
                TokenEntity(
                    blockchain = Blockchain.TON,
                    address = addr,
                    name = name,
                    symbol = symbol,
                    imageUri = imageUrl.toUriOrNull() ?: TokenEntity.TON_ICON_URI,
                    decimals = decimals,
                    verification = TokenEntity.Verification.none,
                    isRequestMinting = false,
                    isTransferable = true,
                    customPayloadApiUri = null,
                )
            }
        }
        "tron", "trx" -> {
            if (isNativeCoin) {
                TokenEntity.TRX
            } else {
                TokenEntity(
                    blockchain = Blockchain.TRON,
                    address = addr,
                    name = name,
                    symbol = symbol,
                    imageUri = imageUrl.toUriOrNull() ?: TokenEntity.TRX_ICON_URI,
                    decimals = decimals,
                    verification = TokenEntity.Verification.none,
                    isRequestMinting = false,
                    isTransferable = true,
                    customPayloadApiUri = null,
                )
            }
        }
        else -> TokenEntity(
            blockchain = Blockchain.TON,
            address = addr,
            name = name,
            symbol = symbol,
            imageUri = imageUrl.toUriOrNull() ?: TokenEntity.TON_ICON_URI,
            decimals = decimals,
            verification = TokenEntity.Verification.none,
            isRequestMinting = false,
            isTransferable = true,
            customPayloadApiUri = null,
        )
    }
}

fun AssetRef.isTokenized(): Boolean =
    assetType == AssetType.stocks || assetType == AssetType.etfs

fun AssetRef.omnistonBuyTokens(): Pair<String, String> {
    val target = asTokenEntity.asCurrency
    return if (target == WalletCurrency.TON) {
        WalletCurrency.USDT_TON.address to WalletCurrency.TON.address
    } else {
        val fromAddress = if (isTokenized()) {
            WalletCurrency.USDT_TON.address
        } else {
            WalletCurrency.TON.address
        }
        fromAddress to target.address
    }
}

fun AssetRef.omnistonSellTokens(): Pair<String, String> {
    val target = asTokenEntity.asCurrency
    return if (target == WalletCurrency.TON) {
        WalletCurrency.TON.address to WalletCurrency.USDT_TON.address
    } else {
        val toAddress = if (isTokenized()) {
            WalletCurrency.USDT_TON.address
        } else {
            WalletCurrency.TON.address
        }
        target.address to toAddress
    }
}

fun LinkType.iconRes(name: String? = null, url: String? = null): Int = when (this) {
    LinkType.telegram -> UIKitIcon.ic_telegram_16
    LinkType.twitter -> UIKitIcon.ic_x_16
    LinkType.facebook -> UIKitIcon.ic_facebook_16
    LinkType.instagram -> UIKitIcon.ic_instagram_16
    LinkType.discord -> UIKitIcon.ic_discord_16
    LinkType.github -> UIKitIcon.ic_github_16
    LinkType.getgems -> UIKitIcon.ic_getgems_16
    else -> inferLinkIconRes(name, url)
}

private fun inferLinkIconRes(name: String?, url: String?): Int {
    val linkName = name.orEmpty().lowercase()
    val host = url.orEmpty().lowercase()
        .substringAfter("://")
        .substringBefore('/')
        .substringBefore(':')

    fun hostIs(domain: String) = host == domain || host.endsWith(".$domain")

    return when {
        hostIs("t.me") || hostIs("telegram.org") || "telegram" in linkName -> UIKitIcon.ic_telegram_16
        hostIs("x.com") || hostIs("twitter.com") || linkName == "x" || "twitter" in linkName -> UIKitIcon.ic_x_16
        hostIs("github.com") || "github" in linkName -> UIKitIcon.ic_github_16
        hostIs("discord.gg") || hostIs("discord.com") || "discord" in linkName -> UIKitIcon.ic_discord_16
        hostIs("facebook.com") || "facebook" in linkName -> UIKitIcon.ic_facebook_16
        hostIs("instagram.com") || "instagram" in linkName -> UIKitIcon.ic_instagram_16
        hostIs("getgems.io") || "getgems" in linkName -> UIKitIcon.ic_getgems_16
        else -> UIKitIcon.ic_globe_16
    }
}
