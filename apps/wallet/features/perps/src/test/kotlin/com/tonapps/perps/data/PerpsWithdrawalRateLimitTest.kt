package com.tonapps.perps.data

import io.infrastructure.ClientError
import io.infrastructure.ClientException
import io.infrastructure.ServerError
import io.infrastructure.ServerException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PerpsWithdrawalRateLimitTest {
    @Test
    fun aNonRetryableWithdrawalLimitIsRefusedRegardlessOfItsHttpStatus() {
        for (status in listOf(400, 409, 429, 503)) {
            for (body in listOf(
                """{"code":"withdrawal_rate_limited","retryable":false}""",
                """{"code":"validation_error","retryable":false,"details":{"reason":"withdrawal_rate_limited"}}""",
            )) {
                val failure = refusal(status, body).toPerpsSendError() as PerpsSendError.Refused
                assertEquals(PerpsSendReason.WITHDRAWAL_RATE_LIMITED, failure.reason)
            }
        }
    }

    @Test
    fun tooManyRequestsIsRefusedWhateverItsBody() {
        for (body in listOf(
            """{"code":"rate_limited"}""",
            """{"code":"rate_limited","retryable":true}""",
            """{"code":"withdrawal_rate_limited","retryable":true}""",
            """{"code":"upstream_unavailable","retryable":false}""",
            "not json",
            null,
        )) {
            val failure = refusal(429, body).toPerpsSendError() as PerpsSendError.Refused
            assertEquals(PerpsSendReason.RATE_LIMITED, failure.reason)
        }
    }

    @Test
    fun tooManyRequestsKeepsTheCodeTheServerNamed() {
        val failure = refusal(429, """{"code":"rate_limited","retryable":false}""").toPerpsSendError()

        assertEquals(PerpsSendError.Refused(PerpsSendReason.RATE_LIMITED, "rate_limited"), failure)
    }

    @Test
    fun anUnavailableServerKeepsAnUnknownSendOutcome() {
        for (body in listOf(
            """{"code":"withdrawal_rate_limited","retryable":true}""",
            """{"code":"upstream_unavailable","retryable":false}""",
            "not json",
            null,
        )) {
            assertSame(PerpsSendError.Unknown, refusal(503, body).toPerpsSendError())
        }
    }

    private fun refusal(status: Int, body: String?): Throwable = if (status >= 500) {
        ServerException(statusCode = status, response = ServerError<Any>(body = body, statusCode = status))
    } else {
        ClientException(statusCode = status, response = ClientError<Any>(body = body, statusCode = status))
    }
}
