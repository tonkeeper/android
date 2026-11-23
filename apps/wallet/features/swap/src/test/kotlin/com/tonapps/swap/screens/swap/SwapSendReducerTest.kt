package com.tonapps.swap.screens.swap

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.core.components.AmountToggle
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwapSendReducerTest {

    private val request = mockk<ConfirmRequest>()

    private fun inputs(
        text: String = "1",
        inFiat: Boolean = false,
        isMax: Boolean = false,
        maxUnits: BigInteger? = null,
        slippageBps: Int? = 100,
        toggle: AmountToggle? = null,
        sellId: String = "ton",
    ) = SwapInputs(
        sellId = sellId,
        buyId = "usdt",
        input = AmountInput(text = text, inFiat = inFiat, isMax = isMax, maxUnits = maxUnits, toggle = toggle),
        slippageBps = slippageBps,
    )

    private fun maxInputs(units: Long, inFiat: Boolean = false) =
        inputs(text = "5", inFiat = inFiat, isMax = true, maxUnits = BigInteger.fromLong(units))

    private fun toggled(from: String, to: String, toFiat: Boolean) =
        AmountToggle(assetId = "ton", sourceText = from, toggledText = to, toFiat = toFiat)

    private fun sent(inputs: SwapInputs, phase: SwapSendPhase): SendNode {
        val started = reduceSend(SendNode(), SendCmd.Quoted(request, inputs))
        val id = started.attempt!!.id
        return reduceSend(started, SendCmd.Progress(SwapAction.SendProgress(id, phase, null), quoteReceivedAt = null))
    }

    private fun SendNode.changed(vararg inputs: SwapInputs): SendNode {
        return inputs.fold(this) { node, next -> reduceSend(node, SendCmd.InputsChanged(next)) }
    }

    @Test
    fun blockedKeepsOnMaxWithTheSameUnits() {
        val node = sent(maxInputs(5_000), SwapSendPhase.Blocked).changed(maxInputs(5_000))

        assertEquals(SwapSendPhase.Blocked, node.attempt?.phase)
    }

    @Test
    fun blockedLiftsOnMaxWithOtherUnits() {
        val node = sent(maxInputs(5_000), SwapSendPhase.Blocked).changed(maxInputs(4_000))

        assertNull(node.attempt)
    }

    @Test
    fun blockedKeepsAcrossAFiatSwitchAfterMax() {
        val node = sent(maxInputs(5_000), SwapSendPhase.Blocked).changed(
            inputs(text = "12.5", inFiat = true),
            maxInputs(5_000, inFiat = true),
            inputs(text = "5"),
        )

        assertEquals(SwapSendPhase.Blocked, node.attempt?.phase)
    }

    @Test
    fun blockedLiftsOnMaxWithOtherUnitsAfterAFiatSwitch() {
        val node = sent(maxInputs(5_000), SwapSendPhase.Blocked).changed(
            inputs(text = "12.5", inFiat = true),
            maxInputs(4_000, inFiat = true),
        )

        assertNull(node.attempt)
    }

    @Test
    fun blockedKeepsOnTheSameAmountWrittenDifferently() {
        val node = sent(inputs(text = "0.5"), SwapSendPhase.Blocked).changed(inputs(text = "0.50"), inputs(text = "0,5"))

        assertEquals(SwapSendPhase.Blocked, node.attempt?.phase)
    }

    @Test
    fun blockedKeepsOnAClearedField() {
        val node = sent(inputs(text = "0.5"), SwapSendPhase.Blocked).changed(inputs(text = ""))

        assertEquals(SwapSendPhase.Blocked, node.attempt?.phase)
    }

    @Test
    fun blockedKeepsOnASlippageChange() {
        val node = sent(inputs(), SwapSendPhase.Blocked).changed(inputs(slippageBps = 300))

        assertEquals(SwapSendPhase.Blocked, node.attempt?.phase)
    }

    @Test
    fun blockedLiftsOnAnotherAmountOrPair() {
        val blocked = sent(inputs(text = "0.5"), SwapSendPhase.Blocked)

        assertNull(blocked.changed(inputs(text = "0.6")).attempt)
        assertNull(blocked.changed(inputs(text = "0.5").copy(buyId = "eth")).attempt)
    }

    @Test
    fun retryableSurvivesTheSameAmountWrittenDifferently() {
        val node = sent(inputs(text = "1"), SwapSendPhase.Retryable).changed(inputs(text = "1.0"))

        assertEquals(SwapSendPhase.Retryable, node.attempt?.phase)
    }

    @Test
    fun retryableEndsOnAnotherAmountOrSlippage() {
        val retryable = sent(inputs(text = "1"), SwapSendPhase.Retryable)

        assertNull(retryable.changed(inputs(text = "1.5")).attempt)
        assertNull(retryable.changed(inputs(text = "1", slippageBps = 300)).attempt)
    }

    @Test
    fun retryableSurvivesASlippageChangedDuringReview() {
        val review = reduceSend(SendNode(), SendCmd.Quoted(request, inputs()))
            .changed(inputs(slippageBps = 300))
        val requoted = reduceSend(review, SendCmd.Quoted(request, inputs = null))
        val id = requoted.attempt!!.id
        val failed = reduceSend(
            requoted,
            SendCmd.Progress(SwapAction.SendProgress(id, SwapSendPhase.Retryable, null), quoteReceivedAt = null),
        )

        assertEquals(SwapSendPhase.Retryable, failed.changed(inputs(text = "1.0", slippageBps = 300)).attempt?.phase)
    }

    @Test
    fun blockedKeepsAcrossAMaxToggleThereAndBack() {
        val units = BigInteger.fromLong(5_000)
        val node = sent(maxInputs(5_000), SwapSendPhase.Blocked).changed(
            inputs(text = "12.5", inFiat = true, maxUnits = units),
            maxInputs(5_000),
        )

        assertEquals(SwapSendPhase.Blocked, node.attempt?.phase)
    }

    @Test
    fun retryableSurvivesAUnitToggleThereAndBack() {
        val node = sent(inputs(text = "1"), SwapSendPhase.Retryable).changed(
            inputs(text = "1.43", inFiat = true, toggle = toggled(from = "1", to = "1.43", toFiat = true)),
            inputs(text = "1", toggle = toggled(from = "1.43", to = "1", toFiat = false)),
        )

        assertEquals(SwapSendPhase.Retryable, node.attempt?.phase)
    }

    @Test
    fun retryableEndsOnAFiatAmountEditedAfterTheToggle() {
        val node = sent(inputs(text = "1"), SwapSendPhase.Retryable).changed(
            inputs(text = "2", inFiat = true, toggle = toggled(from = "1", to = "1.43", toFiat = true)),
        )

        assertNull(node.attempt)
    }

    @Test
    fun retryableEndsWhenALostRateTurnsFiatTextIntoTokens() {
        val fiat = inputs(text = "1.43", inFiat = true, toggle = toggled(from = "1", to = "1.43", toFiat = true))
        val node = sent(fiat, SwapSendPhase.Retryable).changed(fiat.copy(input = fiat.input.copy(inFiat = false)))

        assertNull(node.attempt)
    }

    @Test
    fun retryableContinueCountsARetryInsteadOfANewAttempt() {
        val retryable = sent(inputs(), SwapSendPhase.Retryable)

        val retried = reduceSend(retryable, SendCmd.Continue(blocked = false, quoteReceivedAt = null))

        assertEquals(retryable.attempt?.id, retried.attempt?.id)
        assertEquals(1, retried.attempt?.retries)
    }

    @Test
    fun aPickedFeeHoldsAcrossAnotherAmount() {
        val node = picked(inputs(text = "1"), USDT_FEE)
            .requotedAt(inputs(text = "0.5"))

        assertEquals(USDT_FEE, node.attempt?.feeId)
    }

    @Test
    fun aNewAttemptReportingNoPickKeepsThePickedFee() {
        val requoted = picked(inputs(text = "1"), USDT_FEE)
            .requotedAt(inputs(text = "0.5"))
        val reported = reduceSend(requoted, SendCmd.FeePicked(SwapAction.FeePicked(requoted.attempt!!.id, null)))

        val node = reported.requotedAt(inputs(text = "0.25"))

        assertEquals(USDT_FEE, node.attempt?.feeId)
    }

    @Test
    fun aPickedFeeDropsWithAnotherAssetSold() {
        val node = picked(inputs(text = "1"), USDT_FEE)
            .requotedAt(inputs(text = "1", sellId = "eth"))

        assertNull(node.attempt?.feeId)
    }

    @Test
    fun aPickedFeeStaysDroppedOnTheFirstAssetAgain() {
        val node = picked(inputs(text = "1"), USDT_FEE)
            .requotedAt(inputs(text = "1", sellId = "eth"))
            .requotedAt(inputs(text = "1"))

        assertNull(node.attempt?.feeId)
    }

    @Test
    fun anotherAssetSoldWhileTheSheetOpensCarriesNoPickedFee() {
        val inSheet = reduceSend(picked(inputs(text = "1"), USDT_FEE), SendCmd.FeeRowTapped)
        val flipped = reduceSend(reduceSend(inSheet, SendCmd.InputKeyChanged), SendCmd.Quoted(request, inputs(text = "1", sellId = "eth")))
        val reported = reduceSend(flipped, SendCmd.FeePicked(SwapAction.FeePicked(flipped.attempt!!.id, flipped.attempt.feeId)))

        val node = reported.requotedAt(inputs(text = "2", sellId = "eth"))

        assertNull(flipped.attempt.feeId)
        assertNull(node.attempt?.feeId)
    }

    @Test
    fun anotherAssetSoldAfterTheHeldInputsWereRebasedCarriesNoPickedFee() {
        val inSheet = reduceSend(picked(inputs(text = "1"), USDT_FEE), SendCmd.FeeRowTapped)
        // A flip to an asset with no rate drops the fiat input: the held inputs become the new ones.
        val rebased = reduceSend(
            reduceSend(inSheet, SendCmd.InputKeyChanged),
            SendCmd.InputsChanged(inputs(text = "1", inFiat = true, sellId = "eth")),
        )

        val node = reduceSend(rebased, SendCmd.Quoted(request, inputs(text = "1", inFiat = true, sellId = "eth")))

        assertEquals("eth", rebased.held?.sellId)
        assertNull(node.attempt?.feeId)
        assertNull(node.pickedFee)
    }

    @Test
    fun aPickMadeAfterTheHeldInputsWereRebasedIsTheStartedAssets() {
        val started = reduceSend(SendNode(), SendCmd.Quoted(request, inputs(text = "1")))
        val inSheet = reduceSend(started, SendCmd.FeeRowTapped)
        val rebased = reduceSend(
            reduceSend(inSheet, SendCmd.InputKeyChanged),
            SendCmd.InputsChanged(inputs(text = "1", inFiat = true, sellId = "eth")),
        )

        // Picked in the sheet still open on the attempt for the first asset.
        val picked = reduceSend(rebased, SendCmd.FeePicked(SwapAction.FeePicked(rebased.attempt!!.id, USDT_FEE)))
        val node = reduceSend(picked, SendCmd.Quoted(request, inputs(text = "1", inFiat = true, sellId = "eth")))

        assertEquals(PickedFee("ton", USDT_FEE), picked.pickedFee)
        assertNull(node.attempt?.feeId)
    }

    @Test
    fun aSendRestartedOnAnotherAssetsQuoteCarriesNoPickedFee() {
        val node = picked(inputs(text = "1"), USDT_FEE)
        val id = node.attempt!!.id
        val working = reduceSend(node, SendCmd.Progress(SwapAction.SendProgress(id, SwapSendPhase.Working, null), quoteReceivedAt = null))
        // A flip in the frame before the screen freezes: the held inputs keep the first asset, the quote is the new pair's.
        val flipped = reduceSend(working, SendCmd.InputsChanged(inputs(text = "1", sellId = "eth")))
        val quoted = reduceSend(flipped, SendCmd.Quoted(quotedAt(42), inputs(text = "1", sellId = "eth")))

        val restarted = reduceSend(quoted, SendCmd.Progress(SwapAction.SendProgress(id, SwapSendPhase.Ended, null), quoteReceivedAt = 42))

        // The restart is the quoted pair's: the held inputs follow it, so a later retry compares with that pair.
        assertEquals("eth", restarted.held?.sellId)
        assertEquals("eth", restarted.attempt?.sellId)
        assertNull(restarted.attempt?.feeId)
        assertNull(restarted.pickedFee)
    }

    @Test
    fun aSendRestartedOnAQuoteForAnotherAmountHoldsTheQuotedInputs() {
        val restarted = restartedOn(held = inputs(text = "1"), quoted = inputs(text = "2"))

        assertEquals("2", restarted.held?.input?.text)
    }

    @Test
    fun aSendRestartedOnTheSameMaxKeepsTheHeldInputs() {
        val restarted = restartedOn(held = maxInputs(5_000), quoted = inputs(text = "4.99", isMax = true, maxUnits = BigInteger.fromLong(5_000)))

        // The same swap: the held inputs, as Max wrote them, stay.
        assertEquals("5", restarted.held?.input?.text)
        assertEquals(true, restarted.held?.input?.isMax)
    }

    @Test
    fun aPickHoldsWhenTheSheetsAssetComesBackBeforeAnotherIsQuoted() {
        val inSheet = reduceSend(picked(inputs(text = "1"), USDT_FEE), SendCmd.FeeRowTapped)
        val rebased = reduceSend(
            reduceSend(inSheet, SendCmd.InputKeyChanged),
            SendCmd.InputsChanged(inputs(text = "1", inFiat = true, sellId = "eth")),
        )

        val back = reduceSend(reduceSend(rebased, SendCmd.InputKeyChanged), SendCmd.Quoted(request, inputs(text = "1")))

        assertEquals(USDT_FEE, back.attempt?.feeId)
        assertEquals(PickedFee("ton", USDT_FEE), back.pickedFee)
    }

    // A send started on [held] goes Working, the screen moves to [quoted] in the frame before it froze, that quote
    // arrives, and the send ends on it: the attempt restarts on the last quote.
    private fun restartedOn(held: SwapInputs, quoted: SwapInputs): SendNode {
        val started = reduceSend(SendNode(), SendCmd.Quoted(request, held))
        val id = started.attempt!!.id
        val working = reduceSend(started, SendCmd.Progress(SwapAction.SendProgress(id, SwapSendPhase.Working, null), quoteReceivedAt = null))
        val moved = reduceSend(working, SendCmd.InputsChanged(quoted))
        val requoted = reduceSend(moved, SendCmd.Quoted(quotedAt(42), quoted))
        return reduceSend(requoted, SendCmd.Progress(SwapAction.SendProgress(id, SwapSendPhase.Ended, null), quoteReceivedAt = 42))
    }

    private fun quotedAt(receivedAt: Long): ConfirmRequest {
        val quote = mockk<ConfirmType.Swap.Quote> { every { this@mockk.receivedAt } returns receivedAt }
        val type = mockk<ConfirmType.Swap> { every { this@mockk.quote } returns quote }
        return mockk { every { this@mockk.type } returns type }
    }

    private fun picked(inputs: SwapInputs, feeId: String): SendNode {
        val started = reduceSend(SendNode(), SendCmd.Quoted(request, inputs))
        return reduceSend(started, SendCmd.FeePicked(SwapAction.FeePicked(started.attempt!!.id, feeId)))
    }

    // The amount, or the asset sold, changed: the attempt in review goes, and the next quote starts another.
    private fun SendNode.requotedAt(inputs: SwapInputs): SendNode {
        val dropped = reduceSend(this, SendCmd.InputKeyChanged)
        return reduceSend(dropped, SendCmd.Quoted(request, inputs))
    }

    private companion object {
        const val USDT_FEE = "bsc/mainnet/erc20/0x55d398326f99059ff775485246999027b3197955"
    }
}
