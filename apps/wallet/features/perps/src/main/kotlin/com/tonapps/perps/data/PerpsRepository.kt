package com.tonapps.perps.data

import com.tonapps.async.Async
import com.tonapps.core.helper.EnvironmentHelper
import com.tonapps.log.L
import com.tonapps.mvi.graph.KResult
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.multichain.asset.PERP_ASSET_ID_PREFIX
import io.infrastructure.ClientException
import io.perpsapi.models.Balance
import io.perpsapi.models.OpenPosition
import io.perpsapi.models.OpenPositionDetail
import io.perpsapi.models.Order
import io.perpsapi.models.OrderCategory
import io.perpsapi.models.OrderSide
import io.perpsapi.models.OrderStatus
import io.perpsapi.models.OrderType
import io.perpsapi.models.Position
import io.perpsapi.models.PositionAutoCloseLeg
import io.perpsapi.models.PositionSide
import io.perpsapi.models.TradingFlags
import io.tradingapi.models.AssetsFilter
import io.tradingapi.models.AssetsOrder
import io.tradingapi.models.AssetsSort
import io.tradingapi.models.AssetsTab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class PerpsRepository(
    private val api: API,
    private val candleFeed: PerpsCandleFeed,
    private val environment: EnvironmentHelper,
) {

    suspend fun getMarkets(
        query: String,
        filter: PerpsMarketFilter,
        sort: PerpsSort,
        currency: String,
        cursor: String?,
    ): KResult<PerpsMarketsPage, PerpsError> = withContext(Async.Io) {
        try {
            val response = api.trading.assets.getAssetsCatalogV2(
                tab = AssetsTab.perpetuals,
                showPerps = null,
                q = query.takeIf { it.isNotBlank() },
                filter = filter.toApiFilter(),
                sort = sort.toApiSort(),
                order = AssetsOrder.desc,
                cursor = cursor,
                pageSize = PERPS_PAGE_SIZE,
                currency = currency,
                xLang = environment.locale(),
                storeCountryCode = environment.storeCountry(),
                deviceCountryCode = environment.deviceCountry(),
                simCountry = environment.simCountry(),
                timezone = environment.timezone(),
                isVpnActive = environment.isVpnActive(),
            )
            KResult.Ok(
                PerpsMarketsPage(
                    markets = response.items.mapNotNull { it.toPerpMarket() },
                    nextCursor = response.nextCursor?.takeIf { it.isNotBlank() },
                )
            )
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            KResult.Err(e.toPerpsError())
        }
    }

    suspend fun getMarketsByIndices(
        indices: Collection<Int>,
    ): KResult<List<PerpMarket>, PerpsError> = withContext(Async.Io) {
        val chunks = indices.distinct().chunked(IDS_CHUNK_SIZE)
        if (chunks.isEmpty()) {
            return@withContext KResult.Ok(emptyList())
        }
        try {
            val markets = chunks.flatMap { chunk ->
                val response = api.trading.assets.getAssetsCatalogV2(
                    tab = AssetsTab.perpetuals,
                    showPerps = null,
                    ids = chunk.map { "$PERP_ASSET_ID_PREFIX$it" },
                    sort = null,
                    order = null,
                    pageSize = IDS_CHUNK_SIZE,
                    currency = USD_CURRENCY,
                    xLang = environment.locale(),
                    storeCountryCode = environment.storeCountry(),
                    deviceCountryCode = environment.deviceCountry(),
                    simCountry = environment.simCountry(),
                    timezone = environment.timezone(),
                    isVpnActive = environment.isVpnActive(),
                )
                response.items.mapNotNull { it.toPerpMarket() }
            }
            KResult.Ok(markets)
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            KResult.Err(e.toPerpsError())
        }
    }

    suspend fun getMarketDetails(
        marketIndex: Int,
    ): KResult<PerpsMarketDetails, PerpsError> = withContext(Async.Io) {
        try {
            val response = api.trading.assets.getAssetDetailsV2(
                assetId = "$PERP_ASSET_ID_PREFIX$marketIndex",
                currency = USD_CURRENCY,
                xLang = environment.locale(),
                storeCountryCode = environment.storeCountry(),
                deviceCountryCode = environment.deviceCountry(),
                simCountry = environment.simCountry(),
                timezone = environment.timezone(),
                isVpnActive = environment.isVpnActive(),
            )
            val market = response.asset.toPerpMarket()
            if (market != null) {
                KResult.Ok(
                    PerpsMarketDetails(
                        market = market,
                        about = response.sections.about
                            .takeIf { it.enabled }
                            ?.text
                            ?.trim()
                            ?.takeIf { it.isNotBlank() },
                    )
                )
            } else {
                KResult.Err(PerpsError.Unknown)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            KResult.Err(e.toPerpsError())
        }
    }

    suspend fun getTradingState(
        marketIndex: Int,
        walletId: String,
    ): KResult<PerpsTradingState, PerpsError> = withContext(Async.Io) {
        try {
            val screen = api.perps.screens.getTradingScreen(
                market = marketIndex,
                xWalletID = walletId,
            )
            if (screen.market != null) {
                KResult.Ok(
                    PerpsTradingState(
                        flags = screen.flags.toPerpsTradingFlags(),
                        limitOrders = screen.openOrders.orEmpty().mapNotNull { it.toPerpsLimitOrder() },
                    )
                )
            } else {
                KResult.Err(PerpsError.Unknown)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            KResult.Err(e.toPerpsError())
        }
    }

    suspend fun getOpenPosition(
        marketIndex: Int,
        walletId: String,
    ): KResult<PerpsPositionDetail?, PerpsError> = withContext(Async.Io) {
        try {
            val page = api.perps.positions.listOpenPositions(xWalletID = walletId)
            val positionId = page.positions
                .orEmpty()
                .firstOrNull { it.marketIndex == marketIndex }
                ?.id
            if (positionId != null) {
                try {
                    val detail = api.perps.positions.getOpenPosition(
                        xWalletID = walletId,
                        id = positionId,
                    )
                    KResult.Ok(detail.toPerpsPositionDetail())
                } catch (e: ClientException) {
                    if (e.statusCode == HTTP_NOT_FOUND) {
                        KResult.Ok(null)
                    } else {
                        throw e
                    }
                }
            } else {
                KResult.Ok(null)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            KResult.Err(e.toPerpsError())
        }
    }

    fun candles(
        symbol: String,
        timeframe: PerpsChartTimeframe,
    ): Flow<PerpsChartFeedEvent> = candleFeed.stream(symbol, timeframe)

    suspend fun getPortfolio(
        walletId: String,
    ): KResult<PerpsPortfolio, PerpsError> = withContext(Async.Io) {
        try {
            val screen = api.perps.screens.getPortfolioScreen(xWalletID = walletId)
            KResult.Ok(
                PerpsPortfolio(
                    balance = screen.balance?.toPerpsBalance(),
                    positions = screen.positions.orEmpty().mapNotNull { it.toPerpsPosition() },
                )
            )
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            val error = e.toPerpsError()
            if (error == PerpsError.ServiceUnavailable) {
                KResult.Err(PerpsError.NoAccount)
            } else {
                KResult.Err(error)
            }
        }
    }
}

internal fun PerpsMarketFilter.toApiFilter(): AssetsFilter? {
    return when (this) {
        PerpsMarketFilter.ALL -> null
        PerpsMarketFilter.TOKENS -> AssetsFilter.tokens
        PerpsMarketFilter.COMMODITIES -> AssetsFilter.commodities
        PerpsMarketFilter.STOCKS -> AssetsFilter.stocks
        PerpsMarketFilter.ETFS -> AssetsFilter.etfs
    }
}

private fun PerpsSort.toApiSort(): AssetsSort {
    return when (this) {
        PerpsSort.VOLUME -> AssetsSort.volume_24h
        PerpsSort.PRICE_CHANGE -> AssetsSort.price_24h
        PerpsSort.OPEN_INTEREST -> AssetsSort.open_interest_usd
    }
}

private fun TradingFlags?.toPerpsTradingFlags(): PerpsTradingFlags {
    if (this != null) {
        return PerpsTradingFlags(
            openEnabled = openEnabled ?: true,
            closeEnabled = closeEnabled ?: true,
            cancelEnabled = cancelEnabled ?: true,
            addMarginEnabled = addMarginEnabled ?: true,
            removeMarginEnabled = removeMarginEnabled ?: true,
            autoCloseEnabled = autoCloseEnabled ?: true,
        )
    }
    return PerpsTradingFlags.ALL_ENABLED
}

private fun Order.toPerpsLimitOrder(): PerpsLimitOrder? {
    if (type != OrderType.limit || status !in RESTING_STATUSES || category !in RESTING_CATEGORIES) {
        return null
    }
    val id = orderId?.takeIf { it.isNotBlank() } ?: return null
    val orderSide = when (side) {
        OrderSide.long -> PerpsPositionSide.LONG
        OrderSide.short -> PerpsPositionSide.SHORT
        null -> return null
    }
    val base = baseSize.toDecimalOrNull()

    return PerpsLimitOrder(
        orderId = id,
        side = orderSide,
        remainingBase = base?.subtract(filledBase.toDecimalOrNull() ?: BigDecimal.ZERO),
        limitPrice = price.toDecimalOrNull(),
    )
}

private fun OpenPositionDetail.toPerpsPositionDetail(): PerpsPositionDetail? {
    val open = position?.toPerpsOpenPosition() ?: return null

    return PerpsPositionDetail(
        position = open,
        autoCloseKnown = autoCloseKnown == true,
        takeProfit = autoClose?.takeProfit?.toPerpsAutoCloseLeg(PerpsAutoCloseLegKind.TAKE_PROFIT),
        stopLoss = autoClose?.stopLoss?.toPerpsAutoCloseLeg(PerpsAutoCloseLegKind.STOP_LOSS),
    )
}

private fun OpenPosition.toPerpsOpenPosition(): PerpsOpenPosition? {
    val positionId = id?.takeIf { it.isNotBlank() } ?: return null
    val index = marketIndex ?: return null
    val ticker = symbol?.takeIf { it.isNotBlank() } ?: return null
    val positionSide = when (side) {
        PositionSide.long -> PerpsPositionSide.LONG
        PositionSide.short -> PerpsPositionSide.SHORT
        null -> return null
    }

    return PerpsOpenPosition(
        id = positionId,
        marketIndex = index,
        symbol = ticker,
        side = positionSide,
        size = propertySize.toDecimalOrNull(),
        positionValue = positionValue.toDecimalOrNull(),
        avgEntryPrice = avgEntryPrice.toDecimalOrNull(),
        markPrice = markPrice.toDecimalOrNull(),
        liquidationPrice = liquidationPrice.toDecimalOrNull(),
        liquidationDistancePct = liquidationDistancePct.toDecimalOrNull(),
        margin = margin.toDecimalOrNull(),
        unrealizedPnl = unrealizedPnl.toDecimalOrNull(),
        roiPct = roiPct.toDecimalOrNull(),
        leverage = leverage?.takeIf { it > 0 },
        fundingPaid = fundingPaid.toDecimalOrNull(),
    )
}

private fun PositionAutoCloseLeg.toPerpsAutoCloseLeg(
    kind: PerpsAutoCloseLegKind,
): PerpsAutoCloseLeg {
    return PerpsAutoCloseLeg(
        kind = kind,
        orderIndex = orderIndex,
        triggerPrice = triggerPrice.toDecimalOrNull(),
        sharePct = sharePct.toDecimalOrNull(),
        projectedEquity = projectedEquity.toDecimalOrNull(),
        projectedRoiPct = projectedRoiPct.toDecimalOrNull(),
    )
}

private fun Balance.toPerpsBalance(): PerpsBalance {
    return PerpsBalance(
        availableBalance = availableBalance.toDecimalOrNull(),
        equity = equity.toDecimalOrNull(),
        unrealizedPnlTotal = unrealizedPnlTotal.toDecimalOrNull(),
    )
}

private fun Position.toPerpsPosition(): PerpsPosition? {
    val index = marketIndex ?: return null
    val ticker = symbol?.takeIf { it.isNotBlank() } ?: return null
    val positionSide = when (side) {
        PositionSide.long -> PerpsPositionSide.LONG
        PositionSide.short -> PerpsPositionSide.SHORT
        null -> return null
    }

    return PerpsPosition(
        marketIndex = index,
        symbol = ticker,
        iconUrl = null,
        side = positionSide,
        positionValue = positionValue.toDecimalOrNull(),
        unrealizedPnl = unrealizedPnl.toDecimalOrNull(),
        leverage = leverage,
    )
}

private fun String?.toDecimalOrNull(): BigDecimal? {
    val value = this?.takeIf { it.isNotBlank() } ?: return null
    return try {
        BigDecimal(value)
    } catch (e: NumberFormatException) {
        L.e(e)
        null
    }
}

private const val HTTP_NOT_FOUND = 404
private const val IDS_CHUNK_SIZE = 10
private val RESTING_STATUSES = setOf(
    OrderStatus.new,
    OrderStatus.open,
    OrderStatus.partially_filled,
)
private val RESTING_CATEGORIES = setOf(OrderCategory.open, OrderCategory.close)
