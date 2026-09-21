package com.tonapps.core.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.CoinType
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.chainkit.core.chain.model.account.TokenType
import com.tonapps.chainkit.core.chain.model.account.TokenType.Bep20
import com.tonapps.chainkit.core.chain.model.account.TokenType.Brc20
import com.tonapps.chainkit.core.chain.model.account.TokenType.Erc1155
import com.tonapps.chainkit.core.chain.model.account.TokenType.Erc20
import com.tonapps.chainkit.core.chain.model.account.TokenType.Erc721
import com.tonapps.chainkit.core.chain.model.account.TokenType.Jetton
import com.tonapps.chainkit.core.chain.model.account.TokenType.SPL
import com.tonapps.chainkit.core.chain.model.account.TokenType.Trc10
import com.tonapps.chainkit.core.chain.model.account.TokenType.Trc20
import com.tonapps.chainkit.core.chain.model.num.BaseUnit
import com.tonapps.chainkit.core.chain.model.num.CoinValue
import com.tonapps.chainkit.core.chain.model.num.FiatRate
import com.tonapps.chainkit.core.chain.model.num.toBaseUnit
import com.tonapps.core.extensions.externalDrawableUrl
import com.tonapps.extensions.AppContext
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import ui.theme.UIKit

@Composable
fun CoinType.painterResource(): Painter {
    return painterResource(coinImageResource())
}

@Composable
fun Chain.painterResource(): Painter {
    return painterResource(coin.chainImageResource())
}

@Composable
fun CoinType.assetImageUrl(): String {
    val context = LocalContext.current
    return imageResourceUrl(context)
}

@Composable
fun CoinType.chainImageUrl(): String {
    val context = LocalContext.current
    return chainImageResourceUrl(context)
}

@Composable
fun Asset.coinShortName(): String? {
    return if (this is Asset.Token || coin.primaryCoin != null) {
        chain.shortName
    } else {
        null
    }
}

fun AssetEntity.isNativeTon(): Boolean {
    val asset = valueOrNull ?: return false
    return asset is Asset.Coin && asset.coin == CoinType.Gram
}

@Composable
fun AssetEntity.chainLabel(): String? {
    return if (value is Asset.Token || value.coin.primaryCoin != null) {
        value.chain.name
    } else {
        null
    }
}

@Composable
fun Address.walletTypeLabel(): String? {
    return when (type) {
        Address.Type.TonV4R2 -> "V4R2"
        Address.Type.TonV5R1 -> "W5"
        Address.Type.BtcSegwit -> "Segwit"
        Address.Type.BtcSTaproot -> "Taproot"
        Address.Type.Default -> null
    }
}

@Composable
fun AssetEntity.assetImageUrl(): String {
    val context = LocalContext.current
    return if (value is Asset.Coin) {
        (value.coin.primaryCoin ?: value.coin).imageResourceUrl(context)
    } else {
        imageUrl.ifBlank { value.coin.imageResourceUrl(context) }
    }
}

@Composable
fun AssetEntity.tokenChainImageUrl(): String? {
    return value.tokenChainImageUrl()
}

@Composable
fun Asset.tokenChainImageUrl(): String? {
    val context = LocalContext.current
    return when (this) {
        is Asset.Coin -> when (coin.primaryCoin != null) {
            true -> coin.imageResourceUrl(context)
            else -> null
        }

        is Asset.Token -> coin.chainImageResourceUrl(context)
    }
}

fun CoinType.chainImageResourceUrl(context: Context = AppContext.value): String {
    return context.externalDrawableUrl(chainImageResource())
}

fun CoinType.imageResourceUrl(context: Context = AppContext.value): String {
    return context.externalDrawableUrl(coinImageResource())
}

fun Chain.toAssetEntity(): AssetEntity = AssetEntity(
    id = coinAssetId,
    name = coin.name,
    symbol = coin.symbol,
    decimals = coin.decimals.value,
    imageUrl = coin.imageResourceUrl(),
)

// TODO add assetId to coinType
//fun CoinType.toAssetEntity(): AssetEntity = AssetEntity(
//    id = Chain.,
//    name = name,
//    symbol = symbol,
//    decimals = decimals.value,
//    imageUrl = imageResourceUrl(),
//)

@Composable
fun Asset.tokenDisplayName(): String? {
    val token = this as? Asset.Token ?: return null

    return when (token.type) {
        Jetton -> "Jetton"
        Trc10 -> "TRC10"
        Trc20 -> "TRC20"
        Erc20 -> "ERC20"
        Erc721 -> "ERC721"
        Erc1155 -> "ERC1155"
        Bep20 -> "BEP20"
        SPL -> "SPL"
        Brc20 -> "BRC10"
    }
}

val Chain.mainTokenType: TokenType
    get() = when (network.type) {
        Network.Type.Ethereum,
        Network.Type.Arbitrum,
        Network.Type.Base -> Erc20
        Network.Type.Smartchain -> Bep20
        Network.Type.Tron -> Trc20
        Network.Type.Ton -> Jetton
        Network.Type.Bitcoin -> Brc20
    }

fun Chain.tokenAssetId(contract: String): String {
    return "${network.type.id}/${network.mode.id}/${mainTokenType.id}/${contract.lowercase()}"
}

private fun CoinType.chainImageResource(): Int {
    return when (this) {
        CoinType.Arbitrum -> UIKitIcon.ic_arb
        CoinType.Base -> UIKitIcon.ic_base
        CoinType.Bitcoin -> UIKitIcon.ic_btc
        CoinType.Ethereum -> UIKitIcon.ic_eth
        CoinType.Smartchain -> UIKitIcon.ic_bsc
        CoinType.Gram -> UIKitIcon.ic_ton
        CoinType.Tron -> UIKitIcon.ic_tron
    }
}

private fun CoinType.coinImageResource(): Int {
    return when (this) {
        CoinType.Arbitrum -> UIKitIcon.ic_arb
        CoinType.Base -> UIKitIcon.ic_base
        CoinType.Bitcoin -> UIKitIcon.ic_btc
        CoinType.Ethereum -> UIKitIcon.ic_eth
        CoinType.Smartchain -> UIKitIcon.ic_bsc
        CoinType.Gram -> UIKitIcon.ic_gram_with_bg
        CoinType.Tron -> UIKitIcon.ic_tron
    }
}

@Composable
fun String.percentDiffColor(): Color {
    return when {
        startsWith("-") -> UIKit.colorScheme.accent.red
        startsWith("+") -> UIKit.colorScheme.accent.green
        else -> UIKit.colorScheme.text.secondary
    }
}

private val FIAT_TO_TOKEN_MODE = DecimalMode(
    decimalPrecision = 30L,
    roundingMode = RoundingMode.ROUND_HALF_CEILING,
    scale = 18L,
)

/**
 * Convert a plain fiat [amount] string into the token's [BaseUnit], using [rate]
 * (fiat per one token) and the token's [decimals].
 */
fun Asset.fiatToToken(amount: String, rate: FiatRate): BaseUnit {
    val token = BigDecimal.parseString(amount)
        .divide(rate.value, FIAT_TO_TOKEN_MODE)
    return toBaseUnit(token)
}

/**
 * Plain, ungrouped amount string rounded to [maxDecimals] with trailing zeros stripped —
 * suitable for an input field (no thousands separators).
 */
fun CoinValue.amountText(maxDecimals: Int): String {
    val rounded = toDisplayValue()
        .roundToDigitPositionAfterDecimalPoint(maxDecimals.toLong(), RoundingMode.ROUND_HALF_CEILING)
    val plain = rounded.toStringExpanded()
    return if (plain.contains('.')) {
        plain.trimEnd('0').trimEnd('.')
    } else {
        plain
    }
}
