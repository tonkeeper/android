package com.tonapps.wallet.data.multichain.asset

import androidx.compose.runtime.Stable
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.num.BaseUnit
import com.tonapps.chainkit.core.chain.model.num.FiatCurrency
import com.tonapps.chainkit.core.chain.model.num.FiatRate
import com.tonapps.extensions.fiatSymbol
import com.tonapps.extensions.lazyUnsafe

class AssetRateEntity(
    val price: String,
    val percentChange24h: String,
    val currencyCode: String,
) {
    val fiatSymbol by lazyUnsafe {
        currencyCode.fiatSymbol()
    }

    val currencySymbol: FiatCurrency by lazyUnsafe {
        FiatCurrency(fiatSymbol)
    }

    val currencyName: FiatCurrency by lazyUnsafe {
        FiatCurrency(currencyCode)
    }

    val valueWithName: FiatRate by lazyUnsafe {
        FiatRate(value = BigDecimal.parseString(price), currency = currencyName)
    }

    val value: FiatRate by lazyUnsafe {
        FiatRate(value = BigDecimal.parseString(price), currency = currencySymbol)
    }
}

class AssetMarketCapEntity(
    val marketCap: String,
    val currencyCode: String,
) {
    val currencySymbol: FiatCurrency by lazyUnsafe {
        FiatCurrency(currencyCode.fiatSymbol())
    }

    val currencyName: FiatCurrency by lazyUnsafe {
        FiatCurrency(currencyCode)
    }

    val valueWithName: FiatRate by lazyUnsafe {
        FiatRate(value = BigDecimal.parseString(marketCap), currency = currencyName)
    }

    val value: FiatRate by lazyUnsafe {
        FiatRate(value = BigDecimal.parseString(marketCap), currency = currencySymbol)
    }
}

@Stable
data class AssetEntity(
    val id: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val imageUrl: String,
    val verification: Verification = Verification.whitelist,
) {
    /**
     * Resolved asset, or `null` for ids that are not parseable fungible assets
     * (e.g. NFT ids the activity feed returns) instead of throwing.
     */
    val valueOrNull: Asset? by lazyUnsafe {
        Asset.fromString(
            assetId = id,
            name = name,
            symbol = symbol,
            decimals = decimals,
        )
    }

    val value: Asset by lazyUnsafe {
        valueOrNull ?: throw IllegalStateException("Incorrect asset $this")
    }

    val one: BaseUnit by lazyUnsafe { // TODO remove
        value.decimals.toBaseUnit(BigInteger.ONE)
    }

    enum class Verification {
        trusted, whitelist, none, blacklist;

        val isVerified: Boolean
            get() = this == trusted || this == whitelist
    }
}

data class AssetWithDetails(
    val asset: AssetEntity,
    val rate: AssetRateEntity?,
    val marketCap: AssetMarketCapEntity?,
    val volume: AssetMarketCapEntity? = null
)

const val PERP_ASSET_ID_PREFIX = "lighter/mainnet/market/"
