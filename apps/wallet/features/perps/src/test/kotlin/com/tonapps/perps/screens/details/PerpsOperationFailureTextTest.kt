package com.tonapps.perps.screens.details

import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsSendReason
import com.tonapps.perps.trade.PerpsTradeFailure
import com.tonapps.perps.trade.isRateLimited
import com.tonapps.perps.trade.tag
import com.tonapps.wallet.data.multichain.perps.PerpsOperationKind
import com.tonapps.wallet.data.multichain.perps.PerpsOperationRecord
import com.tonapps.wallet.data.multichain.perps.PerpsOperationStatus
import com.tonapps.wallet.localization.Localization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerpsOperationFailureTextTest {
    @Test
    fun tooManyRequestsIsShownForEveryTradingOperation() {
        for (kind in PerpsOperationKind.entries) {
            val text = failed(kind, PerpsTradeFailure.SendRefused(PerpsSendReason.RATE_LIMITED, "rate_limited")).failureText()

            assertEquals(kind.name, Localization.perps_too_many_requests, text)
        }
    }

    @Test
    fun tooManyRequestsWithoutACodeIsShownToo() {
        val text = failed(PerpsOperationKind.OPEN, PerpsTradeFailure.SendRefused(PerpsSendReason.RATE_LIMITED, "")).failureText()

        assertEquals(Localization.perps_too_many_requests, text)
    }

    @Test
    fun anotherRefusalKeepsTheOperationsOwnText() {
        val text = failed(PerpsOperationKind.CLOSE, PerpsTradeFailure.SendRefused(PerpsSendReason.OTHER, "code")).failureText()

        assertEquals(Localization.perps_operation_close_failed_toast, text)
    }

    @Test
    fun onlyARateLimitedRefusalCountsAsRateLimited() {
        assertTrue(PerpsTradeFailure.SendRefused(PerpsSendReason.RATE_LIMITED, "").isRateLimited)
        assertFalse(PerpsTradeFailure.SendRefused(PerpsSendReason.WITHDRAWAL_RATE_LIMITED, "withdrawal_rate_limited").isRateLimited)
        assertFalse(PerpsTradeFailure.SendRefused(PerpsSendReason.LIGHTER_REJECTED, "code").isRateLimited)
        assertFalse(PerpsTradeFailure.SendFailed(PerpsError.ServiceUnavailable).isRateLimited)
        assertFalse(PerpsTradeFailure.OperationInProgress.isRateLimited)
    }

    private fun failed(kind: PerpsOperationKind, failure: PerpsTradeFailure) = PerpsOperationRecord(
        operationId = "operation",
        walletId = "wallet",
        marketIndex = 1,
        kind = kind,
        side = "LONG",
        marginQuote = "2",
        leverage = 20,
        reviewJson = null,
        planDigest = null,
        expectedBaseSize = null,
        baseBefore = null,
        marginBefore = null,
        status = PerpsOperationStatus.FAILED,
        error = failure.tag(),
        createdAt = 0L,
        updatedAt = 0L,
    )
}
