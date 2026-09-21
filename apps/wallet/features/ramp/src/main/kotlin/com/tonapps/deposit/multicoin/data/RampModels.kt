package com.tonapps.deposit.multicoin.data

import com.tonapps.wallet.data.multichain.asset.AssetEntity
import io.exchangeapi.models.ExchangeLimits
import io.exchangeapi.models.ExchangeMerchantSlug
import io.exchangeapi.models.ExchangePaymentMethodType
import io.exchangeapi.models.OfframpAsset
import io.exchangeapi.models.OfframpAssetDetail
import io.exchangeapi.models.OfframpConfiguration
import io.exchangeapi.models.OfframpPayoutMethod
import io.exchangeapi.models.OfframpProvider
import io.exchangeapi.models.OfframpQuoteResult
import io.exchangeapi.models.OfframpQuotes
import io.exchangeapi.models.OnrampAsset
import io.exchangeapi.models.OnrampAssetDetail
import io.exchangeapi.models.OnrampConfiguration
import io.exchangeapi.models.OnrampPaymentMethod
import io.exchangeapi.models.OnrampProvider
import io.exchangeapi.models.OnrampQuoteResult
import io.exchangeapi.models.OnrampQuotes
import io.exchangeapi.models.RampFees
import io.exchangeapi.models.RampUnavailableReason

/**
 * Direction-agnostic ramp models shared by the multicoin onramp (deposit) and
 * offramp (withdraw to card) flows. The generated onramp/offramp types are
 * structurally identical, so we map both into these to keep a single UI path.
 */

data class RampConfiguration(
    val assets: List<RampAsset>,
    val nextCursor: String?,
)

data class RampAsset(
    /** Display data; the chain icon + badge are derived from [AssetEntity.value]. */
    val asset: AssetEntity,
    /** Provider-supplied network label shown as the cell subtitle. */
    val networkName: String?,
    /** Fiats this asset can be ramped with; not yet consumed, kept for fiat filtering. */
    val availableFiats: List<String>,
)

data class RampAssetDetail(
    val assetId: String,
    val symbol: String,
    val decimals: Int,
    val image: String?,
    val networkName: String?,
    val networkImage: String?,
    val methods: List<RampMethod>,
    val extraIdRequired: Boolean,
    val extraIdName: String?,
)

data class RampMethod(
    val type: ExchangePaymentMethodType,
    val name: String,
    val image: String,
    val providers: List<RampProvider>,
)

data class RampProvider(
    val merchant: ExchangeMerchantSlug,
    val fiat: String,
    val limits: ExchangeLimits?,
)

data class RampQuotes(
    val items: List<RampQuote>,
    val suggested: List<RampQuote>,
    val unavailableReason: RampUnavailableReason?,
)

data class RampQuote(
    val merchant: ExchangeMerchantSlug,
    val paymentMethod: ExchangePaymentMethodType,
    /** Fiat paid (onramp) / crypto paid (offramp). */
    val amountIn: String,
    /** Crypto received (onramp) / fiat received (offramp). */
    val amountOut: String,
    val rate: String,
    val fees: RampFees,
    val merchantTransactionId: String,
    val minAmount: String?,
    val maxAmount: String?,
)

// region onramp mappers

internal fun OnrampConfiguration.toRamp(): RampConfiguration = RampConfiguration(
    assets = assets.map { it.toRamp() },
    nextCursor = nextCursor,
)

internal fun OnrampAsset.toRamp(): RampAsset = RampAsset(
    asset = AssetEntity(
        id = assetId,
        name = networkName ?: symbol,
        symbol = symbol,
        decimals = decimals,
        imageUrl = image.orEmpty(),
    ),
    networkName = networkName,
    availableFiats = availableFiats,
)

internal fun OnrampAssetDetail.toRamp(): RampAssetDetail = RampAssetDetail(
    assetId = assetId,
    symbol = symbol,
    decimals = decimals,
    image = image,
    networkName = networkName,
    networkImage = networkImage,
    methods = paymentMethods.map { it.toRamp() },
    extraIdRequired = extraIdRequired,
    extraIdName = extraIdName,
)

internal fun OnrampPaymentMethod.toRamp(): RampMethod = RampMethod(
    type = type,
    name = name,
    image = image,
    providers = providers.map { it.toRamp() },
)

internal fun OnrampProvider.toRamp(): RampProvider = RampProvider(
    merchant = merchant,
    fiat = fiat,
    limits = limits,
)

internal fun OnrampQuotes.toRamp(): RampQuotes = RampQuotes(
    items = items.map { it.toRamp() },
    suggested = suggested.map { it.toRamp() },
    unavailableReason = unavailableReason,
)

internal fun OnrampQuoteResult.toRamp(): RampQuote = RampQuote(
    merchant = merchant,
    paymentMethod = paymentMethod,
    amountIn = amountIn,
    amountOut = amountOut,
    rate = rate,
    fees = fees,
    merchantTransactionId = merchantTransactionId,
    minAmount = minAmount,
    maxAmount = maxAmount,
)

// endregion

// region offramp mappers

internal fun OfframpConfiguration.toRamp(): RampConfiguration = RampConfiguration(
    assets = assets.map { it.toRamp() },
    nextCursor = nextCursor,
)

internal fun OfframpAsset.toRamp(): RampAsset = RampAsset(
    asset = AssetEntity(
        id = assetId,
        name = networkName ?: symbol,
        symbol = symbol,
        decimals = decimals,
        imageUrl = image.orEmpty(),
    ),
    networkName = networkName,
    availableFiats = availableFiats,
)

internal fun OfframpAssetDetail.toRamp(): RampAssetDetail = RampAssetDetail(
    assetId = assetId,
    symbol = symbol,
    decimals = decimals,
    image = image,
    networkName = networkName,
    networkImage = networkImage,
    methods = payoutMethods.map { it.toRamp() },
    extraIdRequired = extraIdRequired,
    extraIdName = extraIdName,
)

internal fun OfframpPayoutMethod.toRamp(): RampMethod = RampMethod(
    type = type,
    name = name,
    image = image,
    providers = providers.map { it.toRamp() },
)

internal fun OfframpProvider.toRamp(): RampProvider = RampProvider(
    merchant = merchant,
    fiat = fiat,
    limits = limits,
)

internal fun OfframpQuotes.toRamp(): RampQuotes = RampQuotes(
    items = items.map { it.toRamp() },
    suggested = suggested.map { it.toRamp() },
    unavailableReason = unavailableReason,
)

internal fun OfframpQuoteResult.toRamp(): RampQuote = RampQuote(
    merchant = merchant,
    paymentMethod = payoutMethod,
    amountIn = amountIn,
    amountOut = amountOut,
    rate = rate,
    fees = fees,
    merchantTransactionId = merchantTransactionId,
    minAmount = minAmount,
    maxAmount = maxAmount,
)

// endregion
