package com.tonapps.wallet.data.ledger.connection

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerAppSetting
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerPhase
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.controller.LedgerOperationState
import com.tonapps.ledgerkit.controller.LedgerPurpose
import com.tonapps.ledgerkit.controller.LedgerRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerActivityLogTest {

    private var now = 1_000L
    private val log = LedgerActivityLog(clock = { now })
    private val sign = LedgerRequest("AA:BB", "Stax", LedgerPurpose.SignTransaction)
    private val verify = LedgerRequest("AA:BB", "Stax", LedgerPurpose.VerifyAddress)

    private fun outcomes() = log.entries.value.map { it.purpose to it.outcome }

    @Test
    fun `one operation is one entry that follows its progress to the end`() {
        log.record(LedgerOperationState.Active(sign, LedgerProgress.Connecting))
        log.record(LedgerOperationState.Active(sign, LedgerProgress.OpeningApp(LedgerAppId.Ton)))
        assertEquals(listOf(LedgerPurpose.SignTransaction to LedgerActivityOutcome.InProgress(LedgerProgress.OpeningApp(LedgerAppId.Ton))), outcomes())
        log.record(LedgerOperationState.Done(sign))
        log.record(LedgerOperationState.Idle)
        assertEquals(listOf(LedgerPurpose.SignTransaction to LedgerActivityOutcome.Done), outcomes())
        assertEquals(1_000L, log.entries.value.single().startedAt)
    }

    @Test
    fun `same request again after it finished is a new entry, newest first`() {
        log.record(LedgerOperationState.Active(sign, LedgerProgress.Connecting))
        log.record(LedgerOperationState.Done(sign))
        now = 2_000L
        log.record(LedgerOperationState.Active(sign, LedgerProgress.Connecting))
        assertEquals(2, log.entries.value.size)
        assertEquals(2_000L, log.entries.value.first().startedAt)
    }

    @Test
    fun `cancelled operation is interrupted`() {
        log.record(LedgerOperationState.Active(verify, LedgerProgress.Connecting))
        log.record(LedgerOperationState.Idle)
        assertEquals(listOf(LedgerPurpose.VerifyAddress to LedgerActivityOutcome.Interrupted), outcomes())
    }

    @Test
    fun `failure keeps its reason when dismissed and a retry continues the same entry`() {
        log.record(LedgerOperationState.Active(sign, LedgerProgress.Connecting))
        log.record(LedgerOperationState.Failed(sign, LedgerFailure.DeviceLocked, canRetry = true))
        assertEquals(listOf(LedgerPurpose.SignTransaction to LedgerActivityOutcome.Failed(LedgerFailure.DeviceLocked)), outcomes())
        log.record(LedgerOperationState.Active(sign, LedgerProgress.Connecting))
        log.record(LedgerOperationState.Failed(sign, LedgerFailure.BluetoothOff, canRetry = true))
        log.record(LedgerOperationState.Idle)
        assertEquals(listOf(LedgerPurpose.SignTransaction to LedgerActivityOutcome.Failed(LedgerFailure.BluetoothOff)), outcomes())
    }

    @Test
    fun `a different request closes the running one`() {
        log.record(LedgerOperationState.Active(sign, LedgerProgress.Connecting))
        log.record(LedgerOperationState.Active(verify, LedgerProgress.Connecting))
        assertEquals(
            listOf(
                LedgerPurpose.VerifyAddress to LedgerActivityOutcome.InProgress(LedgerProgress.Connecting),
                LedgerPurpose.SignTransaction to LedgerActivityOutcome.Interrupted,
            ),
            outcomes(),
        )
    }

    @Test
    fun `only the last twenty operations are kept`() {
        repeat(25) { index ->
            now = index.toLong()
            log.record(LedgerOperationState.Active(sign, LedgerProgress.Connecting))
            log.record(LedgerOperationState.Done(sign))
        }
        assertEquals(20, log.entries.value.size)
        assertEquals(24L, log.entries.value.first().startedAt)
    }

    @Test
    fun `entries survive a restart, an operation cut short reads as interrupted and ids keep growing`() {
        val store = MemoryStore()
        val first = LedgerActivityLog(clock = { now }, store = store)
        first.record(LedgerOperationState.Active(sign, LedgerProgress.Connecting))
        first.record(LedgerOperationState.Failed(sign, LedgerFailure.SettingRequired(LedgerAppId.Ethereum, LedgerAppSetting.RawMessages), canRetry = true))
        first.record(LedgerOperationState.Idle)
        first.record(LedgerOperationState.Active(verify, LedgerProgress.OpeningApp(LedgerAppId.Ton)))
        val restored = LedgerActivityLog(clock = { now }, store = store).apply { restore() }
        assertEquals(
            listOf(
                LedgerPurpose.VerifyAddress to LedgerActivityOutcome.Interrupted,
                LedgerPurpose.SignTransaction to LedgerActivityOutcome.Failed(LedgerFailure.SettingRequired(LedgerAppId.Ethereum, LedgerAppSetting.RawMessages)),
            ),
            restored.entries.value.map { it.purpose to it.outcome },
        )
        restored.record(LedgerOperationState.Active(sign, LedgerProgress.Connecting))
        assertEquals(2L, restored.entries.value.first().id)
    }

    @Test
    fun `stored entries keep every failure kind`() {
        val failures = listOf(
            LedgerFailure.DeviceLocked,
            LedgerFailure.Timeout(LedgerPhase.Apdu),
            LedgerFailure.WrongAppOpen("Bitcoin", LedgerAppId.Solana),
            LedgerFailure.AppUpdateRequired(LedgerAppId.Ton, "2.1.0", "2.7.0"),
            LedgerFailure.InvalidPayload(0x6a80, "Invalid data"),
        )
        val entries = failures.mapIndexed { index, failure ->
            LedgerActivity(index.toLong(), "AA:BB", LedgerPurpose.SignMessage, 5L, LedgerActivityOutcome.Failed(failure))
        } + LedgerActivity(9L, "AA:BB", LedgerPurpose.Connect, 6L, LedgerActivityOutcome.Done)
        assertEquals(entries, LedgerActivityCodec.decode(LedgerActivityCodec.encode(entries)))
    }

    private class MemoryStore : LedgerActivityStore {

        private var raw: String? = null

        override fun load(): List<LedgerActivity> = raw?.let(LedgerActivityCodec::decode).orEmpty()

        override fun save(entries: List<LedgerActivity>) {
            raw = LedgerActivityCodec.encode(entries)
        }
    }
}
