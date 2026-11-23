package com.tonapps.deposit.multicoin.moonpay

import com.tonapps.deposit.multicoin.moonpay.api.MoonPayBuyQuote
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonPayPaymentDisclosuresTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `no disclosures show nothing`() {
        assertTrue(MoonPayPaymentDisclosures.texts(disclosures("[]")).isEmpty())
    }

    @Test
    fun `both EEA disclosures show both in order`() {
        val texts = MoonPayPaymentDisclosures.texts(
            disclosures("""[{"id":"eea-crypto-asset-risk","version":"1"},{"id":"eea-unregulated-stablecoin-risk","version":"1"}]"""),
        )

        assertEquals(2, texts.size)
        assertTrue(texts[0].text.startsWith("By continuing, you agree to transact with MoonPay Europe"))
        assertTrue(texts[1].text.startsWith("Important: You are about to transact in a stablecoin"))
        assertEquals(
            listOf("Terms of Use", "Privacy Policy", "Learn more", "review the whitepaper"),
            texts[1].links.map { texts[1].text.substring(it.start, it.end) },
        )
    }

    @Test
    fun `terms of use is a link to MoonPay's terms`() {
        val text = MoonPayPaymentDisclosures.texts(disclosures("""[{"id":"us-transaction-finality","version":"1"}]""")).single()

        assertFalse(text.text.contains("]("))
        assertTrue(text.text.startsWith("I agree to MoonPay’s Terms of Use and understand"))
        assertTrue(text.text.endsWith("with no recourse."))
        val link = text.links.single()
        assertEquals("Terms of Use", text.text.substring(link.start, link.end))
        assertEquals("https://www.moonpay.com/legal/terms", link.url)
    }

    @Test
    fun `every text keeps its links and loses the markup`() {
        MoonPayPaymentDisclosures.Disclosure.entries.forEach { disclosure ->
            val text = MoonPayPaymentDisclosures.texts(listOf(MoonPayBuyQuote.PaymentDisclosure(disclosure.id, "1"))).single()
            assertFalse(disclosure.id, text.text.contains('[') || text.text.contains("]("))
            assertTrue(disclosure.id, text.links.isNotEmpty())
            assertTrue(disclosure.id, text.links.all { it.url.startsWith("https://") })
        }
    }

    @Test
    fun `a repeated disclosure shows once`() {
        val texts = MoonPayPaymentDisclosures.texts(
            disclosures("""[{"id":"gateway-token","version":"1"},{"id":"eea-crypto-asset-risk"},{"id":"gateway-token","version":"1"}]"""),
        )

        assertEquals(2, texts.size)
        assertTrue(texts[0].text.startsWith("By proceeding, you agree to two steps under these terms:"))
        assertTrue(texts[1].text.startsWith("By continuing"))
    }

    @Test
    fun `an unknown disclosure falls back to the transaction finality text and is reported, as is another version`() {
        val reports = mutableListOf<String>()
        val texts = MoonPayPaymentDisclosures.texts(
            disclosures("""[{"id":"some-new-disclosure","version":"1"},{"id":"us-transaction-finality","version":"2"},{"id":"gateway-token","version":"1"}]"""),
            reports::add,
        )

        assertEquals(2, texts.size)
        assertTrue(texts[0].text.startsWith("I agree to MoonPay’s Terms of Use"))
        assertEquals(listOf("unknown moonpay disclosure some-new-disclosure v1", "unknown moonpay disclosure us-transaction-finality v2"), reports)
    }

    @Test
    fun `a malformed disclosure is shown as an unknown one and keeps the others`() {
        val reports = mutableListOf<String>()
        val quote = quote("""[{"id":"eea-crypto-asset-risk","version":1},{"version":"1"},{"id":"gateway-token"}]""")

        val texts = MoonPayPaymentDisclosures.texts(quote.paymentDisclosures.orEmpty(), reports::add)

        assertEquals(3, quote.paymentDisclosures?.size)
        assertEquals("1", quote.paymentDisclosures?.first()?.version)
        assertEquals(3, texts.size)
        assertTrue(texts[0].text.startsWith("By continuing"))
        assertTrue(texts[1].text.startsWith("I agree to MoonPay’s Terms of Use"))
        assertTrue(texts[2].text.startsWith("By proceeding"))
        assertEquals(1, reports.size)
        assertTrue(reports.single().startsWith("undecodable moonpay disclosure"))
    }

    @Test
    fun `a quote without disclosures or with none decodes`() {
        assertEquals(null, quote(null).paymentDisclosures)
        assertEquals(null, quote("null").paymentDisclosures)
        assertTrue(quote("[]").paymentDisclosures!!.isEmpty())
    }

    @Test
    fun `the quote's disclosures survive the trip to the payment screen`() {
        val quote = quote("""[{"id":"eea-crypto-asset-risk","version":"1"}]""")

        assertEquals(quote, json.decodeFromString<MoonPayBuyQuote>(json.encodeToString(quote)))
    }

    private fun disclosures(json: String): List<MoonPayBuyQuote.PaymentDisclosure> {
        return quote(json).paymentDisclosures.orEmpty()
    }

    private fun quote(disclosures: String?): MoonPayBuyQuote {
        val field = disclosures?.let { ""","paymentDisclosures":$it""" }.orEmpty()
        return json.decodeFromString(
            """{"source":{"asset":{"code":"usd"}},"destination":{"asset":{"code":"ton"}},"signature":"s"$field}""",
        )
    }
}
