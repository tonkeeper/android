package com.tonapps.perps.trade

import com.tonapps.perps.security.PerpsSecurityBlocks
import com.tonapps.perps.security.PerpsSecurityConfigRepository
import com.tonapps.perps.security.PerpsSecurityConfigStore
import io.perpsapi.models.Market
import kotlinx.serialization.json.JsonObject
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters

object PerpsSignedMarkets {

    fun listing(vararg markets: Market): PerpsSecurityConfigRepository {
        return repository(markets.map(::entry))
    }

    fun following(served: () -> Market): PerpsSecurityConfigRepository {
        return signedBy(PerpsSecurityBlocks.newKey()) { listOf(entry(served())) }
    }

    fun repository(markets: List<JsonObject>?): PerpsSecurityConfigRepository {
        return signedBy(PerpsSecurityBlocks.newKey()) { markets }
    }

    private fun signedBy(key: Ed25519PrivateKeyParameters, markets: () -> List<JsonObject>?): PerpsSecurityConfigRepository {
        return PerpsSecurityConfigRepository(
            servedBlock = { PerpsSecurityBlocks.signed(key, PerpsSecurityBlocks.payload(markets = markets())) },
            store = NothingKept,
            trustedKeys = PerpsSecurityBlocks.trusting(key),
        )
    }

    fun entry(market: Market): JsonObject {
        val priceDecimals = checkNotNull(market.priceDecimals)
        val sizeDecimals = checkNotNull(market.sizeDecimals)
        return PerpsSecurityBlocks.market(
            marketId = checkNotNull(market.marketIndex),
            symbol = checkNotNull(market.symbol),
            priceDecimals = priceDecimals,
            sizeDecimals = sizeDecimals,
            minBaseAmount = checkNotNull(market.minSizeBase),
            minQuoteAmount = checkNotNull(market.minSizeQuote),
            maxLeverage = checkNotNull(market.maxLeverage),
        )
    }

    private object NothingKept : PerpsSecurityConfigStore {

        override fun keptBlock(): String? = null

        override fun keep(block: String) = Unit
    }
}
