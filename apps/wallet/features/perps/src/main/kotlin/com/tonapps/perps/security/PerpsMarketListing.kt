package com.tonapps.perps.security

import com.tonapps.bus.core.IssueHelper
import com.tonapps.log.L
import com.tonapps.perps.data.PerpsPosition
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

class PerpsMarketListing(
    private val securityConfig: PerpsSecurityConfigRepository,
    private val report: (Throwable, Map<String, String>) -> Unit = IssueHelper::recordException,
) {

    private val reportedMarkets = ConcurrentHashMap.newKeySet<Int>()

    suspend fun signedSymbols(): Map<Int, String>? {
        return listingConfig()?.markets?.mapValues { (_, market) -> market.symbol }
    }

    suspend fun isListed(marketIndex: Int, catalogSymbol: String): Boolean {
        val config = listingConfig() ?: return true
        return config.markets[marketIndex]?.symbol == catalogSymbol
    }

    suspend fun reportUnlisted(positions: List<PerpsPosition>) {
        if (positions.isEmpty()) {
            return
        }
        val config = listingConfig() ?: return
        positions
            .filter { it.marketIndex !in config.markets && reportedMarkets.add(it.marketIndex) }
            .forEach { position ->
                val keys = mapOf(
                    MARKET_INDEX_KEY to position.marketIndex.toString(),
                    SYMBOL_KEY to position.symbol,
                    CONFIG_VERSION_KEY to config.version.toString(),
                )
                try {
                    report(PerpsUnlistedPositionException(position.marketIndex), keys)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    L.e(e)
                }
            }
    }

    private suspend fun listingConfig(): PerpsSecurityConfig? {
        return securityConfig.verifiedWithoutWaiting()?.takeIf { it.markets.isNotEmpty() }
    }

    private companion object {
        const val MARKET_INDEX_KEY = "perps_market_index"
        const val SYMBOL_KEY = "perps_symbol"
        const val CONFIG_VERSION_KEY = "perps_security_version"
    }
}

class PerpsUnlistedPositionException(marketIndex: Int) :
    IllegalStateException("perps position on market $marketIndex, which the signed perps config does not list")
