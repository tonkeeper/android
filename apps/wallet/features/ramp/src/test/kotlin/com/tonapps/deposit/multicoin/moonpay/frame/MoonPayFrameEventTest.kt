package com.tonapps.deposit.multicoin.moonpay.frame

import com.tonapps.deposit.multicoin.moonpay.api.MoonPayErrorDetail
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayPaymentMethodAvailability
import com.tonapps.deposit.multicoin.moonpay.api.MoonPaySavedCard
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonPayFrameEventTest {

    @Test
    fun `active connection`() {
        val event = event(
            """{"version":2,"meta":{"channelId":"ch_1"},"kind":"complete","payload":{
                "status":"active",
                "customer":{"id":"cus_1","country":"USA","administrativeArea":"NY"},
                "credentials":"ZW5j"
            }}""",
        )

        assertEquals(
            completion(
                MoonPayFrameCompletion.Connection(
                    MoonPayConnectionResult(
                        status = MoonPayConnectionStatus.Active,
                        encryptedCredentials = "ZW5j",
                        customer = MoonPayCustomer(id = "cus_1", country = "USA", administrativeArea = "NY"),
                    ),
                ),
            ),
            event,
        )
    }

    @Test
    fun `connection required with a mismatch`() {
        val event = event(
            """{"meta":{"channelId":"ch_1"},"kind":"complete","payload":{"status":"connectionRequired","credentials":"ZW5j","mismatch":true}}""",
        )

        assertEquals(
            completion(
                MoonPayFrameCompletion.Connection(
                    MoonPayConnectionResult(
                        status = MoonPayConnectionStatus.ConnectionRequired,
                        encryptedCredentials = "ZW5j",
                        mismatch = true,
                    ),
                ),
            ),
            event,
        )
    }

    @Test
    fun `status-only connection states`() {
        assertEquals(
            completion(
                MoonPayFrameCompletion.Connection(
                    MoonPayConnectionResult(status = MoonPayConnectionStatus.TermsAcceptanceRequired),
                ),
            ),
            event("""{"meta":{"channelId":"ch_1"},"kind":"complete","payload":{"status":"termsAcceptanceRequired"}}"""),
        )
        assertEquals(
            completion(
                MoonPayFrameCompletion.Connection(
                    MoonPayConnectionResult(status = MoonPayConnectionStatus.Failed, reason = "kyc_rejected"),
                ),
            ),
            event("""{"meta":{"channelId":"ch_1"},"kind":"complete","payload":{"status":"failed","reason":"kyc_rejected"}}"""),
        )
        assertEquals(
            completion(MoonPayFrameCompletion.Status("somethingNew")),
            event("""{"meta":{"channelId":"ch_1"},"kind":"complete","payload":{"status":"somethingNew"}}"""),
        )
    }

    @Test
    fun `transaction created and completed`() {
        assertEquals(
            MoonPayFrameEvent.TransactionCreated(MoonPayTransaction(id = "txn_01", status = "pending")),
            event(
                """{"meta":{"channelId":"ch_1"},"kind":"transactionCreated","payload":{"transaction":{"id":"txn_01","status":"pending"}}}""",
            ),
        )

        val failed = event(
            """{"meta":{"channelId":"ch_1"},"kind":"complete","payload":{"transaction":{"id":"txn_02","status":"failed","failureCode":"authorizationDeclined","failureReason":"Declined"}}}""",
        )

        val transaction = ((failed as MoonPayFrameEvent.Complete).completion as MoonPayFrameCompletion.Transaction).transaction
        assertTrue(transaction.isFailed)
        assertEquals("authorizationDeclined", transaction.failureCode)
        assertEquals("Declined", transaction.failureReason)
    }

    @Test
    fun `challenge`() {
        assertEquals(
            MoonPayFrameEvent.Challenge("https://platform.moonpay.com/platform/v1/challenge?challengeToken=t"),
            event(
                """{"meta":{"channelId":"ch_1"},"kind":"challenge","payload":{"kind":"frame","url":"https://platform.moonpay.com/platform/v1/challenge?challengeToken=t"}}""",
            ),
        )
    }

    @Test
    fun `challenge completion branches on the flow`() {
        assertEquals(
            completion(MoonPayFrameCompletion.Transaction(MoonPayTransaction(id = "txn_03", status = "completed"))),
            event(
                """{"meta":{"channelId":"ch_1"},"kind":"complete","payload":{"flow":"buy","transaction":{"id":"txn_03","status":"completed"}}}""",
            ),
        )
        assertEquals(
            completion(MoonPayFrameCompletion.Identity("idn_1")),
            event("""{"meta":{"channelId":"ch_1"},"kind":"complete","payload":{"flow":"identity","identityId":"idn_1"}}"""),
        )
        assertEquals(
            completion(MoonPayFrameCompletion.Status("upgraded")),
            event(
                """{"meta":{"channelId":"ch_1"},"kind":"complete","payload":{"flow":"guest_checkout_limit_upgrade","status":"upgraded"}}""",
            ),
        )
    }

    @Test
    fun `an identity challenge still in progress reports its status`() {
        assertEquals(
            completion(MoonPayFrameCompletion.Status("pending")),
            event("""{"meta":{"channelId":"ch_1"},"kind":"complete","payload":{"flow":"identity","status":"pending"}}"""),
        )
    }

    @Test
    fun `a widget transaction completes as complete`() {
        assertTrue(MoonPayTransaction(id = "txn_1", status = "complete").isCompleted)
    }

    @Test
    fun `a buy completion reports an upgraded session`() {
        assertEquals(
            completion(
                MoonPayFrameCompletion.Transaction(
                    MoonPayTransaction(id = "txn_03", status = "pending"),
                    sessionUpgraded = true,
                ),
            ),
            event(
                """{"meta":{"channelId":"ch_1"},"kind":"complete","payload":{"flow":"buy","transaction":{"id":"txn_03","status":"pending"},"sessionUpgraded":true}}""",
            ),
        )
    }

    @Test(expected = MoonPayFrameException.Malformed::class)
    fun `a buy challenge without a transaction is malformed`() {
        event("""{"meta":{"channelId":"ch_1"},"kind":"complete","payload":{"flow":"buy"}}""")
    }

    @Test
    fun `add-card completion`() {
        val event = event(
            """{"meta":{"channelId":"ch_1"},"kind":"complete","payload":{"card":{
                "id":"a1b2","type":"card","cardType":"credit","brand":"visa","last4":"4242",
                "expirationMonth":"12","expirationYear":2027,"availability":{"active":true}
            }}}""",
        )

        assertEquals(
            completion(
                MoonPayFrameCompletion.Card(
                    MoonPaySavedCard(
                        id = "a1b2",
                        type = "card",
                        cardType = "credit",
                        brand = "visa",
                        last4 = "4242",
                        expirationMonth = "12",
                        expirationYear = "2027",
                        availability = MoonPayPaymentMethodAvailability(active = true),
                    ),
                ),
            ),
            event,
        )
    }

    @Test
    fun `add-card completion carries availability reasons`() {
        val event = event(
            """{"meta":{"channelId":"ch_1"},"kind":"complete","payload":{"card":{"id":"c3d4","brand":"mastercard","last4":"8910","availability":{"active":false,"reasons":["card_expired"]}}}}""",
        )

        val card = ((event as MoonPayFrameEvent.Complete).completion as MoonPayFrameCompletion.Card).card
        assertEquals(MoonPayPaymentMethodAvailability(active = false, reasons = listOf("card_expired")), card.availability)
    }

    @Test
    fun `error`() {
        assertEquals(
            MoonPayFrameEvent.Error(
                MoonPayFrameError(
                    code = "validationError",
                    message = "Bad request",
                    errors = listOf(MoonPayErrorDetail(code = "required", message = "clientToken is required")),
                ),
            ),
            event(
                """{"meta":{"channelId":"ch_1"},"kind":"error","payload":{"code":"validationError","message":"Bad request","errors":[{"code":"required","message":"clientToken is required"}]}}""",
            ),
        )
        assertEquals(
            MoonPayFrameEvent.Error(MoonPayFrameError(code = "unknown", message = "Unknown error")),
            event("""{"meta":{"channelId":"ch_1"},"kind":"error"}"""),
        )
    }

    @Test
    fun `lifecycle and unknown kinds`() {
        assertEquals(MoonPayFrameEvent.Handshake, event("""{"meta":{"channelId":"ch_1"},"kind":"handshake"}"""))
        assertEquals(MoonPayFrameEvent.Ready, event("""{"meta":{"channelId":"ch_1"},"kind":"ready"}"""))
        assertEquals(MoonPayFrameEvent.ButtonPressed, event("""{"meta":{"channelId":"ch_1"},"kind":"buttonPressed"}"""))
        assertEquals(
            MoonPayFrameEvent.Cancelled(buildJsonObject { put("flow", "buy") }),
            event("""{"meta":{"channelId":"ch_1"},"kind":"cancelled","payload":{"flow":"buy"}}"""),
        )
        assertEquals(
            MoonPayFrameEvent.Unknown("somethingElse", buildJsonObject { put("a", 1) }),
            event("""{"meta":{"channelId":"ch_1"},"kind":"somethingElse","payload":{"a":1}}"""),
        )
        assertEquals(
            completion(MoonPayFrameCompletion.Raw(null)),
            event("""{"meta":{"channelId":"ch_1"},"kind":"complete"}"""),
        )
    }

    private fun event(json: String): MoonPayFrameEvent = MoonPayFrameEvent.from(MoonPayFrameMessage.decode(json))

    private fun completion(completion: MoonPayFrameCompletion): MoonPayFrameEvent {
        return MoonPayFrameEvent.Complete(completion)
    }
}
