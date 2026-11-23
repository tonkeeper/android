package com.tonapps.bus.core.contract

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RedMetadataTest {

    @Test
    fun unknownValuesAreLeftOutAndKeysSorted() {
        val json = RedMetadata.json(
            "token_symbol" to "TON",
            "fee_paid_in" to null,
            "amount" to 1.5,
            "source" to "wallet_screen",
        )

        assertEquals("""{"amount":1.5,"source":"wallet_screen","token_symbol":"TON"}""", json)
    }

    @Test
    fun noKnownValueSendsNoMetadata() {
        assertNull(RedMetadata.json())
        assertNull(RedMetadata.json("amount" to null, "source" to null))
    }

    @Test
    fun numbersAndBooleansStayUnquoted() {
        assertEquals("""{"a":2,"b":true,"c":0.25}""", RedMetadata.json("a" to 2, "b" to true, "c" to 0.25))
    }

    @Test
    fun aNonFiniteAmountIsLeftOut() {
        assertNull(RedMetadata.json("amount" to Double.NaN, "to_amount" to Double.POSITIVE_INFINITY))
    }

    @Test
    fun theOutputIsValidJsonThatKeepsUnicodeAndEscapes() {
        val json = RedMetadata.json(
            "token_symbol" to "USD₮",
            "asset" to "ton/mainnet/jetton/0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe",
            "source" to "quote\"and\\slash",
        )!!

        val parsed = Json.parseToJsonElement(json).jsonObject
        assertEquals("USD₮", parsed.getValue("token_symbol").jsonPrimitive.content)
        assertEquals(
            "ton/mainnet/jetton/0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe",
            parsed.getValue("asset").jsonPrimitive.content,
        )
        assertEquals("quote\"and\\slash", parsed.getValue("source").jsonPrimitive.content)
    }
}
