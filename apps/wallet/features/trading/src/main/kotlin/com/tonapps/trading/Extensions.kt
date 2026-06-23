package com.tonapps.trading

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.tonapps.blockchain.contract.Blockchain
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.core.extensions.externalDrawableUrl
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
    if (this <= 0L) return ""

    val zdt = Instant.ofEpochSecond(this)
        .atZone(ZoneId.systemDefault())

    val pattern = buildString {
        if (includeYear) append("yyyy ")
        append("EEE, d MMM")
        if (includeTime) append(" HH:mm")
    }

    return DateTimeFormatter.ofPattern(pattern, locale)
        .format(zdt)
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
    get() {
        val chain = id.substringBefore("/")
        val addr = id.substringAfterLast("/").let {
            if (it == "coin") WalletCurrency.TON_CHAIN_KEY else it
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
            "tron" -> TokenEntity(
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

fun LinkType.iconRes(): Int = when (this) {
    LinkType.telegram -> UIKitIcon.ic_telegram_16
    LinkType.twitter -> UIKitIcon.ic_x_16
    LinkType.facebook -> UIKitIcon.ic_facebook_16
    LinkType.instagram -> UIKitIcon.ic_instagram_16
    LinkType.discord -> UIKitIcon.ic_discord_16
    LinkType.github -> UIKitIcon.ic_github_16
    LinkType.getgems -> UIKitIcon.ic_getgems_16
    else -> UIKitIcon.ic_globe_16
}

//TODO: remove in multichain
@Composable
fun String.assetChainImageUrl(): String? {
    val context = LocalContext.current

    if (contains("coin")) {
        return null
    }

    val address = substringAfterLast("/")
    val isUsdtTon = startsWith("ton") &&
        address.equals(WalletCurrency.USDT_TON_ADDRESS, ignoreCase = true)
    val isUsdtTron = startsWith("tron") &&
        address == WalletCurrency.USDT_TRON.address

    return when {
        isUsdtTon -> context.externalDrawableUrl(UIKitIcon.ic_ton)
        isUsdtTron -> context.externalDrawableUrl(UIKitIcon.ic_tron)
        else -> null
    }
}