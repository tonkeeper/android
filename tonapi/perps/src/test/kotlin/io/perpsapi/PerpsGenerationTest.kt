package io.perpsapi

import io.Serializer
import io.perpsapi.models.ClientMetricPositionClose
import io.perpsapi.models.ClientMetricPositionOpen
import io.perpsapi.models.QuotePayloadMeta
import io.perpsapi.models.RecordClientMetricsRequest
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

    @Test
    fun clientMetricsEncodeAsTheDocumentedBody() {
        val request = RecordClientMetricsRequest(
            metrics = listOf(
                ClientMetricPositionOpen(id = "a", type = "position_open", durationMs = 2140),
                ClientMetricPositionClose(id = "b", type = "position_close", durationMs = 1830),
            ),
        )
        assertEquals(
            Json.parseToJsonElement(
                """{"metrics":[{"id":"a","type":"position_open","duration_ms":2140},{"id":"b","type":"position_close","duration_ms":1830}]}""",
            ),
            Json.parseToJsonElement(Serializer.JSON.encodeToString(RecordClientMetricsRequest.serializer(), request)),
        )
    }
}
