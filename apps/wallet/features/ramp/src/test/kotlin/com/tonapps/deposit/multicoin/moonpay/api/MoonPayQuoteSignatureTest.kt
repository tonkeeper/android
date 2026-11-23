package com.tonapps.deposit.multicoin.moonpay.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class MoonPayQuoteSignatureTest {

    @Test
    fun `a complete quote lacks nothing`() {
        val payload = MoonPayQuoteSignature.payload(jwt(COMPLETE))!!

        assertEquals(emptyList<String>(), MoonPayQuoteSignature.missingPaymentFrameFields(payload))
    }

    @Test
    fun `missing and null fields are reported by their path`() {
        val payload = MoonPayQuoteSignature.payload(
            jwt(
                """
                {"signature":"s","wallet":{},"paymentMethod":{"type":null},
                 "source":{"amount":"100","asset":{"code":"usd"}},"destination":{"asset":{"code":"ton"}}}
                """,
            ),
        )!!

        assertEquals(
            listOf("wallet.address", "paymentMethod.type", "destination.amount"),
            MoonPayQuoteSignature.missingPaymentFrameFields(payload),
        )
    }

    @Test
    fun `a signature without a readable payload has none`() {
        assertNull(MoonPayQuoteSignature.payload("opaque"))
        assertNull(MoonPayQuoteSignature.payload("header.%%%.sig"))
        assertNull(MoonPayQuoteSignature.payload(jwt("[1, 2]")))
    }

    private fun jwt(payload: String): String {
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.trimIndent().toByteArray())
        return "eyJhbGciOiJIUzI1NiJ9.$encoded.sig"
    }

    private companion object {
        const val COMPLETE = """
            {"signature":"s","wallet":{"address":"UQ"},"paymentMethod":{"type":"card"},
             "source":{"amount":"100","asset":{"code":"usd"}},
             "destination":{"amount":"20","asset":{"code":"ton"}},"executable":true}
        """
    }
}
