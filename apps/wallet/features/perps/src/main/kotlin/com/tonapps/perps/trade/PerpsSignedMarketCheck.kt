package com.tonapps.perps.trade

import com.tonapps.chainkit.perpetual.PerpsTradeError
import com.tonapps.chainkit.perpetual.PerpsTradeException
import com.tonapps.perps.data.MIN_LEVERAGE
import com.tonapps.perps.data.toWireDecimalOrNull
import com.tonapps.perps.security.PerpsSecurityConfig
import com.tonapps.perps.security.PerpsSignedMarket
import io.perpsapi.models.Market
import java.math.BigDecimal

internal const val SIGNED_MARKET_FIELD = "perps_security"
private const val MARKET_INDEX = "market_index"
private const val SYMBOL = "symbol"
private const val PRICE_DECIMALS = "price_decimals"
private const val SIZE_DECIMALS = "size_decimals"
private const val MIN_SIZE_BASE = "min_size_base"
private const val MIN_SIZE_QUOTE = "min_size_quote"
private const val MAX_LEVERAGE = "max_leverage"

internal object PerpsSignedMarketCheck {

    fun signedMarket(marketId: Int, market: Market, config: PerpsSecurityConfig?): PerpsSignedMarket {
        if (marketId !in 0..Short.MAX_VALUE) {
            throw refusal(PerpsTradeError.UnsupportedMarket, "market $marketId is out of the signable range")
        }
        if (config == null) {
            throw PerpsTradeException(PerpsTradeError.UnsupportedMarket, "no verified perps security config")
        }
        val signed = config.markets[marketId] ?: throw refusal(
            PerpsTradeError.UnsupportedMarket,
            "market $marketId is not in the signed perps config",
        )
        if (signed.maxLeverage < MIN_LEVERAGE) {
            throw refusal(
                PerpsTradeError.UnsupportedMarket,
                "the signed perps config allows no leverage on market $marketId",
            )
        }
        requireSame(marketId, MARKET_INDEX, market.marketIndex, marketId)
        if (market.symbol != signed.symbol) {
            throw mismatch(marketId, SYMBOL, market.symbol, signed.symbol)
        }
        requireSame(marketId, PRICE_DECIMALS, market.priceDecimals, signed.priceDecimals)
        requireSame(marketId, SIZE_DECIMALS, market.sizeDecimals, signed.sizeDecimals)
        requireSameAmount(marketId, MIN_SIZE_BASE, market.minSizeBase, signed.minBaseAmount)
        requireSameAmount(marketId, MIN_SIZE_QUOTE, market.minSizeQuote, signed.minQuoteAmount)
        requireSame(marketId, MAX_LEVERAGE, market.maxLeverage, signed.maxLeverage)
        return signed
    }

    private fun requireSame(marketId: Int, field: String, served: Int?, signed: Int) {
        if (served != signed) {
            throw mismatch(marketId, field, served?.toString(), signed.toString())
        }
    }

    private fun requireSameAmount(marketId: Int, field: String, served: String?, signed: BigDecimal) {
        if (served.toWireDecimalOrNull()?.compareTo(signed) != 0) {
            throw mismatch(marketId, field, served, signed.toPlainString())
        }
    }

    private fun mismatch(marketId: Int, field: String, served: String?, signed: String): PerpsTradeException {
        return refusal(
            PerpsTradeError.InvalidRules,
            "market $marketId $field $served differs from the signed perps config's $signed",
        )
    }

    private fun refusal(kind: PerpsTradeError, message: String): PerpsTradeException {
        return PerpsTradeException(kind, message, SIGNED_MARKET_FIELD)
    }
}
