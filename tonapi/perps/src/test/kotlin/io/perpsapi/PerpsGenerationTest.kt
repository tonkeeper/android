package io.perpsapi

import io.perpsapi.models.QuotePayloadMeta
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PerpsGenerationTest {
    @Test
    fun payloadDataKeepsBothCalldataAndStructuredSignatures() {
        for (data in listOf("\"0x1234\"", """{"types":{"Future":[]},"message":{"nonce":1}}""")) {
            val meta = Json.decodeFromString<QuotePayloadMeta>("""{"data":$data}""")
            assertEquals(Json.parseToJsonElement(data), meta.data)
            assertEquals(
                Json.parseToJsonElement("""{"data":$data}"""),
                Json.parseToJsonElement(Json.encodeToString(QuotePayloadMeta.serializer(), meta)),
            )
        }
    }
}
