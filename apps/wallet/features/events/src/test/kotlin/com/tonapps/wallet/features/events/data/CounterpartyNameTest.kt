package com.tonapps.wallet.features.events.data

import io.walletapi.models.Activity
import io.walletapi.models.ActivityDirection
import io.walletapi.models.ActivityParty
import io.walletapi.models.ActivityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime

class CounterpartyNameTest {

    @Test
    fun `send shows the recipient name from the to party`() {
        val event = map(
            activity(
                type = "send",
                direction = ActivityDirection.out,
                fromAddress = "0xdeprecated",
                from = ActivityParty(address = "0xfrom"),
                to = ActivityParty(address = "0xto", name = "alice.ton"),
            ),
        )

        assertEquals("0xfrom", event.fromAddress)
        assertEquals("0xto", event.toAddress)
        assertNull(event.fromName)
        assertEquals("alice.ton", event.counterpartyName)
    }

    @Test
    fun `receive shows the sender name from the from party`() {
        val event = map(
            activity(
                type = "receive",
                direction = ActivityDirection.`in`,
                from = ActivityParty(address = "0xfrom", name = "bob.t.me"),
                to = ActivityParty(address = "0xto", name = "alice.ton"),
            ),
        )

        assertEquals("bob.t.me", event.counterpartyName)
    }

    @Test
    fun `swap keeps the address because its recipient is the own wallet`() {
        val event = map(
            activity(
                type = "swap",
                direction = ActivityDirection.out,
                to = ActivityParty(address = "0xto", name = "alice.ton"),
            ),
        )

        assertNull(event.counterpartyName)
    }

    @Test
    fun `flat addresses are kept when the backend omits the parties`() {
        val event = map(
            activity(
                type = "send",
                direction = ActivityDirection.out,
                fromAddress = "0xfrom",
                toAddress = "0xto",
            ),
        )

        assertEquals("0xfrom", event.fromAddress)
        assertEquals("0xto", event.toAddress)
        assertNull(event.counterpartyName)
    }

    private fun map(activity: Activity) = requireNotNull(mapActivityToEntity(activity, showsLoyaltyReward = false) { null })

    private fun activity(
        type: String,
        direction: ActivityDirection,
        fromAddress: String? = null,
        toAddress: String? = null,
        from: ActivityParty? = null,
        to: ActivityParty? = null,
    ) = Activity(
        activityType = type,
        status = ActivityStatus.confirmed,
        blockTime = OffsetDateTime.parse("2026-11-03T12:00:00Z"),
        fromChain = "ton",
        toChain = "ton",
        direction = direction,
        txIds = emptyList(),
        fromAddress = fromAddress,
        toAddress = toAddress,
        from = from,
        to = to,
    )
}
