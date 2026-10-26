package io.tradingapi

import io.tradingapi.models.AssetCapability
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TradingGenerationTest {
    @Test
    fun futureCapabilityDoesNotDiscardKnownCapabilities() {
        val capabilities = Json.decodeFromString<List<AssetCapability>>("""["swap","future-capability"]""")
        assertEquals(listOf(AssetCapability.swap, AssetCapability.unknown), capabilities)
        assertEquals("\"swap\"", Json.encodeToString(AssetCapability.serializer(), capabilities.first()))
    }
}
