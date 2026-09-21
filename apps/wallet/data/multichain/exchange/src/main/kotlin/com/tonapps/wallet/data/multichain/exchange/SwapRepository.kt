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
import com.tonapps.wallet.data.settings.SettingsRepository
import io.exchangeapi.models.CreateCrossSwapQuoteRequest
import io.exchangeapi.models.CrossSwapAggregator
import io.exchangeapi.models.CrossSwapAsset
import io.exchangeapi.models.CrossSwapCalldataPayloadType
import io.exchangeapi.models.CrossSwapExactType
import io.exchangeapi.models.CrossSwapPayload
import io.exchangeapi.models.CrossSwapPayloadKind
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext

data class SwapRoute(
    val main: SwapPayloadInfo.Data,
    val approval: SwapPayloadInfo.Approve?,
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
) {

    companion object {
        private val TON_MAX_RESERVE = BigDecimal.fromFloat(1f)

        private const val RATE_SCALE = 8
        private val RATE_DIVISION_MODE = DecimalMode(
            decimalPrecision = 30L,
            roundingMode = RoundingMode.ROUND_HALF_CEILING,
            scale = RATE_SCALE.toLong(),
        )
    }

    private interface CacheKeys : CacheKey {
        object Config : CacheKeys
    }

    private val configCache = TimedCacheMemory<CacheKeys>()

    val swapCompleted: SharedFlow<Unit> field = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun notifySwapCompleted() {
        swapCompleted.tryEmit(Unit)
    }

    fun getMaxReserve(asset: Asset): BaseUnit {
        if (asset !is Asset.Coin) {
            return asset.decimals.toBaseUnit(BigDecimal.ZERO)
        }

        val reserve = when (asset.chain) {
            is Chain.Ton -> TON_MAX_RESERVE
            else -> BigDecimal.ZERO
        }

        return asset.decimals.toBaseUnit(reserve)
    }

    // TODO add cache between confirm/swap
    suspend fun getConfig(
        walletId: String,
        fromAssetId: String? = null,
        toAssetId: String? = null,
    ): SwapConfig {
        return fetchConfig(walletId, fromAssetId, toAssetId)
    }

    suspend fun getSlippage(walletId: String, chain: Chain): SwapSlippage {
        return loadConfig(walletId).slippage(chain)
    }

    private suspend fun loadConfig(walletId: String): SwapConfig {
        return configCache.getOrLoad(CacheKeys.Config, payload = walletId) {
            fetchConfig(walletId)
        }
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

    suspend fun fetchQuote(
        sell: AccountWithDetails,
        buy: AccountWithDetails,
        amount: BigInteger,
        slippageBps: Int? = null,
        direction: SwapQuoteDirection = SwapQuoteDirection.ExactInput,
    ): SwapQuote? {
        if (!amount.isPositive) {
            return null
        }

        val exactOutput = direction == SwapQuoteDirection.ExactOutput
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
            exactType = when (direction) {
                SwapQuoteDirection.ExactInput -> CrossSwapExactType.exact_input
                SwapQuoteDirection.ExactOutput -> CrossSwapExactType.exact_output
            },
            aggregators = when {
                exactOutput -> null
                WalletFeature.SwapKit.isEnabled -> listOf(CrossSwapAggregator.swapkit)
                else -> null
            }
        )

        val response = withContext(Async.Io) {
            api.exchange.swapV2.createCrossSwapQuote(
                createCrossSwapQuoteRequest = request,
                xWalletID = sell.data.walletId,
                F = settings.installId,
                isNew = !settings.hadWalletsOnMultichainRelease,
            )
        }
        val route = response.routes.firstOrNull() ?: return null

        val sourceBaseAmount = when (direction) {
            SwapQuoteDirection.ExactInput -> amount
            SwapQuoteDirection.ExactOutput -> route.sourceAmount?.let { BigInteger.parseString(it) } ?: return null
        }
        val buyBaseAmount = BigInteger.parseString(route.estimatedDestinationAmount)

        return SwapQuote(
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
            },
            providerTxId = route.providerRouteId,
        )
    }

    /**
     * Formats the human-readable exchange rate (e.g. "1 ETH ≈ 5.42 USDT") shared by the swap input
     * and confirm screens. Returns null when the sell amount is zero (rate is undefined).
     */
    fun formatRateLabel(quote: SwapQuote, sellAsset: Asset, buyAsset: Asset): String? {
        val sellDisplay = sellAsset.toDisplayUnit(quote.sourceBaseAmount)
        val buyDisplay = buyAsset.toDisplayUnit(quote.buyBaseAmount)
        if (sellDisplay.value.isZero()) return null

        val rate = buyDisplay.value.divide(sellDisplay.value, RATE_DIVISION_MODE)
        val rateFormatted = Formatter.formatAsset(
            value = DisplayUnit(rate, buyAsset.decimals),
            asset = buyAsset,
            scale = RATE_SCALE,
            approximate = true,
        )
        return "1 ${sellAsset.symbol} $rateFormatted"
    }

    suspend fun prepareRoute(quote: SwapQuote, sellChain: Chain, walletId: String): SwapRoute {
        // "1-step" providers already returned the signing payloads inline on the quote route, so
        // we can build the route without the extra /prepare round-trip. Everyone else needs it.
        val payloads = quote.payloads?.takeIf { it.isNotEmpty() }
            ?: withContext(Async.Io) {
                api.exchange.swapV2.prepareCrossSwapRoute(
                    routeId = quote.routeId,
                    xWalletID = walletId,
                    F = settings.installId,
                    isNew = !settings.hadWalletsOnMultichainRelease,
                ).payloads
            }

        return payloads.toSwapRoute(sellChain, quote.provider)
    }

    private fun List<CrossSwapPayload>.toSwapRoute(sellChain: Chain, provider: SwapQuote.Provider): SwapRoute {
        val mainPayload = firstOrNull { it.kind == CrossSwapPayloadKind.main }
            ?: throw IllegalStateException("No swap payload")

        val main = toSwapInfo(
            payload = mainPayload.toSwapPayloadInfo(sellChain, provider),
            calldataType = mainPayload.requireCalldataType(),
        )

        val approval = firstOrNull { it.kind == CrossSwapPayloadKind.approval }
            ?.let {
                toSwapApprovalInfo(
                    payload = it.toSwapPayloadInfo(sellChain, provider),
                    calldataType = it.requireCalldataType(),
                )
            }

        return SwapRoute(main = main, approval = approval)
    }

    private fun CrossSwapPayload.requireCalldataType(): CrossSwapCalldataPayloadType =
        calldataPayloadType ?: throw IllegalStateException("CrossSwapCalldataPayloadType not found")

    private fun CrossSwapPayload.toSwapPayloadInfo(chain: Chain, provider: SwapQuote.Provider): SwapPayload {
        val provider = when (provider) {
            SwapQuote.Provider.SwapXyz -> SwapPayload.Provider.SwapXyz
            SwapQuote.Provider.SwapKit -> SwapPayload.Provider.SwapKit
            SwapQuote.Provider.Omniston -> SwapPayload.Provider.SwapKit
        }

        val type = when (requireCalldataType()) {
            CrossSwapCalldataPayloadType.exact -> SwapPayload.Type.Exact
            CrossSwapCalldataPayloadType.flex -> SwapPayload.Type.Flex
        }

        return SwapPayload.fromQuote(provider, type, chain, payload, humanSummary.depositAddress, humanSummary.spendAmount)
    }

    private fun toSwapInfo(
        payload: SwapPayload,
        calldataType: CrossSwapCalldataPayloadType,
    ): SwapPayloadInfo.Data {
        return payload.run {
            SwapPayloadInfo.Data(
                to = to,
                amount = amount,
                data = data,
                calldataType = calldataType,
                mode = mode,
                fee = fee,
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
                fee = fee ?: throw IllegalStateException("approval payload should have fee"),
            )
        }
    }
}
