package com.tonapps.swap.screens.swap

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.test.screen.ScreenSnapshot
import org.junit.Assert.assertTrue

/**
 * The value form of [SwapState]: every node the screen reads, sampled at one instant.
 *
 * The nodes stay separate flows in the feature — this is a test-side projection of them, so an
 * assertion can talk about a rendered screen instead of about ten flows one at a time.
 */
data class SwapStateSnapshot(
    val initState: SwapInitState,
    val sellAsset: AssetEntity?,
    val buyAsset: AssetEntity?,
    val sellAccount: AccountWithDetails?,
    val buyAccount: AccountWithDetails?,
    val amountInput: AmountInput,
    // The validated amounts as plain integers: BaseUnit has no value equality, and two snapshots
    // have to be comparable.
    val sellAmount: BigInteger?,
    val buyAmount: BigInteger?,
    val insufficientFunds: Boolean,
    val selectedSlippageBps: Int?,
    val quote: DisplayQuote?,
    val quoteErrorMessageRes: Int?,
    val quoteTimer: Float?,
    val isLoading: Boolean,
    val continueEnabled: Boolean,
    val continueLoading: Boolean,
) : ScreenSnapshot {

    /** A request in flight or a confirm being prepared: the screen has not finished moving. */
    override val isBusy: Boolean get() = isLoading || continueLoading

    /**
     * A screen that failed to open has no pair, so its account nodes sit in their initial Loading
     * for as long as it is up — [isBusy] there is not a stage that ends.
     */
    override val isStuck: Boolean get() = initState is SwapInitState.Error

    // The timer ticks four times a second for as long as a quote is live, so two frames are never
    // equal while it is in them. It is the one node that says nothing about what the screen agrees
    // on.
    override val stable: Any get() = copy(quoteTimer = null)

    val hasQuote: Boolean get() = quote != null

    val quoteUnavailable: Boolean get() = quoteErrorMessageRes != null

    val hasAccounts: Boolean get() = sellAccount != null && buyAccount != null

    /** The amount the user fixed, as a number — null when the field holds "0", "." or nothing. */
    val enteredAmount: BigInteger?
        get() = when (amountInput.side) {
            AmountInputSide.Sell -> sellAmount
            AmountInputSide.Buy -> buyAmount
        }

    /** A real amount is entered against a resolved pair, so a quote is owed for it. */
    val expectsQuote: Boolean
        get() = initState is SwapInitState.Ready && hasAccounts && enteredAmount != null

    /**
     * What the screen may never render, whatever route it took to get here.
     *
     * Runs on every snapshot the driver's `pass` and `after` hand back, and — where a test asks for
     * it — on every frame in its `rendered`. Frames that exist only inside one cascade are never
     * sampled, so this is a contract about rendered screens, not about every combination the nodes
     * pass through.
     */
    override fun assertCoherent() {
        // The button promises everything behind it: a quote to send, nothing in flight, and a
        // balance that covers it.
        assertTrue("continue enabled without a quote: $this", !continueEnabled || hasQuote)
        assertTrue(
            "continue enabled while loading: $this",
            !continueEnabled || !(isLoading || continueLoading),
        )
        assertTrue("continue enabled without the funds: $this", !continueEnabled || !insufficientFunds)
        assertTrue("a quote and an unavailable quote at the same time: $this", !quoteUnavailable || !hasQuote)
        assertTrue("a quote survived into a loading state: $this", !hasQuote || !isLoading)
        assertTrue(
            "the screen is not ready but the graph produced a quote: $this",
            initState is SwapInitState.Ready || !hasQuote,
        )
        assertTrue(
            "settled with an amount but no quote and no error: $this",
            !expectsQuote || isLoading || hasQuote || quoteUnavailable,
        )
    }

    override fun toString(): String = buildString {
        append("SwapState(")
        append("init=$initState, ")
        append("pair=${sellAsset?.symbol}->${buyAsset?.symbol}, ")
        append("amount='${amountInput.text}'${if (amountInput.inFiat) { "$" } else { "" }}@${amountInput.side}, ")
        append("accounts=$hasAccounts, ")
        append("loading=$isLoading, quote=${quote?.buyAmount}, unavailable=$quoteUnavailable, ")
        append("funds=${!insufficientFunds}, continue=$continueEnabled")
        append(")")
    }
}

/** What the screen would render right now. */
fun SwapFeature.viewState() = SwapStateSnapshot(
    initState = initState.value,
    sellAsset = sellAsset.value,
    buyAsset = buyAsset.value,
    sellAccount = sellAccount.value,
    buyAccount = buyAccount.value,
    amountInput = amountInput.value,
    sellAmount = sellValidatedAmount.value?.value,
    buyAmount = buyValidatedAmount.value?.value,
    insufficientFunds = insufficientFunds.value,
    selectedSlippageBps = selectedSlippageBps.value,
    quote = quote.value,
    quoteErrorMessageRes = quoteErrorMessageRes.value,
    quoteTimer = quoteTimer.value,
    isLoading = isLoading.value,
    continueEnabled = continueEnabled.value,
    continueLoading = continueLoading.value,
)
