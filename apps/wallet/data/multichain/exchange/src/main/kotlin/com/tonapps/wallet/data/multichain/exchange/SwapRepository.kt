package com.tonapps.wallet.data.multichain.exchange

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.async.Async
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.num.BaseUnit
import com.tonapps.chainkit.core.chain.model.num.DisplayUnit
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.chainkit.core.chain.model.num.toDisplayUnit
import com.tonapps.chainkit.plugin.swap.SwapPayload
import com.tonapps.core.flags.WalletFeature
import com.tonapps.extensions.CacheKey
import com.tonapps.extensions.TimedCacheMemory
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.fees.NetworkFees
import com.tonapps.wallet.data.multichain.fees.NetworkFeesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import io.exchangeapi.models.CreateCrossSwapQuoteRequest
import io.exchangeapi.models.CrossSwapAggregator
import io.exchangeapi.models.CrossSwapAsset
import io.exchangeapi.models.CrossSwapCalldataPayloadType
import io.exchangeapi.models.CrossSwapExactType
import io.exchangeapi.models.CrossSwapPayload
import io.exchangeapi.models.CrossSwapPayloadKind
import io.exchangeapi.models.CrossSwapProviderError
import io.exchangeapi.models.CrossSwapProviderErrorCode
import io.exchangeapi.models.CrossSwapQuote
import io.exchangeapi.models.CrossSwapQuoteRequest
import io.exchangeapi.models.CrossSwapQuoteV3
import io.exchangeapi.models.CrossSwapTimeEstimate
import io.infrastructure.ClientError
import io.infrastructure.ClientException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.OffsetDateTime
import kotlin.time.Duration.Companion.minutes

data class SwapRoute(
    val mainMessages: List<SwapPayloadInfo.Data>,
    val approval: SwapPayloadInfo.Approve?,
    val providerRouteId: String?,
    val buyBaseAmount: BigInteger?,
    val minimumBuyBaseAmount: BigInteger?,
)

// A quoted route as both quote versions answer it; only v2 carries payloads inline.
private class QuotedRoute(
    val routeId: String,
    val aggregator: CrossSwapAggregator,
    val sourceAmount: String?,
    val estimatedDestinationAmount: String,
    val minimumDestinationAmount: String,
    val totalSlippageBps: Int?,
    val valueDifferenceBps: Int?,
    val providerRouteId: String?,
    val estimatedTime: CrossSwapTimeEstimate?,
    val dateExpire: OffsetDateTime,
    val payloads: List<CrossSwapPayload>?,
)

private class QuotedRoutes(
    val routes: List<QuotedRoute>,
    val providerErrors: List<CrossSwapProviderError>,
)

private fun CrossSwapQuote.toQuotedRoutes() = QuotedRoutes(
    routes = routes.map { route ->
        QuotedRoute(
            routeId = route.routeId,
            aggregator = route.aggregator,
            sourceAmount = route.sourceAmount,
            estimatedDestinationAmount = route.estimatedDestinationAmount,
            minimumDestinationAmount = route.minimumDestinationAmount,
            totalSlippageBps = route.totalSlippageBps,
            valueDifferenceBps = route.valueDifferenceBps,
            providerRouteId = route.providerRouteId,
            estimatedTime = route.estimatedTime,
            dateExpire = route.dateExpire,
            payloads = route.payloads,
        )
    },
    providerErrors = providerErrors.orEmpty(),
)

private fun CrossSwapQuoteV3.toQuotedRoutes() = QuotedRoutes(
    routes = routes.map { route ->
        QuotedRoute(
            routeId = route.routeId,
            aggregator = route.aggregator,
            sourceAmount = route.sourceAmount,
            estimatedDestinationAmount = route.estimatedDestinationAmount,
            minimumDestinationAmount = route.minimumDestinationAmount,
            totalSlippageBps = route.totalSlippageBps,
            valueDifferenceBps = route.valueDifferenceBps,
            providerRouteId = route.providerRouteId,
            estimatedTime = route.estimatedTime,
            dateExpire = route.dateExpire,
            payloads = null,
        )
    },
    providerErrors = providerErrors.orEmpty(),
)

fun CrossSwapAsset.toAssetEntity(): AssetEntity = AssetEntity(
    id = assetId,
    name = name,
    symbol = symbol,
    decimals = decimals,
    imageUrl = image.orEmpty(),
)

class SwapRepository(
    private val api: API,
    private val settings: SettingsRepository,
    private val networkFeesRepository: NetworkFeesRepository,
) {

    private interface CacheKeys : CacheKey {
        data class Config(
            val fromAssetId: String?,
            val toAssetId: String?,
        ) : CacheKeys {
            override val ttl: Long = CONFIG_TTL
        }
    }

    private val configCache = TimedCacheMemory<CacheKeys>()

    val swapCompleted: SharedFlow<Boolean> field = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

    /** The last `/network-fees` response, for what [getMaxReserve] answers to recompute when it lands. */
    val networkFees: StateFlow<NetworkFees?>
        get() = networkFeesRepository.fees

    fun notifySwapCompleted(tracked: Boolean) {
        swapCompleted.tryEmit(tracked)
    }

    /** Loads `/network-fees` ahead of MAX, which reads only what is already loaded and never waits for it. */
    suspend fun loadNetworkFees() {
        networkFeesRepository.load()
    }

    // What MAX leaves of the balance for the swap's gas: a fixed 1 TON for the TON coin whatever `/network-fees`
    // says, else its safe fee where the response has one paid in this asset, else nothing. MAX, its percents
    // and the below-reserve toast all read it, so they agree.
    fun getMaxReserve(asset: Asset): BaseUnit {
        if (asset is Asset.Coin && asset.chain is Chain.Ton) {
            return asset.decimals.toBaseUnit(TON_MAX_RESERVE)
        }

        val safe = networkFeesRepository.fees.value?.swapReserve(asset.id)
        if (safe != null) {
            return asset.decimals.baseUnit(safe.toString())
        }

        return asset.decimals.toBaseUnit(BigDecimal.ZERO)
    }

    // The smallest amount a swap may sell: 1 TON for the TON coin, nothing for any other asset. A `/network-fees`
    // safe fee is what MAX leaves for gas, not a minimum: a typed amount below it still leaves the rest of the
    // balance for the gas.
    fun getMinimumAmount(asset: Asset): BaseUnit {
        if (asset is Asset.Coin && asset.chain is Chain.Ton) {
            return asset.decimals.toBaseUnit(TON_MAX_RESERVE)
        }

        return asset.decimals.toBaseUnit(BigDecimal.ZERO)
    }

    suspend fun getConfig(
        walletId: String,
        fromAssetId: String? = null,
        toAssetId: String? = null,
    ): SwapConfig {
        val from = fromAssetId?.takeIf { it.isNotBlank() }
        val to = toAssetId?.takeIf { it.isNotBlank() }

        return configCache.getOrLoad(
            key = CacheKeys.Config(fromAssetId = from, toAssetId = to),
            payload = walletId,
        ) {
            fetchConfig(walletId, from, to)
        }
    }

    suspend fun getSlippage(walletId: String, chain: Chain): SwapSlippage {
        return loadConfig(walletId).slippage(chain)
    }

    private suspend fun loadConfig(walletId: String): SwapConfig {
        return getConfig(walletId)
    }

    private suspend fun fetchConfig(
        walletId: String,
        fromAssetId: String? = null,
        toAssetId: String? = null,
    ): SwapConfig {
        val response = withContext(Async.Io) {
            api.exchange.swapV2.getCrossSwapConfig(
                walletId = walletId,
                fromAssetId = fromAssetId?.takeIf { it.isNotBlank() },
                toAssetId = toAssetId?.takeIf { it.isNotBlank() },
            )
        }

        return SwapConfig(
            defaultPair = SwapDefaultPair(
                source = response.swapPair.sourceAsset.toAssetEntity(),
                destination = response.swapPair.destinationAsset.toAssetEntity(),
            ),
            slippagePerChain = response.slippages.mapValues { (_, options) ->
                SwapSlippage(
                    options = options.optionsBps.map { bps ->
                        SwapSlippage.Option(bps = bps, formatted = formatSlippage(bps))
                    },
                    defaultBps = options.defaultBps,
                )
            },
        )
    }

    /**
     * [walletId] is the registered wallet the quote is asked for; a legacy wallet the backend never
     * registered asks without one.
     */
    suspend fun fetchQuote(
        sell: AccountWithDetails,
        buy: AccountWithDetails,
        amount: BigInteger,
        slippageBps: Int? = null,
        direction: SwapQuoteDirection = SwapQuoteDirection.ExactInput,
        walletId: String?,
        // A confirm screen quotes again on the API its route came from; null asks the one the wallet swaps on.
        genericPayload: Boolean? = null,
    ): SwapQuoteResult {
        if (!amount.isPositive) {
            return SwapQuoteResult.NoRoute(SwapNoRouteReason.Unknown)
        }

        val exactOutput = direction == SwapQuoteDirection.ExactOutput
        val exactType = when (direction) {
            SwapQuoteDirection.ExactInput -> CrossSwapExactType.exact_input
            SwapQuoteDirection.ExactOutput -> CrossSwapExactType.exact_output
        }
        val aggregators = when {
            exactOutput -> null
            WalletFeature.SwapKit.isEnabled -> listOf(CrossSwapAggregator.swapkit)
            else -> null
        }
        val onV3 = walletId != null && (genericPayload ?: usesGenericPayload(walletId))

        val quoted = withContext(Async.Io) {
            if (onV3) {
                val request = CrossSwapQuoteRequest(
                    sourceAsset = sell.asset.value.id,
                    sourceAmount = amount.toString().takeUnless { exactOutput },
                    destinationAsset = buy.asset.value.id,
                    destinationAmount = amount.toString().takeIf { exactOutput },
                    senderAddress = sell.address.display,
                    recipientAddress = buy.address.display,
                    slippageBps = slippageBps,
                    returnDepositAddress = true,
                    exactType = exactType,
                    aggregators = aggregators,
                )
                api.exchange.swapV2.createCrossSwapQuoteV3(
                    crossSwapQuoteRequest = request,
                    xWalletID = walletId,
                    F = settings.installId,
                ).toQuotedRoutes()
            } else {
                val request = CreateCrossSwapQuoteRequest(
                    sourceAsset = sell.asset.value.id,
                    sourceAmount = amount.toString().takeUnless { exactOutput },
                    destinationAsset = buy.asset.value.id,
                    destinationAmount = amount.toString().takeIf { exactOutput },
                    senderAddress = sell.address.display,
                    recipientAddress = buy.address.display,
                    slippageBps = slippageBps,
                    returnDepositAddress = true,
                    includePayload = true,
                    exactType = exactType,
                    aggregators = aggregators,
                )
                api.exchange.swapV2.createCrossSwapQuote(
                    createCrossSwapQuoteRequest = request,
                    xWalletID = walletId,
                    F = settings.installId,
                ).toQuotedRoutes()
            }
        }

        val providerErrors = quoted.providerErrors
        val route = quoted.routes.firstOrNull { !it.isScreened(providerErrors) }
            ?: return SwapQuoteResult.NoRoute(noRouteReason(providerErrors))

        val sourceBaseAmount = when (direction) {
            SwapQuoteDirection.ExactInput -> amount
            SwapQuoteDirection.ExactOutput -> route.sourceAmount?.let { BigInteger.parseString(it) }
                ?: return SwapQuoteResult.NoRoute(SwapNoRouteReason.Unknown)
        }
        val buyBaseAmount = BigInteger.parseString(route.estimatedDestinationAmount)

        val quote = SwapQuote(
            routeId = route.routeId,
            sourceBaseAmount = sourceBaseAmount,
            buyBaseAmount = buyBaseAmount,
            minimumBuyBaseAmount = BigInteger.parseString(route.minimumDestinationAmount),
            slippageBps = route.totalSlippageBps,
            priceImpactBps = route.valueDifferenceBps,
            payloads = route.payloads,
            provider = when (route.aggregator) {
                CrossSwapAggregator.swapsxyz -> SwapQuote.Provider.SwapXyz
                CrossSwapAggregator.swapkit -> SwapQuote.Provider.SwapKit
                CrossSwapAggregator.omniston -> SwapQuote.Provider.Omniston
                CrossSwapAggregator.relay -> throw IllegalStateException("Relay routes are not swaps")
            },
            providerTxId = route.providerRouteId,
            estimatedSeconds = route.estimatedTime?.totalSeconds,
            expiresAtMillis = route.dateExpire.toInstant().toEpochMilli(),
            genericPayload = onV3,
        )
        return SwapQuoteResult.Found(quote)
    }

    // A legacy wallet the backend never registered stays on v2.
    private fun usesGenericPayload(walletId: String?): Boolean {
        return walletId != null && WalletFeature.SwapV3.isEnabled
    }

    private fun QuotedRoute.isScreened(errors: List<CrossSwapProviderError>): Boolean {
        return payloads.isNullOrEmpty() && errors.any { error ->
            error.aggregator == aggregator && error.code == CrossSwapProviderErrorCode.wallet_screened
        }
    }

    private fun noRouteReason(errors: List<CrossSwapProviderError>): SwapNoRouteReason {
        val codes = errors.mapTo(mutableSetOf()) { it.code }
        return NO_ROUTE_REASON_PRIORITY.firstOrNull { it in codes }
            ?.toNoRouteReason()
            ?: SwapNoRouteReason.Unknown
    }

    private fun CrossSwapProviderErrorCode.toNoRouteReason(): SwapNoRouteReason = when (this) {
        CrossSwapProviderErrorCode.country_blocked -> SwapNoRouteReason.RegionBlocked
        CrossSwapProviderErrorCode.min_amount_not_met -> SwapNoRouteReason.AmountTooSmall
        CrossSwapProviderErrorCode.no_route -> SwapNoRouteReason.PairUnavailable
        CrossSwapProviderErrorCode.asset_not_supported -> SwapNoRouteReason.AssetUnavailable
        CrossSwapProviderErrorCode.provider_unavailable -> SwapNoRouteReason.ProviderUnavailable
        CrossSwapProviderErrorCode.wallet_screened -> SwapNoRouteReason.WalletScreened
        CrossSwapProviderErrorCode.insufficient_balance,
        CrossSwapProviderErrorCode.provider_error -> SwapNoRouteReason.Unknown
    }

    /**
     * Formats the human-readable exchange rate (e.g. "1 ETH ≈ 5.42 USDT") shared by the swap input
     * and confirm screens. Returns null when the sell amount is zero (rate is undefined).
     */
    fun formatRateLabel(quote: SwapQuote, sellAsset: Asset, buyAsset: Asset): String? {
        val sellDisplay = sellAsset.toDisplayUnit(quote.sourceBaseAmount)
        val buyDisplay = buyAsset.toDisplayUnit(quote.buyBaseAmount)
        if (sellDisplay.value.isZero()) {
            return null
        }

        val rate = buyDisplay.value.divide(sellDisplay.value, RATE_DIVISION_MODE)
        val rateFormatted = Formatter.formatAsset(
            value = DisplayUnit(rate, buyAsset.decimals),
            asset = buyAsset,
            scale = RATE_SCALE,
            approximate = true,
        )
        val label = "1 ${sellAsset.symbol} $rateFormatted"
        return if (Formatter.defaultOptions.isolateLtr) {
            "$LEFT_TO_RIGHT_ISOLATE$label$POP_DIRECTIONAL_ISOLATE"
        } else {
            label
        }
    }

    /** A `/v3` [quote] prepared into the operation the wallet signs. Only a registered wallet swaps on `/v3`. */
    suspend fun prepareOperation(quote: SwapQuote, walletId: String): PreparedSwapOperation {
        val prepared = screened {
            api.exchange.swapV2.prepareCrossSwapRouteV3(
                routeId = quote.routeId,
                xWalletID = walletId,
                F = settings.installId,
            )
        }
        return PreparedSwapOperation.from(prepared)
    }

    /** A `/v2` [quote] prepared into the route the transaction confirm signs, on [sellChain]. */
    suspend fun prepareRoute(quote: SwapQuote, sellChain: Chain, walletId: String?): SwapRoute {
        check(!quote.genericPayload) { "A /v3 route is prepared into an operation" }

        // "1-step" providers already returned the signing payloads inline on the quote route, so
        // we can build the route without the extra /prepare round-trip. Everyone else needs it.
        val inlinePayloads = quote.payloads?.takeIf { it.isNotEmpty() }
        if (inlinePayloads != null) {
            return inlinePayloads.toSwapRoute(sellChain, quote.provider, quote.providerTxId)
        }

        val prepared = screened {
            api.exchange.swapV2.prepareCrossSwapRoute(
                routeId = quote.routeId,
                xWalletID = walletId,
                F = settings.installId,
            )
        }

        return prepared.payloads.toSwapRoute(
            sellChain = sellChain,
            provider = quote.provider,
            providerRouteId = prepared.providerRouteId ?: quote.providerTxId,
        )
    }

    // Prepare is where the aggregator screens the wallets: its refusal is final for the wallet.
    private suspend fun <T> screened(prepare: () -> T): T = withContext(Async.Io) {
        try {
            prepare()
        } catch (e: ClientException) {
            if (e.errorCode() == CrossSwapProviderErrorCode.wallet_screened.value) {
                throw SwapWalletScreenedException(e)
            }
            throw e
        }
    }

    private fun ClientException.errorCode(): String? {
        val body = (response as? ClientError<*>)?.body as? String ?: return null
        val json = try {
            Json.parseToJsonElement(body)
        } catch (_: SerializationException) {
            return null
        }
        return ((json as? JsonObject)?.get("code") as? JsonPrimitive)?.contentOrNull
    }

    private fun List<CrossSwapPayload>.toSwapRoute(
        sellChain: Chain,
        provider: SwapQuote.Provider,
        providerRouteId: String?,
    ): SwapRoute {
        val mainPayload = firstOrNull { it.kind == CrossSwapPayloadKind.main }
            ?: throw IllegalStateException("No swap payload")

        val mainMessages = mainPayload.splitMessages(sellChain).map { message ->
            toSwapInfo(
                payload = mainPayload.toSwapPayloadInfo(sellChain, provider, message),
                calldataType = mainPayload.requireCalldataType(),
                summaryMemo = mainPayload.humanSummary.memo,
            )
        }

        val approval = firstOrNull { it.kind == CrossSwapPayloadKind.approval }
            ?.let {
                toSwapApprovalInfo(
                    payload = it.toSwapPayloadInfo(sellChain, provider, it.payload),
                    calldataType = it.requireCalldataType(),
                )
            }

        return SwapRoute(
            mainMessages = mainMessages,
            approval = approval,
            providerRouteId = providerRouteId,
            buyBaseAmount = mainPayload.humanSummary.receiveAmount?.toBaseAmountOrNull(),
            minimumBuyBaseAmount = mainPayload.humanSummary.minimumReceiveAmount?.toBaseAmountOrNull(),
        )
    }

    private fun CrossSwapPayload.splitMessages(chain: Chain): List<String> {
        if (!chain.isTvm) {
            return listOf(payload)
        }
        val messages = try {
            Json.parseToJsonElement(payload) as? JsonArray
        } catch (_: SerializationException) {
            null
        }
        if (messages == null || messages.size < 2) {
            return listOf(payload)
        }
        return messages.map { JsonArray(listOf(it)).toString() }
    }

    private fun CrossSwapPayload.requireCalldataType(): CrossSwapCalldataPayloadType =
        calldataPayloadType ?: throw IllegalStateException("CrossSwapCalldataPayloadType not found")

    private fun String.toBaseAmountOrNull(): BigInteger? = try {
        BigInteger.parseString(this)
    } catch (_: NumberFormatException) {
        null
    }

    private fun CrossSwapPayload.toSwapPayloadInfo(
        chain: Chain,
        provider: SwapQuote.Provider,
        message: String,
    ): SwapPayload {
        val provider = when (provider) {
            SwapQuote.Provider.SwapXyz -> SwapPayload.Provider.SwapXyz
            SwapQuote.Provider.SwapKit -> SwapPayload.Provider.SwapKit
            SwapQuote.Provider.Omniston -> SwapPayload.Provider.SwapKit
        }

        val type = when (requireCalldataType()) {
            CrossSwapCalldataPayloadType.exact -> SwapPayload.Type.Exact
            CrossSwapCalldataPayloadType.flex -> SwapPayload.Type.Flex
        }

        return SwapPayload.fromQuote(provider, type, chain, message, humanSummary.depositAddress, humanSummary.spendAmount)
    }

    private fun toSwapInfo(
        payload: SwapPayload,
        calldataType: CrossSwapCalldataPayloadType,
        summaryMemo: String?,
    ): SwapPayloadInfo.Data {
        // An exact payload carries the memo inside: the summary only backs up a flex one.
        val memo = payload.memo ?: summaryMemo?.takeIf { it.isNotBlank() && calldataType == CrossSwapCalldataPayloadType.flex }
        return payload.run {
            SwapPayloadInfo.Data(
                to = to,
                amount = amount,
                data = data,
                calldataType = calldataType,
                mode = mode,
                fee = fee,
                memo = memo,
            )
        }
    }

    private fun toSwapApprovalInfo(
        payload: SwapPayload,
        calldataType: CrossSwapCalldataPayloadType,
    ): SwapPayloadInfo.Approve {
        return payload.run {
            SwapPayloadInfo.Approve(
                to = to,
                amount = amount,
                data = data ?: throw IllegalStateException("approval payload should have data"),
                calldataType = calldataType,
                mode = mode,
                fee = fee,
            )
        }
    }

    companion object {
        private val TON_MAX_RESERVE = BigDecimal.fromFloat(1f)

        private val CONFIG_TTL = 30.minutes.inWholeMilliseconds

        private const val RATE_SCALE = 8
        private val RATE_DIVISION_MODE = DecimalMode(
            decimalPrecision = 30L,
            roundingMode = RoundingMode.ROUND_HALF_CEILING,
            scale = RATE_SCALE.toLong(),
        )

        private const val LEFT_TO_RIGHT_ISOLATE = "\u2066"
        private const val POP_DIRECTIONAL_ISOLATE = "\u2069"

        private val NO_ROUTE_REASON_PRIORITY = listOf(
            CrossSwapProviderErrorCode.wallet_screened,
            CrossSwapProviderErrorCode.country_blocked,
            CrossSwapProviderErrorCode.min_amount_not_met,
            CrossSwapProviderErrorCode.no_route,
            CrossSwapProviderErrorCode.asset_not_supported,
            CrossSwapProviderErrorCode.provider_unavailable,
        )
    }
}
