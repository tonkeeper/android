package com.tonapps.wallet.data.multichain.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeEnvelopeParserTest {

    @Test
    fun `parses envelope and ignores unknown fields`() {
        val payload = """
            {"v":1,"event":"balance.hint","wallet_id":"aia3n6","seq":1788188409363,
             "ts":"2026-08-31T12:20:09.363Z","data":{"chain":"ton","assets":2}}
        """.trimIndent().encodeToByteArray()

        val envelope = RealtimeEnvelopeParser.parse(payload).getOrThrow()

        assertEquals(1, envelope.version)
        assertEquals("balance.hint", envelope.event)
        assertEquals("aia3n6", envelope.walletId)
        assertEquals(1788188409363L, envelope.seq)
    }

    @Test
    fun `fails on malformed json`() {
        val result = RealtimeEnvelopeParser.parse("{not json".encodeToByteArray())

        assertTrue(result.isFailure)
    }

    @Test
    fun `fails when required field is missing`() {
        val result = RealtimeEnvelopeParser.parse("""{"v":1,"event":"activity.hint"}""".encodeToByteArray())

        assertTrue(result.isFailure)
    }
}
