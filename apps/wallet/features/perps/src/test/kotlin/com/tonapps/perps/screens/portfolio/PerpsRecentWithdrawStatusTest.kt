package com.tonapps.perps.screens.portfolio

import com.tonapps.wallet.data.multichain.perps.PerpsDepositRoute
import com.tonapps.wallet.data.multichain.perps.PerpsDepositStage
import com.tonapps.wallet.data.multichain.perps.PerpsFundingKind
import com.tonapps.wallet.data.multichain.perps.PerpsFundingRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PerpsRecentWithdrawStatusTest {

    @Test
    fun anAcceptedWithdrawalIsSentUntilItIsPaidOut() {
        val accepted = recentWithdrawStatus(listOf(withdrawal(PerpsDepositStage.ACCEPTED, updatedAt = NOW - 1_000)), NOW)
        val awaitingL1 = recentWithdrawStatus(listOf(withdrawal(PerpsDepositStage.AWAITING_L1, updatedAt = NOW - 1_000)), NOW)

        assertEquals(PerpsFundingStatus.WITHDRAW_SENT, accepted?.status)
        assertEquals(PerpsFundingStatus.WITHDRAW_SENT, awaitingL1?.status)
        assertEquals(NOW + 5_000, accepted?.shownUntil)
    }

    @Test
    fun aPaidOutWithdrawalIsConfirmed() {
        val settled = recentWithdrawStatus(listOf(withdrawal(PerpsDepositStage.SETTLED, updatedAt = NOW - 1_000)), NOW)

        assertEquals(PerpsFundingStatus.CONFIRMED, settled?.status)
    }

    @Test
    fun theLatestWithdrawalDecidesThePill() {
        val records = listOf(
            withdrawal(PerpsDepositStage.SETTLED, updatedAt = NOW - 3_000, operationId = "paid"),
            withdrawal(PerpsDepositStage.ACCEPTED, updatedAt = NOW - 1_000, operationId = "sent"),
        )

        assertEquals(PerpsFundingStatus.WITHDRAW_SENT, recentWithdrawStatus(records, NOW)?.status)
    }

    @Test
    fun anOldOrUnfinishedWithdrawalShowsNoPill() {
        assertNull(recentWithdrawStatus(listOf(withdrawal(PerpsDepositStage.SETTLED, updatedAt = NOW - 6_000)), NOW))
        assertNull(recentWithdrawStatus(listOf(withdrawal(PerpsDepositStage.SOURCE_SENDING, updatedAt = NOW)), NOW))
        assertNull(recentWithdrawStatus(listOf(withdrawal(PerpsDepositStage.FAILED, updatedAt = NOW)), NOW))
        assertNull(recentWithdrawStatus(emptyList(), NOW))
    }

    private fun withdrawal(
        stage: PerpsDepositStage,
        updatedAt: Long,
        operationId: String = "withdrawal",
    ) = PerpsFundingRecord(
        operationId = operationId,
        walletId = "wallet",
        kind = PerpsFundingKind.WITHDRAW,
        stage = stage,
        route = PerpsDepositRoute.DIRECT_GATEWAY,
        beneficiary = "0xbeneficiary",
        sourceAssetId = "lighter/mainnet/3",
        sourceSymbol = "USDC",
        sourceDecimals = 6,
        sourceAmount = "1000000",
        targetAmount = "1000000",
        quoteId = null,
        executionId = null,
        sourceTxHash = null,
        l1TxHash = null,
        steps = emptyList(),
        linkSignature = null,
        linkDeadline = null,
        registration = null,
        depositId = null,
        creditedAmount = null,
        accountIndex = null,
        failureReason = null,
        revision = 0,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    private companion object {
        const val NOW = 1_000_000L
    }
}
