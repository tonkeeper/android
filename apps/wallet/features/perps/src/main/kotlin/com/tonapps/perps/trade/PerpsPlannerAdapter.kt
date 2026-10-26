package com.tonapps.perps.trade

import com.tonapps.chainkit.core.common.getOrElse
import com.tonapps.chainkit.lighter.LighterConstants
import com.tonapps.chainkit.lighter.trading.LighterPerpsMediator
import com.tonapps.chainkit.lighter.trading.PerpsFixedPoint
import com.tonapps.chainkit.lighter.trading.PerpsTradeBounds
import com.tonapps.chainkit.perpetual.PerpetualAccountSnapshot
import com.tonapps.chainkit.perpetual.PerpetualMarketSnapshot
import com.tonapps.chainkit.perpetual.PerpetualOperation
import com.tonapps.chainkit.perpetual.PerpsAppliedPrerequisite
import com.tonapps.chainkit.perpetual.PerpsAutoCloseLeg
import com.tonapps.chainkit.perpetual.PerpsAutoCloseSpec
import com.tonapps.chainkit.perpetual.PerpsBookLevel
import com.tonapps.chainkit.perpetual.PerpsBookSlice
import com.tonapps.chainkit.perpetual.PerpsContextCompleteness
import com.tonapps.chainkit.perpetual.PerpsFeeKind
import com.tonapps.chainkit.perpetual.PerpsFeeRates
import com.tonapps.chainkit.perpetual.PerpsFreshness
import com.tonapps.chainkit.perpetual.PerpsIntegratorFees
import com.tonapps.chainkit.perpetual.PerpsMarginSettings
import com.tonapps.chainkit.perpetual.PerpsMarkPrice
import com.tonapps.chainkit.perpetual.PerpsMarketRules
import com.tonapps.chainkit.perpetual.PerpsOrderCategory
import com.tonapps.chainkit.perpetual.PerpsOrderSpec
import com.tonapps.chainkit.perpetual.PerpsPositionState
import com.tonapps.chainkit.perpetual.PerpsPrepareRequest
import com.tonapps.chainkit.perpetual.PerpsRes
import com.tonapps.chainkit.perpetual.PerpsRestingOrder
import com.tonapps.chainkit.perpetual.PerpsRestingOrderStatus
import com.tonapps.chainkit.perpetual.PerpsRestingOrderType
import com.tonapps.chainkit.perpetual.PerpsScale
import com.tonapps.chainkit.perpetual.PerpsScope
import com.tonapps.chainkit.perpetual.PerpsSide
import com.tonapps.chainkit.perpetual.PerpsTradeError
import com.tonapps.chainkit.perpetual.PerpsTradeException
import com.tonapps.chainkit.perpetual.PerpsTradeIntent
import com.tonapps.log.L
import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLoadException
import com.tonapps.perps.data.PerpsMarkQuote
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.data.PerpsTradingSnapshot
import com.tonapps.perps.data.PerpsTradingState
import com.tonapps.perps.data.spendableBalance
import com.tonapps.wallet.data.multichain.lighter.LighterSigningCredentials
import io.perpsapi.models.Integrator
import io.perpsapi.models.MarginMode
import io.perpsapi.models.Market
import io.perpsapi.models.Order
import io.perpsapi.models.OrderCategory
import io.perpsapi.models.OrderSide
import io.perpsapi.models.OrderStatus
import io.perpsapi.models.OrderType
import io.perpsapi.models.PositionSide
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.UUID
import kotlin.math.abs

data class PerpsPositionBounds(
    val minReduceMargin: BigDecimal?,
    val maxReduceMargin: BigDecimal?,
    val maxAddMargin: BigDecimal,
    val maxAddMarginQuote: BigDecimal,
    val maxRemoveMarginQuote: BigDecimal,
    val minAdjustMarginQuote: BigDecimal,
)

class PerpsPlannerAdapter(
    private val repository: PerpsRepository,
    private val accountService: PerpsAccountService,
    private val clientOrderIndex: PerpsClientOrderIndexStore,
    val mediator: LighterPerpsMediator,
) {

    suspend fun plan(
        request: PerpsTradeRequest,
        credentials: LighterSigningCredentials,
        mark: PerpsMarkQuote?,
        operationId: String,
        applied: List<PerpsAppliedPrerequisite> = emptyList(),
        previous: PerpsTradeIntent? = null,
    ): KResult<PerpsPlannedTrade, PerpsPlanFailure> {
        val snapshot = when (val result = tradingSnapshot(request.marketIndex, request.walletId)) {
            is KResult.Ok -> result.value
            is KResult.Err -> return KResult.Err(PerpsPlanFailure.Load(result.value))
        }
        val market = snapshot.screen.market
            ?: return KResult.Err(PerpsPlanFailure.Load(PerpsError.Unknown))
        if (snapshot.hasClockSkew()) {
            return KResult.Err(PerpsPlanFailure.ClockSkew)
        }

        var priceDecimals: Int? = null
        return try {
            val scope = scope(request.walletId, credentials)
            val rules = rules(market, snapshot.integrator, scope, snapshot.receivedAtMs)
            priceDecimals = rules.scale.priceDecimals
            val nowMs = System.currentTimeMillis()
            val context = context(
                snapshot = snapshot,
                scope = scope,
                rules = rules,
                openOrdersReadable = openOrdersReadable(request.walletId, request.marketIndex, snapshot),
                request = request,
            )
            val markPrice = markPrice(mark, market.markPrice?.toBigDecimalOrNull(), rules.scale, snapshot.receivedAtMs)
            val intent = previous
                ?: intent(request, scope, rules, context, markPrice.price, operationId, nowMs)
            val plan = prepare(request, intent, rules, context, markPrice, nowMs, applied)
            KResult.Ok(
                PerpsPlannedTrade(
                    request = request,
                    credentials = credentials,
                    intent = intent,
                    rules = rules,
                    context = context,
                    plan = plan,
                    integrator = snapshot.integrator,
                    snapshotAtMs = snapshot.receivedAtMs,
                    markPrice = BigDecimal.valueOf(markPrice.price, rules.scale.priceDecimals),
                )
            )
        } catch (e: PerpsLoadException) {
            L.e(e)
            KResult.Err(PerpsPlanFailure.Load(e.error))
        } catch (e: PerpsTradeException) {
            L.e(e)
            KResult.Err(
                PerpsPlanFailure.Planner(
                    kind = e.kind,
                    message = e.message.orEmpty(),
                    field = e.field,
                    suggestion = e.suggestion,
                    priceDecimals = priceDecimals,
                )
            )
        }
    }

    suspend fun minOpenMargin(request: PerpsTradeRequest.Open): BigDecimal? {
        val snapshot = (tradingSnapshot(request.marketIndex, request.walletId) as? KResult.Ok)?.value ?: return null
        val market = snapshot.screen.market ?: return null
        return try {
            val scope = PerpsScope.review(
                walletId = request.walletId,
                venue = PerpsProductRules.VENUE,
                chainId = PerpsProductRules.CHAIN_ID,
                environment = PerpsProductRules.ENVIRONMENT,
            )
            val rules = rules(market, snapshot.integrator, scope, snapshot.receivedAtMs)
            val nowMs = System.currentTimeMillis()
            val mark = markPrice(null, market.markPrice?.toBigDecimalOrNull(), rules.scale, snapshot.receivedAtMs)
            val intent = openIntent(request, scope, rules, UUID.randomUUID().toString(), nowMs) { it }
            val bounds = withMarketSnapshot(request, intent, rules, mark, nowMs) { marketSnapshot ->
                PerpsTradeBounds().openBounds(intent, rules, marketSnapshot, nowMs)
            }
            BigDecimal.valueOf(bounds.minMarginBudgetQuote, rules.scale.quoteDecimals)
        } catch (e: PerpsLoadException) {
            L.e(e)
            null
        } catch (e: PerpsTradeException) {
            L.e(e)
            null
        }
    }

    suspend fun refreshMarketState(planned: PerpsPlannedTrade): KResult<PerpsMarketState, PerpsError> {
        val request = planned.request
        val snapshot = when (val result = tradingSnapshot(request.marketIndex, request.walletId)) {
            is KResult.Ok -> result.value
            is KResult.Err -> return KResult.Err(result.value)
        }
        val market = snapshot.screen.market ?: return KResult.Err(PerpsError.Unknown)
        val scope = planned.plan.scope

        return try {
            val rules = rules(market, snapshot.integrator, scope, snapshot.receivedAtMs)
            KResult.Ok(
                PerpsMarketState(
                    rules = rules,
                    context = context(
                        snapshot = snapshot,
                        scope = scope,
                        rules = rules,
                        openOrdersReadable = openOrdersReadable(request.walletId, request.marketIndex, snapshot),
                        request = request,
                    ),
                )
            )
        } catch (e: PerpsTradeException) {
            L.e(e)
            KResult.Err(PerpsError.Unknown)
        }
    }

    private fun scope(walletId: String, credentials: LighterSigningCredentials): PerpsScope {
        return PerpsScope(
            walletId = walletId,
            venue = PerpsProductRules.VENUE,
            chainId = credentials.chainId,
            environment = credentials.environment,
            accountIndex = credentials.accountIndex,
            keyBindingId = "${credentials.accountIndex}/${credentials.apiKeyIndex}",
            expectedPublicKey = credentials.key.publicKeyHex,
        )
    }

    private fun rules(
        market: Market,
        integrator: Integrator,
        scope: PerpsScope,
        receivedAtMs: Long,
    ): PerpsMarketRules {
        val marketId = market.marketIndex?.toLong() ?: missing("market_index")
        val priceDecimals = market.priceDecimals ?: missing("price_decimals")
        val baseDecimals = market.sizeDecimals ?: missing("size_decimals")
        val status = market.status?.takeIf { it.isNotBlank() } ?: missing("status")
        val scale = PerpsScale(
            priceDecimals = priceDecimals,
            baseDecimals = baseDecimals,
            quoteDecimals = priceDecimals + baseDecimals,
        )
        val maxLeverage = PerpsProductRules.leverageCeiling(market.maxLeverage, market.initialMarginFraction)

        return PerpsMarketRules(
            scopeEnvironment = scope.environment,
            marketId = marketId,
            quoteAsset = market.quoteAsset ?: PerpsProductRules.QUOTE_ASSET,
            tradingStatus = status,
            scale = scale,
            priceTick = required(market.tickSize, priceDecimals, "tick_size", RoundingMode.UP),
            baseStep = required(market.stepSize, baseDecimals, "step_size", RoundingMode.UP),
            minBaseAmount = optional(market.minSizeBase, baseDecimals, RoundingMode.UP) ?: 0L,
            maxBaseAmount = optional(market.maxSizeBase, baseDecimals, RoundingMode.DOWN),
            minNotionalQuote = optional(market.minSizeQuote, scale.quoteDecimals, RoundingMode.UP),
            maxNotionalQuote = optional(market.maxSizeQuote, scale.quoteDecimals, RoundingMode.DOWN),
            makerFeePpm = feePpm(market.makerFeePct, "maker_fee_pct"),
            takerFeePpm = feePpm(market.takerFeePct, "taker_fee_pct"),
            minInitialMarginBps = PerpsProductRules.IMF_TICKS / maxLeverage,
            maxInitialMarginBps = PerpsProductRules.IMF_TICKS,
            maintenanceMarginBps = market.maintenanceMarginFraction
                ?: missing("maintenance_margin_fraction"),
            closeoutMarginBps = market.closeoutMarginFraction,
            priceSlippagePpm = PerpsProductRules.PRICE_SLIPPAGE_PPM,
            tpslPriceSlippagePpm = PerpsProductRules.TPSL_PRICE_SLIPPAGE_PPM,
            tpslBufferPpm = PerpsProductRules.TPSL_BUFFER_PPM,
            minAdjustMarginQuote = PerpsFixedPoint.parse(
                PerpsProductRules.MIN_ADJUST_MARGIN_QUOTE,
                scale.quoteDecimals,
            ),
            integrator = integratorFees(integrator),
            freshness = PerpsFreshness(receivedAtUnixMs = receivedAtMs),
            removeMarginBufferPpm = PerpsProductRules.REMOVE_MARGIN_BUFFER_PPM,
            minRemoveMarginBufferQuote = BigDecimal(PerpsProductRules.MIN_REMOVE_MARGIN_BUFFER_QUOTE)
                .toFixedPoint(scale.quoteDecimals, RoundingMode.UP),
        )
    }

    private fun integratorFees(integrator: Integrator): PerpsIntegratorFees {
        if (integrator.integratorAccountIndex == 0L) {
            return PerpsIntegratorFees.NONE
        }
        val rates = PerpsFeeRates(
            takerFeePpm = integrator.integratorTakerFee.toLong(),
            makerFeePpm = integrator.integratorMakerFee.toLong(),
        )
        return PerpsIntegratorFees(
            accountIndex = integrator.integratorAccountIndex,
            open = rates,
            close = rates,
            takeProfit = rates,
            stopLoss = rates,
        )
    }

    suspend fun tradingState(
        marketIndex: Int,
        walletId: String,
        reissue: Boolean = true,
    ): KResult<PerpsTradingState, PerpsError> {
        val read: suspend () -> KResult<PerpsTradingState, PerpsError> = { repository.getTradingState(marketIndex, walletId) }
        return if (reissue) {
            accountService.reissuingRoToken(walletId, read)
        } else {
            read()
        }
    }

    private suspend fun tradingSnapshot(marketIndex: Int, walletId: String): KResult<PerpsTradingSnapshot, PerpsError> {
        return accountService.reissuingRoToken(walletId) { repository.tradingSnapshot(marketIndex, walletId) }
    }

    private suspend fun openOrdersReadable(
        walletId: String,
        marketIndex: Int,
        snapshot: PerpsTradingSnapshot,
    ): Boolean {
        if (snapshot.screen.position == null) {
            return snapshot.screen.openOrders != null
        }
        return when (val result = repository.getOpenPosition(marketIndex, walletId)) {
            is KResult.Ok -> result.value?.autoCloseKnown ?: false
            is KResult.Err -> false
        }
    }

    private fun context(
        snapshot: PerpsTradingSnapshot,
        scope: PerpsScope,
        rules: PerpsMarketRules,
        openOrdersReadable: Boolean,
        request: PerpsTradeRequest? = null,
    ): PerpetualAccountSnapshot {
        val screen = snapshot.screen
        val scale = rules.scale
        val position = screen.position
        val positionState = positionState(snapshot, rules)
        val leverage = position?.leverage ?: 0
        val marginSettings = when {
            leverage > 0 -> PerpsMarginSettings.Known(
                mode = if (position?.marginMode == MarginMode.cross) {
                    LighterConstants.CrossMargin
                } else {
                    LighterConstants.IsolatedMargin
                },
                initialMarginBps = PerpsProductRules.initialMarginBps(leverage),
            )

            // chain-kit refuses modify bounds without margin settings, and a flat market has none.
            request is PerpsTradeRequest.OrderModify -> PerpsMarginSettings.Known(
                mode = LighterConstants.IsolatedMargin,
                initialMarginBps = screen.market?.initialMarginFraction?.takeIf { it > 0 }
                    ?: rules.minInitialMarginBps,
            )

            else -> PerpsMarginSettings.Unknown
        }

        return PerpetualAccountSnapshot(
            scope = scope,
            marketId = rules.marketId,
            freshness = PerpsFreshness(receivedAtUnixMs = snapshot.receivedAtMs),
            completeness = if (openOrdersReadable) {
                PerpsContextCompleteness.Complete
            } else {
                PerpsContextCompleteness.Incomplete(OPEN_ORDERS_UNAVAILABLE)
            },
            openEnabled = screen.flags?.openEnabled ?: true,
            availableBalanceQuote = optional(
                screen.balance?.spendableBalance(),
                scale.quoteDecimals,
                RoundingMode.DOWN,
            ) ?: 0L,
            position = positionState,
            restingOrders = screen.openOrders.orEmpty().mapNotNull { it.toRestingOrder(scale) },
            marginSettings = marginSettings,
        )
    }

    private fun positionState(snapshot: PerpsTradingSnapshot, rules: PerpsMarketRules): PerpsPositionState {
        val scale = rules.scale
        val position = snapshot.screen.position ?: return PerpsPositionState.KnownFlat
        val baseAmount = optional(position.propertySize, scale.baseDecimals, RoundingMode.DOWN) ?: 0L
        if (baseAmount <= 0L) {
            return PerpsPositionState.KnownFlat
        }

        return PerpsPositionState.Present(
            positionId = rules.marketId.toString(),
            side = if (position.side == PositionSide.short) {
                PerpsSide.Short
            } else {
                PerpsSide.Long
            },
            baseAmount = baseAmount,
            entryPrice = required(position.avgEntryPrice, scale.priceDecimals, "avg_entry_price", RoundingMode.HALF_UP),
            allocatedMarginQuote = required(
                position.allocatedMargin,
                scale.quoteDecimals,
                "allocated_margin",
                RoundingMode.DOWN,
            ),
            liquidationPrice = optional(position.liquidationPrice, scale.priceDecimals, RoundingMode.HALF_UP),
        )
    }

    private fun intent(
        request: PerpsTradeRequest,
        scope: PerpsScope,
        rules: PerpsMarketRules,
        context: PerpetualAccountSnapshot,
        mark: Long,
        operationId: String,
        nowMs: Long,
    ): PerpsTradeIntent {
        val scale = rules.scale

        return when (request) {
            is PerpsTradeRequest.Open -> openIntent(request, scope, rules, operationId, nowMs)

            is PerpsTradeRequest.Close -> {
                val present = present(context)
                PerpsTradeIntent.Close(
                    operationId = operationId,
                    scope = scope,
                    marketId = rules.marketId,
                    positionId = present.positionId,
                    side = present.side,
                    positionBaseAmount = present.baseAmount,
                    baseAmount = null,
                    maxSlippagePpm = PerpsProductRules.PRICE_SLIPPAGE_PPM,
                    clientOrderIndex = clientOrderIndex.next(nowMs),
                )
            }

            is PerpsTradeRequest.Reduce -> {
                val present = present(context)
                val delta = request.marginDelta.toFixedPoint(scale.quoteDecimals, RoundingMode.DOWN)
                if (present.allocatedMarginQuote <= 0L || delta >= present.allocatedMarginQuote) {
                    throw PerpsTradeException(
                        PerpsTradeError.ExceedsPosition,
                        "amount exceeds position margin",
                        field = MARGIN_DELTA_FIELD,
                    )
                }
                val closeBase = floorToStep(
                    value = BigInteger.valueOf(present.baseAmount)
                        .multiply(BigInteger.valueOf(delta))
                        .divide(BigInteger.valueOf(present.allocatedMarginQuote))
                        .toLong(),
                    step = rules.baseStep,
                )
                if (closeBase <= 0L) {
                    throw PerpsTradeException(
                        PerpsTradeError.InvalidInput,
                        "amount is too small to reduce the position",
                        field = MARGIN_DELTA_FIELD,
                    )
                }
                val bounds = PerpsTradeBounds().closeBounds(present, rules, mark).getOrElse { throw it }
                val minimum = bounds.minPartialBaseAmount
                if (minimum == null || closeBase < minimum) {
                    throw PerpsTradeException(
                        PerpsTradeError.InvalidInput,
                        "amount is below the market minimum",
                        field = MARGIN_DELTA_FIELD,
                    )
                }
                val maximum = bounds.maxPartialBaseAmount
                if (maximum != null && closeBase > maximum) {
                    throw PerpsTradeException(
                        PerpsTradeError.ExceedsPosition,
                        "amount exceeds position margin",
                        field = MARGIN_DELTA_FIELD,
                    )
                }
                val legs = autoCloseLegs(request.autoClose, context.restingOrders, scale, nowMs)

                PerpsTradeIntent.Close(
                    operationId = operationId,
                    scope = scope,
                    marketId = rules.marketId,
                    positionId = present.positionId,
                    side = present.side,
                    positionBaseAmount = present.baseAmount,
                    baseAmount = closeBase,
                    maxSlippagePpm = PerpsProductRules.PRICE_SLIPPAGE_PPM,
                    clientOrderIndex = clientOrderIndex.next(nowMs),
                    autoClose = legs.spec(),
                )
            }

            is PerpsTradeRequest.Add -> {
                val present = present(context)
                val settings = context.marginSettings as? PerpsMarginSettings.Known
                    ?: throw PerpsTradeException(
                        PerpsTradeError.IncompleteContext,
                        "position leverage is unavailable",
                    )
                val legs = autoCloseLegs(request.autoClose, context.restingOrders, scale, nowMs)

                PerpsTradeIntent.Add(
                    operationId = operationId,
                    scope = scope,
                    marketId = rules.marketId,
                    positionId = present.positionId,
                    side = present.side,
                    marginBudgetQuote = request.marginDelta.toFixedPoint(scale.quoteDecimals, RoundingMode.DOWN),
                    initialMarginBps = settings.initialMarginBps,
                    clientOrderIndex = clientOrderIndex.next(nowMs),
                    order = PerpsOrderSpec.Market(maxSlippagePpm = PerpsProductRules.PRICE_SLIPPAGE_PPM),
                    autoClose = AutoCloseLegs(
                        takeProfit = legs.takeProfit,
                        stopLoss = legs.stopLoss,
                        replacedTakeProfit = legs.replacedTakeProfit.takeIf { legs.takeProfit != null },
                        replacedStopLoss = legs.replacedStopLoss.takeIf { legs.stopLoss != null },
                    ).spec(),
                )
            }

            is PerpsTradeRequest.Margin -> {
                val present = present(context)
                val amountQuote = request.amountQuote.toFixedPoint(scale.quoteDecimals, RoundingMode.DOWN)
                if (!request.add && amountQuote >= present.allocatedMarginQuote) {
                    throw PerpsTradeException(
                        PerpsTradeError.MarginBelowRequirement,
                        "amount exceeds position margin",
                        field = AMOUNT_QUOTE_FIELD,
                    )
                }

                PerpsTradeIntent.Margin(
                    operationId = operationId,
                    scope = scope,
                    marketId = rules.marketId,
                    positionId = present.positionId,
                    amountQuote = amountQuote,
                    addToPosition = request.add,
                )
            }

            is PerpsTradeRequest.OrderCancel -> PerpsTradeIntent.OrderCancel(
                operationId = operationId,
                scope = scope,
                marketId = rules.marketId,
                orderIndex = request.orderIndex,
            )

            is PerpsTradeRequest.OrderModify -> {
                val resting = context.restingOrders.firstOrNull { it.orderIndex == request.orderIndex }
                    ?: throw PerpsTradeException(PerpsTradeError.OrderNotFound, "order is no longer resting")

                PerpsTradeIntent.OrderModify(
                    operationId = operationId,
                    scope = scope,
                    marketId = rules.marketId,
                    orderIndex = request.orderIndex,
                    baseAmount = resting.remainingBaseAmount,
                    price = floorToStep(
                        value = request.price.toFixedPoint(scale.priceDecimals, RoundingMode.HALF_UP),
                        step = rules.priceTick,
                    ),
                    triggerPrice = null,
                )
            }

            is PerpsTradeRequest.AutoClose -> {
                val present = present(context)
                val legs = autoCloseLegs(
                    edit = PerpsAutoCloseEdit.Set(request.target),
                    restingOrders = context.restingOrders,
                    scale = scale,
                    nowMs = nowMs,
                )
                if (legs.isEmpty) {
                    throw PerpsTradeException(PerpsTradeError.InvalidAutoClose, "nothing to change")
                }

                PerpsTradeIntent.AutoClose(
                    operationId = operationId,
                    scope = scope,
                    marketId = rules.marketId,
                    positionId = present.positionId,
                    side = present.side,
                    autoClose = legs.spec(),
                )
            }
        }
    }

    private fun openIntent(
        request: PerpsTradeRequest.Open,
        scope: PerpsScope,
        rules: PerpsMarketRules,
        operationId: String,
        nowMs: Long,
        nextIndex: (Long) -> Long = clientOrderIndex::next,
    ): PerpsTradeIntent.Open {
        val scale = rules.scale
        val expiryUnixMs = nowMs + PerpsProductRules.AUTO_CLOSE_EXPIRY_MS
        val takeProfit = request.autoClose?.takeProfit?.toAutoCloseLeg(
            priceDecimals = scale.priceDecimals,
            expiryUnixMs = expiryUnixMs,
            clientOrderIndex = nextIndex(nowMs + TAKE_PROFIT_INDEX_OFFSET),
        )
        val stopLoss = request.autoClose?.stopLoss?.toAutoCloseLeg(
            priceDecimals = scale.priceDecimals,
            expiryUnixMs = expiryUnixMs,
            clientOrderIndex = nextIndex(nowMs + STOP_LOSS_INDEX_OFFSET),
        )

        return PerpsTradeIntent.Open(
            operationId = operationId,
            scope = scope,
            marketId = request.marketIndex.toLong(),
            side = request.side.toPerpsSide(),
            marginBudgetQuote = request.marginQuote.toFixedPoint(scale.quoteDecimals, RoundingMode.DOWN),
            initialMarginBps = PerpsProductRules.initialMarginBps(request.leverage),
            clientOrderIndex = nextIndex(nowMs),
            order = request.limitPrice?.let { price ->
                PerpsOrderSpec.Limit(
                    limitPrice = floorToStep(
                        value = price.toFixedPoint(scale.priceDecimals, RoundingMode.HALF_UP),
                        step = rules.priceTick,
                    ),
                    postOnly = false,
                    orderExpiryUnixMs = minOf(expiryUnixMs, LighterConstants.MaxTimestamp),
                )
            } ?: PerpsOrderSpec.Market(),
            autoClose = if (takeProfit != null || stopLoss != null) {
                PerpsAutoCloseSpec(takeProfit = takeProfit, stopLoss = stopLoss)
            } else {
                null
            },
        )
    }

    private fun present(context: PerpetualAccountSnapshot): PerpsPositionState.Present {
        return context.position as? PerpsPositionState.Present
            ?: throw PerpsTradeException(PerpsTradeError.NoPosition, "there is no position")
    }

    private fun autoCloseLegs(
        edit: PerpsAutoCloseEdit,
        restingOrders: List<PerpsRestingOrder>,
        scale: PerpsScale,
        nowMs: Long,
    ): AutoCloseLegs {
        val target = when (edit) {
            PerpsAutoCloseEdit.Keep -> return AutoCloseLegs()
            is PerpsAutoCloseEdit.Set -> edit.target
        }
        val expiryUnixMs = nowMs + PerpsProductRules.AUTO_CLOSE_EXPIRY_MS
        val takeProfitTrigger = target?.takeProfit?.toFixedPoint(scale.priceDecimals, RoundingMode.HALF_UP)
        val stopLossTrigger = target?.stopLoss?.toFixedPoint(scale.priceDecimals, RoundingMode.HALF_UP)
        val restingTakeProfit = restingOrders.restingLeg(PerpsOrderCategory.TakeProfit)
        val restingStopLoss = restingOrders.restingLeg(PerpsOrderCategory.StopLoss)
        if (sameLeg(takeProfitTrigger, restingTakeProfit) && sameLeg(stopLossTrigger, restingStopLoss)) {
            return AutoCloseLegs()
        }

        fun leg(trigger: Long?, indexOffset: Long): PerpsAutoCloseLeg? = trigger?.let {
            PerpsAutoCloseLeg(
                triggerPrice = it,
                expiryUnixMs = expiryUnixMs,
                clientOrderIndex = clientOrderIndex.next(nowMs + indexOffset),
            )
        }

        return AutoCloseLegs(
            takeProfit = leg(takeProfitTrigger, TAKE_PROFIT_INDEX_OFFSET),
            stopLoss = leg(stopLossTrigger, STOP_LOSS_INDEX_OFFSET),
            replacedTakeProfit = restingTakeProfit?.cancelKey(),
            replacedStopLoss = restingStopLoss?.cancelKey(),
        )
    }

    suspend fun reviewMarketState(
        walletId: String,
        marketIndex: Int,
    ): KResult<PerpsMarketState, PerpsPlanFailure> {
        val snapshot = when (val result = tradingSnapshot(marketIndex, walletId)) {
            is KResult.Ok -> result.value
            is KResult.Err -> return KResult.Err(PerpsPlanFailure.Load(result.value))
        }
        val market = snapshot.screen.market
            ?: return KResult.Err(PerpsPlanFailure.Load(PerpsError.Unknown))
        val scope = PerpsScope.review(
            walletId = walletId,
            venue = PerpsProductRules.VENUE,
            chainId = PerpsProductRules.CHAIN_ID,
            environment = PerpsProductRules.ENVIRONMENT,
        )

        return try {
            val rules = rules(market, snapshot.integrator, scope, snapshot.receivedAtMs)
            KResult.Ok(
                PerpsMarketState(
                    rules = rules,
                    context = context(snapshot, scope, rules, openOrdersReadable(walletId, marketIndex, snapshot)),
                )
            )
        } catch (e: PerpsTradeException) {
            L.e(e)
            KResult.Err(
                PerpsPlanFailure.Planner(
                    kind = e.kind,
                    message = e.message.orEmpty(),
                    field = e.field,
                    suggestion = e.suggestion,
                    priceDecimals = null,
                )
            )
        }
    }

    fun bounds(state: PerpsMarketState, mark: BigDecimal): PerpsPositionBounds? {
        val rules = state.rules
        val scale = rules.scale
        val present = state.context.position as? PerpsPositionState.Present ?: return null
        val markFixed = mark.toMarkPrice(scale.priceDecimals) ?: return null

        return try {
            val tradeBounds = PerpsTradeBounds()
            val close = tradeBounds.closeBounds(present, rules, markFixed).getOrElse { throw it }
            val margin = tradeBounds.marginBounds(state.context, rules, markFixed).getOrElse { throw it }
            val add = tradeBounds.addBounds(state.context, rules, markFixed, book = null).getOrElse { throw it }
            if (close.unavailable != null || margin.unavailable != null || add.unavailable != null) {
                return null
            }

            PerpsPositionBounds(
                minReduceMargin = close.minPartialBaseAmount
                    ?.let { reduceMargin(it, present, scale, RoundingMode.CEILING) },
                maxReduceMargin = close.maxPartialBaseAmount
                    ?.let { reduceMargin(it, present, scale, RoundingMode.DOWN) },
                maxAddMargin = BigDecimal.valueOf(add.maxMarginBudgetQuote, scale.quoteDecimals),
                maxAddMarginQuote = BigDecimal.valueOf(margin.maxAddQuote, scale.quoteDecimals),
                maxRemoveMarginQuote = BigDecimal.valueOf(margin.maxRemoveQuote, scale.quoteDecimals)
                    .setScale(MARGIN_INPUT_SCALE, RoundingMode.DOWN),
                minAdjustMarginQuote = BigDecimal.valueOf(rules.minAdjustMarginQuote, scale.quoteDecimals),
            )
        } catch (e: PerpsTradeException) {
            L.e(e)
            null
        }
    }

    private fun reduceMargin(
        baseAmount: Long,
        present: PerpsPositionState.Present,
        scale: PerpsScale,
        rounding: RoundingMode,
    ): BigDecimal {
        val margin = BigDecimal.valueOf(present.allocatedMarginQuote, scale.quoteDecimals)
        val positionBase = BigDecimal.valueOf(present.baseAmount, scale.baseDecimals)

        return BigDecimal.valueOf(baseAmount, scale.baseDecimals)
            .multiply(margin)
            .divide(positionBase, MARGIN_INPUT_SCALE, rounding)
    }

    private suspend fun prepare(
        request: PerpsTradeRequest,
        intent: PerpsTradeIntent,
        rules: PerpsMarketRules,
        context: PerpetualAccountSnapshot,
        mark: PerpsMarkPrice,
        nowMs: Long,
        applied: List<PerpsAppliedPrerequisite>,
    ): PerpetualOperation = withMarketSnapshot(request, intent, rules, mark, nowMs) { snapshot ->
        mediator.prepare(prepareRequest(intent, rules, context, snapshot, nowMs, applied))
    }

    private suspend fun <T> withMarketSnapshot(
        request: PerpsTradeRequest,
        intent: PerpsTradeIntent,
        rules: PerpsMarketRules,
        mark: PerpsMarkPrice,
        nowMs: Long,
        attempt: (PerpetualMarketSnapshot) -> PerpsRes<T>,
    ): T {
        val bookRequest = bookRequest(intent, rules, mark.price)
        if (bookRequest == null) {
            return attempt(snapshot(rules, intent, nowMs, mark.observedAtUnixMs, null, mark)).getOrElse { throw it }
        }

        var coverage = bookRequest.coverageQuote
        repeat(BOOK_READS) { read ->
            val book = when (
                val result = repository.truncatedOrderBook(
                    walletId = request.walletId,
                    marketIndex = request.marketIndex,
                    side = bookRequest.side.toOrderSide(),
                    feeRate = BigDecimal.valueOf(
                        when (intent) {
                            is PerpsTradeIntent.Open, is PerpsTradeIntent.Add ->
                                rules.effectiveTakerFeePpm(PerpsFeeKind.Open)
                            is PerpsTradeIntent.Close -> rules.effectiveTakerFeePpm(PerpsFeeKind.Close)
                            else -> rules.takerFeePpm
                        },
                        PPM_DECIMALS,
                    ),
                    notional = BigDecimal.valueOf(coverage, rules.scale.quoteDecimals),
                )
            ) {
                is KResult.Ok -> result.value
                is KResult.Err -> if (read == 0) {
                    throw PerpsLoadException(result.value)
                } else {
                    throw PerpsTradeException(
                        PerpsTradeError.NeedsMoreDepth,
                        "book read failed at coverage $coverage",
                    )
                }
            }
            // chain-kit only rejects a book from the future; one the backend stopped refreshing would
            // otherwise price the bound off a day-old level and sign an IOC that can never fill.
            val bookAgeMs = book.updatedAtMs?.let { nowMs - it }
            if (bookAgeMs != null && bookAgeMs > PerpsProductRules.POLICY.maxMarketSnapshotAgeUnixMs) {
                throw PerpsTradeException(PerpsTradeError.UnusableSnapshot, "order book is $bookAgeMs ms old")
            }
            val slice = PerpsBookSlice(
                side = bookRequest.side,
                levels = book.levels.map { (price, size) ->
                    PerpsBookLevel(
                        price = price.toFixedPoint(rules.scale.priceDecimals, RoundingMode.HALF_UP),
                        baseAmount = size.toFixedPoint(rules.scale.baseDecimals, RoundingMode.DOWN),
                    )
                },
                truncatedAtCoverage = book.truncated,
                requestedCoverageQuote = coverage,
            )
            val result = attempt(snapshot(rules, intent, nowMs, book.updatedAtMs, slice, mark))
            val error = result.error ?: return result.unwrap()
            if (error.kind == PerpsTradeError.NeedsMoreDepth && book.truncated && coverage <= Long.MAX_VALUE / 2) {
                coverage *= 2
            } else {
                throw error
            }
        }

        throw PerpsTradeException(
            PerpsTradeError.InsufficientLiquidity,
            "the book did not cover the order after $BOOK_READS reads",
        )
    }

    private fun prepareRequest(
        intent: PerpsTradeIntent,
        rules: PerpsMarketRules,
        context: PerpetualAccountSnapshot,
        snapshot: PerpetualMarketSnapshot,
        nowMs: Long,
        applied: List<PerpsAppliedPrerequisite>,
    ): PerpsPrepareRequest {
        return PerpsPrepareRequest(
            intent = intent,
            rules = rules,
            accountSnapshot = context,
            marketSnapshot = snapshot,
            planId = UUID.randomUUID().toString(),
            nowUnixMs = nowMs,
            freshnessPolicy = PerpsProductRules.POLICY,
            appliedPrerequisites = applied,
        )
    }

    private fun snapshot(
        rules: PerpsMarketRules,
        intent: PerpsTradeIntent,
        nowMs: Long,
        sourceUpdatedAtMs: Long?,
        book: PerpsBookSlice?,
        mark: PerpsMarkPrice,
    ): PerpetualMarketSnapshot {
        return PerpetualMarketSnapshot(
            snapshotId = UUID.randomUUID().toString(),
            marketId = rules.marketId,
            environment = intent.scope.environment,
            receivedAtUnixMs = nowMs,
            sourceUpdatedAtUnixMs = sourceUpdatedAtMs,
            book = book,
            mark = mark,
        )
    }

    // Open and Add walk the book on the side they buy into, a close walks the side it exits onto.
    private fun bookRequest(
        intent: PerpsTradeIntent,
        rules: PerpsMarketRules,
        mark: Long,
    ): BookRequest? {
        val request = when {
            intent is PerpsTradeIntent.Open && intent.order is PerpsOrderSpec.Market -> BookRequest(
                side = intent.side,
                coverageQuote = marginCoverageQuote(intent.marginBudgetQuote, intent.initialMarginBps),
            )

            intent is PerpsTradeIntent.Add && intent.order is PerpsOrderSpec.Market -> BookRequest(
                side = intent.side,
                coverageQuote = marginCoverageQuote(intent.marginBudgetQuote, intent.initialMarginBps),
            )

            intent is PerpsTradeIntent.Close -> BookRequest(
                side = intent.side.opposite(),
                coverageQuote = clampToLong(
                    BigDecimal(intent.baseAmount ?: intent.positionBaseAmount)
                        .multiply(BigDecimal(mark))
                        .multiply(BigDecimal(COVERAGE_MULTIPLIER))
                ),
            )

            else -> return null
        }
        val quoteUnit = BigDecimal.TEN.pow(rules.scale.quoteDecimals).toLong()

        return request.copy(coverageQuote = maxOf(quoteUnit, request.coverageQuote))
    }

    private fun marginCoverageQuote(marginBudgetQuote: Long, initialMarginBps: Int): Long {
        return clampToLong(
            BigDecimal(marginBudgetQuote)
                .multiply(BigDecimal(PerpsProductRules.IMF_TICKS))
                .divide(BigDecimal(maxOf(initialMarginBps, 1)), 0, RoundingMode.UP)
                .multiply(BigDecimal(COVERAGE_MULTIPLIER))
        )
    }

    private fun markPrice(
        live: PerpsMarkQuote?,
        fallback: BigDecimal?,
        scale: PerpsScale,
        fallbackObservedAtMs: Long,
    ): PerpsMarkPrice {
        val livePrice = live?.price.toMarkPrice(scale.priceDecimals)
        if (live != null && livePrice != null) {
            return PerpsMarkPrice(price = livePrice, observedAtUnixMs = live.observedAtMs)
        }
        val price = fallback.toMarkPrice(scale.priceDecimals)
            ?: throw PerpsTradeException(PerpsTradeError.UnusableSnapshot, "mark price is unavailable")

        return PerpsMarkPrice(price = price, observedAtUnixMs = fallbackObservedAtMs)
    }

    private companion object {
        const val MARGIN_INPUT_SCALE = 2
        const val MARGIN_DELTA_FIELD = "marginDelta"
        const val AMOUNT_QUOTE_FIELD = "amountQuote"
        const val BOOK_READS = 4
        const val COVERAGE_MULTIPLIER = 2
        const val PPM_DECIMALS = 6
        const val TAKE_PROFIT_INDEX_OFFSET = 1L
        const val STOP_LOSS_INDEX_OFFSET = 2L
        const val OPEN_ORDERS_UNAVAILABLE = "open orders are unavailable"
    }
}

private data class AutoCloseLegs(
    val takeProfit: PerpsAutoCloseLeg? = null,
    val stopLoss: PerpsAutoCloseLeg? = null,
    val replacedTakeProfit: Long? = null,
    val replacedStopLoss: Long? = null,
) {

    val isEmpty: Boolean
        get() = takeProfit == null && stopLoss == null &&
            replacedTakeProfit == null && replacedStopLoss == null

    fun spec(): PerpsAutoCloseSpec? {
        if (isEmpty) {
            return null
        }

        return PerpsAutoCloseSpec(
            takeProfit = takeProfit,
            stopLoss = stopLoss,
            replacedTakeProfitOrderIndex = replacedTakeProfit,
            replacedStopLossOrderIndex = replacedStopLoss,
        )
    }
}

private data class BookRequest(
    val side: PerpsSide,
    val coverageQuote: Long,
)

internal fun List<PerpsRestingOrder>.restingLeg(category: PerpsOrderCategory): PerpsRestingOrder? {
    return firstOrNull {
        it.category == category && it.positionTied &&
            (it.status == PerpsRestingOrderStatus.Pending || it.status == PerpsRestingOrderStatus.Open)
    }
}

private fun PerpsRestingOrder.cancelKey(): Long {
    return clientOrderIndex?.takeIf { it > 0 } ?: orderIndex
}

private fun sameLeg(trigger: Long?, resting: PerpsRestingOrder?): Boolean {
    if (trigger == null || resting == null) {
        return trigger == null && resting == null
    }
    val restingTrigger = resting.triggerPrice ?: return false
    return triggersMatch(trigger, restingTrigger)
}

private fun triggersMatch(trigger: Long, resting: Long): Boolean {
    val difference = if (trigger > resting) {
        trigger - resting
    } else {
        resting - trigger
    }

    return difference * PPM_SCALE <= abs(resting)
}

private fun floorToStep(value: Long, step: Long): Long {
    if (step <= 0L) {
        return value
    }

    return value - value % step
}

private fun Order.toRestingOrder(scale: PerpsScale): PerpsRestingOrder? {
    val index = orderIndex ?: return null
    val size = optional(baseSize, scale.baseDecimals, RoundingMode.DOWN) ?: 0L
    val filled = optional(filledBase, scale.baseDecimals, RoundingMode.UP) ?: 0L

    return PerpsRestingOrder(
        orderIndex = index,
        clientOrderIndex = clientOrderIndex,
        side = if (side == OrderSide.short) {
            PerpsSide.Short
        } else {
            PerpsSide.Long
        },
        category = category.toPerpsCategory(),
        orderType = toPerpsOrderType(),
        status = status.toPerpsStatus(),
        reduceOnly = reduceOnly ?: (
            category == OrderCategory.take_profit ||
                category == OrderCategory.stop_loss ||
                category == OrderCategory.close
            ),
        positionTied = category == OrderCategory.take_profit || category == OrderCategory.stop_loss,
        remainingBaseAmount = maxOf(0L, size - filled),
        limitPrice = optional(price, scale.priceDecimals, RoundingMode.HALF_UP)?.takeIf { it > 0L },
        // tk-perps sends "0.00" for the trigger a plain limit order does not have.
        triggerPrice = optional(triggerPrice, scale.priceDecimals, RoundingMode.HALF_UP)?.takeIf { it > 0L },
        expiryUnixMs = expiresAt?.toInstant()?.toEpochMilli()?.takeIf { it > 0L },
    )
}

private fun OrderCategory?.toPerpsCategory(): PerpsOrderCategory {
    return when (this) {
        OrderCategory.close -> PerpsOrderCategory.Close
        OrderCategory.take_profit -> PerpsOrderCategory.TakeProfit
        OrderCategory.stop_loss -> PerpsOrderCategory.StopLoss
        OrderCategory.liquidation -> PerpsOrderCategory.Liquidation
        else -> PerpsOrderCategory.Open
    }
}

private fun Order.toPerpsOrderType(): PerpsRestingOrderType {
    return when (category) {
        OrderCategory.take_profit -> PerpsRestingOrderType.TakeProfit
        OrderCategory.stop_loss -> PerpsRestingOrderType.StopLoss
        OrderCategory.liquidation -> PerpsRestingOrderType.Liquidation
        else -> if (type == OrderType.market) {
            PerpsRestingOrderType.Market
        } else {
            PerpsRestingOrderType.Limit
        }
    }
}

private fun OrderStatus?.toPerpsStatus(): PerpsRestingOrderStatus {
    return when (this) {
        OrderStatus.new -> PerpsRestingOrderStatus.Pending
        OrderStatus.filled -> PerpsRestingOrderStatus.Filled
        OrderStatus.canceled, OrderStatus.rejected -> PerpsRestingOrderStatus.Canceled
        else -> PerpsRestingOrderStatus.Open
    }
}

private fun PerpsOrderSide.toPerpsSide(): PerpsSide {
    return when (this) {
        PerpsOrderSide.LONG -> PerpsSide.Long
        PerpsOrderSide.SHORT -> PerpsSide.Short
    }
}

private fun PerpsSide.toOrderSide(): PerpsOrderSide {
    return when (this) {
        PerpsSide.Long -> PerpsOrderSide.LONG
        PerpsSide.Short -> PerpsOrderSide.SHORT
    }
}

private fun BigDecimal.toAutoCloseLeg(
    priceDecimals: Int,
    expiryUnixMs: Long,
    clientOrderIndex: Long,
): PerpsAutoCloseLeg {
    return PerpsAutoCloseLeg(
        triggerPrice = toFixedPoint(priceDecimals, RoundingMode.HALF_UP),
        expiryUnixMs = expiryUnixMs,
        clientOrderIndex = clientOrderIndex,
    )
}

private fun BigDecimal?.toMarkPrice(priceDecimals: Int): Long? {
    return this?.takeIf { it.signum() > 0 }?.toFixedPoint(priceDecimals, RoundingMode.HALF_UP)?.takeIf { it > 0 }
}

private fun BigDecimal.toFixedPoint(decimals: Int, mode: RoundingMode): Long {
    return PerpsFixedPoint.parse(setScale(decimals, mode).toPlainString(), decimals)
}

private fun required(value: String?, decimals: Int, field: String, mode: RoundingMode): Long {
    val text = value?.takeIf { it.isNotBlank() } ?: missing(field)
    val amount = text.toBigDecimalOrNull() ?: missing(field)
    return amount.toFixedPoint(decimals, mode)
}

private fun optional(value: String?, decimals: Int, mode: RoundingMode): Long? {
    return value?.takeIf { it.isNotBlank() }?.toBigDecimalOrNull()?.toFixedPoint(decimals, mode)
}

private fun feePpm(value: String?, field: String): Long {
    val percent = value?.toBigDecimalOrNull() ?: missing(field)
    return PerpsProductRules.percentToPpm(percent)
}

private fun missing(field: String): Nothing {
    throw PerpsTradeException(PerpsTradeError.InvalidRules, "$field is missing")
}

private fun PerpsTradingSnapshot.hasClockSkew(): Boolean {
    val offsetMs = (serverTimeMs ?: return false) - receivedAtMs
    return offsetMs > PerpsProductRules.POLICY.maxFutureSkewUnixMs ||
        -offsetMs > PerpsProductRules.POLICY.maxMarketSnapshotAgeUnixMs
}

private fun PerpsSide.opposite(): PerpsSide {
    return when (this) {
        PerpsSide.Long -> PerpsSide.Short
        PerpsSide.Short -> PerpsSide.Long
    }
}

private fun clampToLong(value: BigDecimal): Long {
    return value.min(BigDecimal(Long.MAX_VALUE)).toLong()
}

private const val PPM_SCALE = 1_000_000L
