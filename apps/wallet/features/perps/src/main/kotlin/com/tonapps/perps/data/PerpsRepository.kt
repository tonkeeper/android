package com.tonapps.perps.data

import com.tonapps.async.Async
import com.tonapps.core.helper.EnvironmentHelper
import com.tonapps.log.L
import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.trade.PerpsProductRules
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.multichain.asset.perpsAssetId
import io.infrastructure.ClientException
import io.infrastructure.ServerError
import io.infrastructure.ServerException
import io.infrastructure.Success
import io.perpsapi.apis.ActivityApi.TypeGetActivity
import io.perpsapi.apis.OrderbookApi.SideGetTruncatedOrderBook
import io.perpsapi.models.Account
import io.perpsapi.models.ActivityItem
import io.perpsapi.models.Balance
import io.perpsapi.models.BindAccountRequest
import io.perpsapi.models.ChangeAccountTierRequest
import io.perpsapi.models.Deposit
import io.perpsapi.models.DepositQuote
import io.perpsapi.models.DepositQuoteRequest
import io.perpsapi.models.FundingStatus
import io.perpsapi.models.OpenDepositRequest
import io.perpsapi.models.IssueRoTokenRequest
import io.perpsapi.models.Market
import io.perpsapi.models.OpenPosition
import io.perpsapi.models.OpenPositionDetail
import io.perpsapi.models.Order
import io.perpsapi.models.OrderCategory
import io.perpsapi.models.OrderSide
import io.perpsapi.models.OrderStatus
import io.perpsapi.models.OrderType
import io.perpsapi.models.PositionAutoCloseLeg
import io.perpsapi.models.PositionEpisode
import io.perpsapi.models.PositionSide
import io.perpsapi.models.SendTransactionsRequest
import io.perpsapi.models.SignedTransaction
import io.perpsapi.models.TradingFlags
import io.perpsapi.models.TradingScreen
import io.perpsapi.models.WithdrawQuote
import io.perpsapi.models.WithdrawQuoteRequest
import io.tradingapi.models.AssetsFilter
import io.tradingapi.models.AssetsOrder
import io.tradingapi.models.AssetsSort
import io.tradingapi.models.AssetsTab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

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
                    ids = chunk.map(::perpsAssetId),
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
                assetId = perpsAssetId(marketIndex),
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
            val screen = retrying { api.perps.screens.getTradingScreen(market = marketIndex, xWalletId = walletId) }
            // Unusable market rules only block order entry; the rest of the screen still reads.
            KResult.Ok(
                PerpsTradingState(
                    flags = screen.flags.toPerpsTradingFlags(),
                    limitOrders = screen.openOrders.orEmpty().mapNotNull { it.toPerpsLimitOrder() },
                    availableBalance = screen.balance?.spendableBalance().toDecimalOrNull(),
                    rules = screen.market?.toPerpsMarketRules(),
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

    suspend fun getOpenOrders(
        marketIndex: Int,
        walletId: String,
    ): KResult<List<PerpsLimitOrder>, PerpsError> = withContext(Async.Io) {
        try {
            val page = api.perps.orders.listOpenOrders(xWalletId = walletId)
            KResult.Ok(
                page.orders.orEmpty()
                    .filter { it.marketIndex == marketIndex }
                    .mapNotNull { it.toPerpsLimitOrder() }
            )
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            KResult.Err(e.toPerpsError())
        }
    }

    suspend fun tradingSnapshot(
        marketIndex: Int,
        walletId: String,
    ): KResult<PerpsTradingSnapshot, PerpsError> = withContext(Async.Io) {
        try {
            val response = retrying { tradingScreen(marketIndex, walletId) }
            val integrator = retrying { api.perps.integrator.getIntegrator(xWalletId = walletId) }
            KResult.Ok(
                PerpsTradingSnapshot(
                    screen = response.screen,
                    integrator = integrator,
                    receivedAtMs = response.receivedAtMs,
                    serverTimeMs = response.serverTimeMs,
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

    suspend fun getOpenPosition(
        marketIndex: Int,
        walletId: String,
    ): KResult<PerpsPositionDetail?, PerpsError> = withContext(Async.Io) {
        try {
            val detail = retrying {
                api.perps.positions.getOpenPosition(xWalletId = walletId, id = LIGHTER_POSITION_PREFIX + marketIndex)
            }
            KResult.Ok(detail.toPerpsPositionDetail())
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            if (e is ClientException && e.statusCode == HTTP_NOT_FOUND) {
                return@withContext KResult.Ok(null)
            }
            L.e(e)
            KResult.Err(e.toPerpsError())
        }
    }

    suspend fun nextNonce(
        walletId: String,
    ): KResult<PerpsNextNonce, PerpsError> = withContext(Async.Io) {
        try {
            val response = retrying {
                api.perps.transactions.getNextNonce(apiKeyIndex = LIGHTER_API_KEY_INDEX, xWalletId = walletId)
            }
            KResult.Ok(
                PerpsNextNonce(
                    accountIndex = response.accountIndex,
                    nonce = response.nonce,
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

    suspend fun account(walletId: String): KResult<PerpsAccount, PerpsError> = withContext(Async.Io) {
        try {
            KResult.Ok(retrying { api.perps.account.getAccount(xWalletId = walletId) }.toPerpsAccount())
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            KResult.Err(e.toPerpsError())
        }
    }

    suspend fun bindAccount(
        walletId: String,
        deadline: Long,
        signature: String,
        proof: String,
    ): KResult<PerpsAccount, PerpsError> = withContext(Async.Io) {
        try {
            val account = retrying {
                api.perps.account.bindAccount(
                    xWalletId = walletId,
                    bindAccountRequest = BindAccountRequest(
                        proof = proof,
                        deadline = deadline,
                        signature = signature,
                    ),
                )
            }
            KResult.Ok(account.toPerpsAccount())
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            KResult.Err(e.toPerpsError())
        }
    }

    suspend fun issueRoToken(
        walletId: String,
        lighterAuth: String,
    ): KResult<Unit, PerpsError> = withContext(Async.Io) {
        try {
            api.perps.account.issueRoToken(
                xWalletId = walletId,
                issueRoTokenRequest = IssueRoTokenRequest(
                    lighterAuth = lighterAuth,
                ),
            )
            KResult.Ok(Unit)
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            KResult.Err(e.toPerpsError())
        }
    }

    suspend fun accountTierName(walletId: String): KResult<String, PerpsError> = withContext(Async.Io) {
        try {
            KResult.Ok(api.perps.account.getAccountTier(xWalletId = walletId).name)
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            KResult.Err(e.toPerpsError())
        }
    }

    suspend fun changeAccountTier(
        walletId: String,
        lighterAuth: String,
        tierName: String,
    ): KResult<Unit, PerpsError> = withContext(Async.Io) {
        try {
            api.perps.account.changeAccountTier(
                xWalletId = walletId,
                changeAccountTierRequest = ChangeAccountTierRequest(
                    lighterAuth = lighterAuth,
                    newTier = tierName,
                ),
            )
            KResult.Ok(Unit)
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            KResult.Err(e.toPerpsError())
        }
    }

    suspend fun quoteDeposit(walletId: String, request: DepositQuoteRequest): DepositQuote = withContext(Async.Io) {
        api.perps.funding.quoteDeposit(xWalletId = walletId, depositQuoteRequest = request)
    }

    suspend fun openDeposit(walletId: String, request: OpenDepositRequest): Deposit = withContext(Async.Io) {
        api.perps.funding.openDeposit(xWalletId = walletId, openDepositRequest = request)
    }

    suspend fun getDeposit(walletId: String, depositId: String): Deposit = withContext(Async.Io) {
        api.perps.funding.getDeposit(xWalletId = walletId, depositId = depositId)
    }

    suspend fun getFundingStatus(walletId: String, executionId: String): FundingStatus = withContext(Async.Io) {
        api.perps.funding.getFundingStatus(xWalletId = walletId, executionId = executionId)
    }

    suspend fun quoteWithdraw(walletId: String, request: WithdrawQuoteRequest): WithdrawQuote = withContext(Async.Io) {
        api.perps.funding.quoteWithdraw(xWalletId = walletId, withdrawQuoteRequest = request)
    }

    suspend fun sendTransactions(
        walletId: String,
        txs: List<PerpsSignedTx>,
    ): KResult<List<PerpsSentTx>, PerpsSendError> = withContext(Async.Io) {
        try {
            val response = api.perps.transactions.sendTransactions(
                xWalletId = walletId,
                sendTransactionsRequest = SendTransactionsRequest(
                    transactions = txs.map {
                        SignedTransaction(txType = it.txType, txInfo = it.txInfo)
                    },
                ),
            )
            KResult.Ok(
                response.transactions.map {
                    PerpsSentTx(txType = it.txType, txHash = it.txHash)
                }
            )
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            KResult.Err(e.toPerpsSendError())
        }
    }

    suspend fun withdrawalActivity(walletId: String, limit: Int): List<ActivityItem> = withContext(Async.Io) {
        api.perps.activity.getActivity(
            xWalletId = walletId,
            type = TypeGetActivity.withdrawal,
            limit = limit,
        ).items.orEmpty()
    }

    suspend fun positionState(
        walletId: String,
        marketIndex: Int,
        clientOrderIndex: Long? = null,
    ): KResult<PerpsPositionState, PerpsError> = withContext(Async.Io) {
        try {
            val state = retrying {
                api.perps.positions.getPositionState(
                    xWalletId = walletId,
                    id = LIGHTER_POSITION_PREFIX + marketIndex,
                    clientOrderIndex = clientOrderIndex,
                )
            }
            KResult.Ok(
                PerpsPositionState(
                    open = state.open?.toPerpsEpisode(),
                    lastClosed = state.lastClosed?.toPerpsEpisode(),
                    orders = state.orders.map {
                        PerpsStateOrder(
                            orderIndex = it.orderIndex,
                            status = it.status,
                            filledBase = it.filledBase.toDecimalOrNull(),
                        )
                    },
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

    suspend fun truncatedOrderBook(
        walletId: String,
        marketIndex: Int,
        side: PerpsOrderSide,
        feeRate: BigDecimal,
        notional: BigDecimal,
    ): KResult<PerpsOrderBook, PerpsError> = withContext(Async.Io) {
        try {
            val book = retrying {
                api.perps.orderbook.getTruncatedOrderBook(
                    market = marketIndex,
                    side = when (side) {
                        PerpsOrderSide.LONG -> SideGetTruncatedOrderBook.buy
                        PerpsOrderSide.SHORT -> SideGetTruncatedOrderBook.sell
                    },
                    feeRate = feeRate.toRequestDecimal(),
                    notional = notional.toRequestDecimal(),
                    xWalletId = walletId,
                )
            }
            val levels = when (side) {
                PerpsOrderSide.LONG -> book.asks
                PerpsOrderSide.SHORT -> book.bids
            }
            KResult.Ok(
                PerpsOrderBook(
                    truncated = book.truncated,
                    levels = levels.orEmpty().mapNotNull { level ->
                        val price = level.price.toDecimalOrNull() ?: return@mapNotNull null
                        val size = level.propertySize.toDecimalOrNull() ?: return@mapNotNull null
                        price to size
                    },
                    updatedAtMs = book.updatedAt.toInstant().toEpochMilli(),
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

    fun candles(
        symbol: String,
        timeframe: PerpsChartTimeframe,
    ): Flow<PerpsChartFeedEvent> = candleFeed.stream(symbol, timeframe)

    suspend fun olderCandles(
        symbol: String,
        timeframe: PerpsChartTimeframe,
        endTimeSeconds: Long,
    ): List<PerpsCandle>? = candleFeed.olderCandles(symbol, timeframe, endTimeSeconds)

    suspend fun getShelfMarkets(): List<PerpMarket> {
        val page = getMarkets("", PerpsMarketFilter.ALL, PerpsSort.VOLUME, USD_CURRENCY, cursor = null)
        return page.getOrNull()?.markets?.take(SHELF_SIZE).orEmpty()
    }

    suspend fun getOpenPositions(
        walletId: String,
    ): KResult<List<PerpsPosition>, PerpsError> = withContext(Async.Io) {
        try {
            val page = api.perps.positions.listOpenPositions(xWalletId = walletId)
            KResult.Ok(page.positions.orEmpty().mapNotNull { it.toPerpsPosition() })
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            KResult.Err(e.toPerpsError())
        }
    }

    suspend fun getPortfolio(
        walletId: String,
    ): KResult<PerpsPortfolio, PerpsError> = withContext(Async.Io) {
        try {
            val portfolio = retrying { api.perps.portfolio.getPortfolio(xWalletId = walletId) }
            KResult.Ok(
                PerpsPortfolio(
                    balance = portfolio.balance?.toPerpsBalance(),
                    positions = portfolio.positions.orEmpty().mapNotNull { it.toPerpsPosition() },
                    openOrders = portfolio.openOrders?.mapNotNull { it.toPortfolioOrder() },
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

    suspend fun getPortfolioWithoutOrders(
        walletId: String,
    ): KResult<PerpsPortfolio, PerpsError> = withContext(Async.Io) {
        val positionsAsync = async { getOpenPositions(walletId) }
        val account = when (val result = account(walletId)) {
            is KResult.Ok -> result.value
            is KResult.Err -> {
                positionsAsync.cancel()
                return@withContext KResult.Err(result.value)
            }
        }
        when (val positions = positionsAsync.await()) {
            is KResult.Ok -> KResult.Ok(
                PerpsPortfolio(
                    balance = account.toPerpsBalance(),
                    positions = positions.value,
                    openOrders = null,
                )
            )
            is KResult.Err -> KResult.Err(positions.value)
        }
    }

    suspend fun getPortfolioBalance(
        walletId: String,
    ): KResult<PerpsBalance?, PerpsError> = withContext(Async.Io) {
        try {
            val portfolio = retrying { api.perps.portfolio.getPortfolio(xWalletId = walletId) }
            KResult.Ok(portfolio.balance?.toPerpsBalance())
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            val error = e.toPerpsError()
            if (error != PerpsError.RoTokenUnavailable) {
                L.e(e)
            }
            KResult.Err(error)
        }
    }

    private suspend fun <T> retrying(block: suspend () -> T): T {
        repeat(RETRY_ATTEMPTS - 1) { attempt ->
            try {
                return block()
            } catch (e: Throwable) {
                if (e is CancellationException || !e.isRetryable()) {
                    throw e
                }
                delay(RETRY_BASE_DELAY_MS shl attempt)
            }
        }
        return block()
    }

    private fun tradingScreen(marketIndex: Int, walletId: String): TradingScreenResponse {
        val response = api.perps.screens.getTradingScreenWithHttpInfo(market = marketIndex, xWalletId = walletId)
        val receivedAtMs = System.currentTimeMillis()
        val screen = (response as? Success)?.data
        if (screen != null) {
            return TradingScreenResponse(screen, response.headers.serverTimeMs(), receivedAtMs)
        }
        // toPerpsError and isRetryable need only statusCode and response.
        throw when (response) {
            is ServerError -> ServerException("Server error : ${response.statusCode}", response.statusCode, response)
            else -> ClientException("Client error : ${response.statusCode}", response.statusCode, response)
        }
    }
}

private class TradingScreenResponse(val screen: TradingScreen, val serverTimeMs: Long?, val receivedAtMs: Long)

private fun Map<String, List<String>>.serverTimeMs(): Long? {
    val value = entries.firstOrNull { it.key.equals(DATE_HEADER, ignoreCase = true) }?.value?.firstOrNull() ?: return null
    return runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
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
            openEnabled = openEnabled ?: false,
            closeEnabled = closeEnabled ?: true,
            cancelEnabled = cancelEnabled ?: true,
            addMarginEnabled = addMarginEnabled ?: true,
            removeMarginEnabled = removeMarginEnabled ?: true,
            autoCloseEnabled = autoCloseEnabled ?: true,
        )
    }
    return PerpsTradingFlags.VENUE_SILENT
}

private fun Market.toPerpsMarketRules(): PerpsMarketRules? {
    val priceScale = priceDecimals ?: return null
    val sizeScale = sizeDecimals ?: return null

    return PerpsMarketRules(
        maxLeverage = PerpsProductRules.leverageCeiling(maxLeverage, initialMarginFraction),
        priceDecimals = priceScale,
        sizeDecimals = sizeScale,
        tickSize = tickSize.toDecimalOrNull(),
        stepSize = stepSize.toDecimalOrNull(),
        minSizeBase = minSizeBase.toDecimalOrNull(),
        minSizeQuote = minSizeQuote.toDecimalOrNull(),
        takerFeePct = takerFeePct.toDecimalOrNull(),
        initialMarginBps = initialMarginFraction,
        maintenanceMarginBps = maintenanceMarginFraction,
        markPrice = markPrice.toDecimalOrNull(),
        tradable = status == null || status == MARKET_STATUS_ACTIVE,
    )
}

private fun Account.toPerpsAccount(): PerpsAccount {
    return PerpsAccount(
        accountIndex = accountIndex?.toLong(),
        l1Address = l1Address,
        hasL2Key = hasL2Key,
        hasRoToken = hasRoToken,
        availableBalance = availableBalance,
        collateral = collateral,
        apiKeys = apiKeys?.map { PerpsAccountKey(apiKeyIndex = it.apiKeyIndex, publicKey = it.publicKey) },
    )
}

private fun PositionEpisode.toPerpsEpisode(): PerpsEpisode {
    return PerpsEpisode(
        episodeId = episodeId,
        openedAtMs = openedAt.toInstant().toEpochMilli(),
    )
}

private fun BigDecimal.toRequestDecimal(): String {
    return setScale(REQUEST_DECIMALS, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
}

private fun Order.toPerpsLimitOrder(): PerpsLimitOrder? {
    if (type != OrderType.limit || status !in RESTING_STATUSES || category !in RESTING_CATEGORIES) {
        return null
    }
    val index = orderIndex ?: return null
    val orderSide = when (side) {
        OrderSide.long -> PerpsPositionSide.LONG
        OrderSide.short -> PerpsPositionSide.SHORT
        OrderSide.unknown, null -> return null
    }
    val base = baseSize.toDecimalOrNull()

    return PerpsLimitOrder(
        orderIndex = index,
        clientOrderIndex = clientOrderIndex,
        side = orderSide,
        remainingBase = base?.subtract(filledBase.toDecimalOrNull() ?: BigDecimal.ZERO),
        limitPrice = price.toDecimalOrNull()?.takeIf { it.signum() > 0 },
        expiryUnixMs = expiresAt?.toInstant()?.toEpochMilli()?.takeIf { it > 0L },
    )
}

private fun Order.toPortfolioOrder(): PerpsPortfolioOrder? {
    val order = toPerpsLimitOrder() ?: return null

    return PerpsPortfolioOrder(
        marketIndex = marketIndex ?: return null,
        orderIndex = order.orderIndex,
        clientOrderIndex = order.clientOrderIndex,
        symbol = symbol?.takeIf { it.isNotBlank() } ?: return null,
        iconUrl = null,
        side = order.side,
        remainingBase = order.remainingBase,
        limitPrice = order.limitPrice,
        leverage = null,
        margin = null,
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
        PositionSide.unknown, null -> return null
    }

    val positionMargin = margin.toDecimalOrNull()
    val positionPnl = unrealizedPnl.toDecimalOrNull()
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
        margin = positionMargin,
        equity = equity.toDecimalOrNull() ?: positionMargin?.add(positionPnl ?: BigDecimal.ZERO),
        unrealizedPnl = positionPnl,
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
        clientOrderIndex = clientOrderIndex,
        triggerPrice = triggerPrice.toDecimalOrNull(),
        sharePct = sharePct.toDecimalOrNull(),
        projectedRoiPct = projectedRoiPct.toDecimalOrNull(),
    )
}

private fun PerpsAccount.toPerpsBalance(): PerpsBalance {
    return PerpsBalance(
        collateral = collateral.toDecimalOrNull(),
        availableBalance = availableBalance.toDecimalOrNull(),
        transferableBalance = null,
    )
}

private fun Balance.toPerpsBalance(): PerpsBalance {
    return PerpsBalance(
        collateral = collateral.toDecimalOrNull(),
        availableBalance = availableBalance.toDecimalOrNull(),
        transferableBalance = transferableBalance.toDecimalOrNull(),
    )
}

private fun OpenPosition.toPerpsPosition(): PerpsPosition? {
    val index = marketIndex ?: return null
    val ticker = symbol?.takeIf { it.isNotBlank() } ?: return null
    val positionSide = when (side) {
        PositionSide.long -> PerpsPositionSide.LONG
        PositionSide.short -> PerpsPositionSide.SHORT
        PositionSide.unknown, null -> return null
    }

    val positionMargin = margin.toDecimalOrNull()
    val positionPnl = unrealizedPnl.toDecimalOrNull()
    return PerpsPosition(
        marketIndex = index,
        symbol = ticker,
        iconUrl = null,
        side = positionSide,
        margin = positionMargin,
        unrealizedPnl = positionPnl,
        equity = equity.toDecimalOrNull() ?: positionMargin?.add(positionPnl ?: BigDecimal.ZERO),
        leverage = leverage?.takeIf { it > 0 },
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
private const val DATE_HEADER = "Date"
private const val RETRY_ATTEMPTS = 3
private const val RETRY_BASE_DELAY_MS = 1_000L
private const val REQUEST_DECIMALS = 8
private const val MARKET_STATUS_ACTIVE = "active"
private const val LIGHTER_POSITION_PREFIX = "lighter:"
private const val IDS_CHUNK_SIZE = 10
private const val SHELF_SIZE = 8
private val RESTING_STATUSES = setOf(
    OrderStatus.new,
    OrderStatus.open,
    OrderStatus.partially_filled,
)
private val RESTING_CATEGORIES = setOf(OrderCategory.open, OrderCategory.close)
