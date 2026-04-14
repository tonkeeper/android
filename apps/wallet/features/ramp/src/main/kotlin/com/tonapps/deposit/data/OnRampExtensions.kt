package com.tonapps.deposit.data

import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.deposit.screens.network.CryptoNetworkInfo
import io.exchangeapi.models.ExchangeLayout
import io.exchangeapi.models.ExchangeLayoutAsset
import io.exchangeapi.models.ExchangeLayoutCryptoMethod
import io.exchangeapi.models.ExchangeLayoutItemType


/**
 * Flattens all assets from all layout items into a single list.
 */
val ExchangeLayout.allAssets: List<ExchangeLayoutAsset>
    get() = items.flatMap { it.assets.orEmpty() }

/**
 * Returns assets from items of the given type only.
 */
fun ExchangeLayout.assetsOfType(type: ExchangeLayoutItemType): List<ExchangeLayoutAsset> =
    items.filter { it.type == type }.flatMap { it.assets.orEmpty() }

fun ExchangeLayoutAsset.toWalletCurrency(): WalletCurrency {
    return buildWalletCurrency(symbol, networkName, network, address, decimals, image, stablecoin)
}

fun ExchangeLayoutCryptoMethod.toWalletCurrency(): WalletCurrency {
    return buildWalletCurrency(symbol, networkName, network,"", decimals, image, stablecoin)
}

fun ExchangeLayoutCryptoMethod.toCryptoNetworkInfo(): CryptoNetworkInfo {
    return CryptoNetworkInfo(
        currency = toWalletCurrency(),
        networkImage = networkImage,
        fee = fee,
        minAmount = minAmount,
        providerMinAmount = providers
            .mapNotNull { it.limits?.min?.toBigDecimalOrNull() }
            .filter { it > java.math.BigDecimal.ZERO }
            .minOrNull()
            ?.toPlainString(),
        providerMaxAmount = providers
            .mapNotNull { it.limits?.max?.toBigDecimalOrNull() }
            .filter { it > java.math.BigDecimal.ZERO }
            .maxOrNull()
            ?.toPlainString(),
    )
}

private fun buildWalletCurrency(
    symbol: String,
    networkName: String,
    network: String,
    address: String?,
    decimals: Int,
    imageUrl: String,
    isStablecoin: Boolean,
): WalletCurrency {
    val chain = networkToChain(network, address, decimals)
    return WalletCurrency(
        code = symbol,
        title = networkName,
        chain = chain,
        iconUrl = imageUrl,
        isToken = network != "NATIVE",
        isStablecoin = isStablecoin,
    )
}

private fun networkToChain(network: String, address: String?, decimals: Int): WalletCurrency.Chain {
    return when (network.lowercase()) {
        "native", "jetton" -> if (address != null) WalletCurrency.Chain.TON(
            address,
            decimals
        ) else WalletCurrency.Chain.TON(decimals = decimals)

        "trc-20" -> if (address != null) WalletCurrency.Chain.TRON(
            address,
            decimals
        ) else WalletCurrency.Chain.TRON(decimals = decimals)

        "erc-20" -> if (address != null) WalletCurrency.Chain.ETHEREUM(
            address,
            decimals
        ) else WalletCurrency.Chain.ETHEREUM(decimals = decimals)

        "bep-20" -> if (address != null) WalletCurrency.Chain.BNB(
            address,
            decimals
        ) else WalletCurrency.Chain.BNB(decimals = decimals)

        "spl" -> if (address != null) WalletCurrency.Chain.Solana(
            address,
            decimals
        ) else WalletCurrency.Chain.Solana(decimals = decimals)

        else -> WalletCurrency.Chain.Unknown(
            type = network,
            address = address ?: network,
            decimals = decimals
        )
    }
}

fun ExchangeLayout.cryptoAssets(): List<WalletCurrency> {
    return (assetsOfType(ExchangeLayoutItemType.crypto) + assetsOfType(ExchangeLayoutItemType.stablecoin))
        .map { it.toWalletCurrency() }
}

fun ExchangeLayout.pairedCryptoAssets(symbol: String, network: String?): List<WalletCurrency> {
    val cryptoAssets = assetsOfType(ExchangeLayoutItemType.crypto) +
            assetsOfType(ExchangeLayoutItemType.stablecoin)
    val asset = cryptoAssets.find {
        it.symbol.equals(symbol, ignoreCase = true)
                && it.network.equals(network, ignoreCase = true)
    } ?: return emptyList()

    return asset.cryptoMethods
        .map { it.toWalletCurrency() }
}

fun ExchangeLayout.pairedCryptoNetworkInfos(symbol: String, network: String?): List<CryptoNetworkInfo> {
    val cryptoAssets = assetsOfType(ExchangeLayoutItemType.crypto) +
            assetsOfType(ExchangeLayoutItemType.stablecoin)
    val asset = cryptoAssets.find {
        it.symbol.equals(symbol, ignoreCase = true)
                && it.network.equals(network, ignoreCase = true)
    } ?: return emptyList()

    return asset.cryptoMethods
        .map { it.toCryptoNetworkInfo() }
}

fun ExchangeLayout.resolveAssetFromDeeplink(
    token: String?,
    network: String?,
): WalletCurrency? {
    if (token == null) return null

    // Try by symbol + network
    val bySymbol = allAssets.find {
        it.symbol.equals(token, ignoreCase = true) &&
                (network == null || it.network.equals(network, ignoreCase = true))
    }
    if (bySymbol != null) return bySymbol.toWalletCurrency()

    // Fallback to well-known tokens
    return resolveWellKnownToken(token, network)
}

private fun resolveWellKnownToken(token: String, network: String?): WalletCurrency? {
    return when {
        token.equals("TON", ignoreCase = true) -> WalletCurrency.TON
        token.equals("USDT", ignoreCase = true) -> when {
            network?.contains("trc", ignoreCase = true) == true ||
                    network?.contains("tron", ignoreCase = true) == true -> WalletCurrency.USDT_TRON

            else -> WalletCurrency.USDT_TON
        }

        else -> null
    }
}

fun ExchangeLayout.cashAssets(): List<WalletCurrency> {
    return assetsOfType(ExchangeLayoutItemType.fiat)
        .map { it.toWalletCurrency() }
}

fun ExchangeLayout.stablecoinAssets(): List<WalletCurrency> {
    return assetsOfType(ExchangeLayoutItemType.stablecoin)
        .flatMap { it.cryptoMethods }
        .distinctBy { it.symbol.uppercase() }
        .map { it.toWalletCurrency() }
}

fun ExchangeLayout.stablecoinRootAssets(): List<WalletCurrency> {
    return assetsOfType(ExchangeLayoutItemType.stablecoin)
        .map { it.toWalletCurrency() }
}


fun ExchangeLayout.resolveAssets(filter: AssetFilter = AssetFilter.All): List<WalletCurrency> {
    return when (filter) {
        AssetFilter.All -> cryptoAssets()
        AssetFilter.Cash -> cashAssets()
        AssetFilter.Stablecoin -> stablecoinAssets()
        AssetFilter.StablecoinRoot -> stablecoinRootAssets()
    }
}

@kotlinx.serialization.Serializable
enum class AssetFilter {
    All, Cash, Stablecoin, StablecoinRoot
}

