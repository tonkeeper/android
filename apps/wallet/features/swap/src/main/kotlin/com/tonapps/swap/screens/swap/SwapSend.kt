package com.tonapps.swap.screens.swap

import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.deposit.multicoin.screens.confirm.SwapFeePlan
import com.tonapps.deposit.multicoin.screens.confirm.SwapFeeSummary

enum class SwapSendPhase { Working, Done, Retryable, Blocked, Ended, Requoted, Review }

data class SwapSend(
    val id: Int,
    val request: ConfirmRequest,
    val phase: SwapSendPhase,
    val retries: Int = 0,
    val inSheet: Boolean = false,
    val feeId: String? = null,
    val clicked: Boolean = false,
    val prepared: Boolean = false,
    val plan: SwapFeePlan = SwapFeePlan.Preparing,
    val shortagePopup: Boolean = false,
    // The asset sold this attempt was started for: a fee picked on it is that asset's.
    val sellId: String? = null,
) {
    fun renewed(id: Int, request: ConfirmRequest): SwapSend {
        return SwapSend(id, request, SwapSendPhase.Review, feeId = feeId, sellId = sellId)
    }
}

internal data class SwapInputs(
    val sellId: String?,
    val buyId: String?,
    val input: AmountInput,
    val slippageBps: Int?,
)

internal sealed interface SendCmd {
    data class Continue(val blocked: Boolean, val quoteReceivedAt: Long?) : SendCmd
    data object FeeRowTapped : SendCmd
    data object CloseDetails : SendCmd
    data class Quoted(val request: ConfirmRequest, val inputs: SwapInputs?) : SendCmd
    data class Progress(val value: SwapAction.SendProgress, val quoteReceivedAt: Long?) : SendCmd
    data object InputKeyChanged : SendCmd
    data class Disposed(val id: Int) : SendCmd
    data class Prepared(val value: SwapAction.Prepared) : SendCmd
    data class FeePlanned(val value: SwapAction.FeePlanned) : SendCmd
    data class FeePicked(val value: SwapAction.FeePicked) : SendCmd
    data object CloseShortage : SendCmd
    data class InputsChanged(val inputs: SwapInputs) : SendCmd
}

internal data class SendNode(
    val lastId: Int = 0,
    val attempt: SwapSend? = null,
    val held: SwapInputs? = null,
    val notice: String? = null,
    val noticeSeq: Int = 0,
    val lastQuoted: ConfirmRequest? = null,
    // The inputs [lastQuoted] was quoted for.
    val lastQuotedInputs: SwapInputs? = null,
    val requoted: SwapSend? = null,
    // The fee the user picked: it outlives the attempt it was picked on, so another amount keeps it.
    val pickedFee: PickedFee? = null,
)

internal data class PickedFee(val sellId: String?, val id: String)

internal fun reduceSend(node: SendNode, cmd: SendCmd): SendNode {
    val attempt = node.attempt
    return when (cmd) {
        is SendCmd.Continue -> when {
            attempt?.phase == SwapSendPhase.Retryable -> node.copy(attempt = attempt.copy(retries = attempt.retries + 1))
            cmd.blocked || attempt?.phase != SwapSendPhase.Review -> node
            else -> node.copy(attempt = continueReview(attempt, cmd.quoteReceivedAt))
        }.copy(notice = null)

        is SendCmd.FeeRowTapped -> when (attempt?.phase) {
            SwapSendPhase.Review, SwapSendPhase.Retryable -> node.copy(attempt = attempt.copy(inSheet = true))
            else -> node
        }

        is SendCmd.CloseDetails -> node.copy(attempt = attempt?.copy(inSheet = false))

        is SendCmd.Quoted -> {
            val quotedFor = cmd.inputs ?: node.lastQuotedInputs ?: node.held
            val sellId = quotedFor?.sellId
            when {
                attempt == null -> node.start(cmd.inputs, sellId) {
                    val started = node.requoted?.renewed(it, cmd.request) ?: SwapSend(it, cmd.request, SwapSendPhase.Review)
                    started.copy(feeId = node.pickedFee?.takeIf { picked -> picked.sellId == sellId }?.id ?: started.feeId)
                }
                attempt.phase != SwapSendPhase.Review -> node
                attempt.inSheet -> node.start(cmd.inputs, sellId) {
                    attempt.copy(
                        id = it,
                        request = cmd.request,
                        prepared = false,
                        clicked = false,
                        plan = SwapFeePlan.Preparing,
                        shortagePopup = false,
                    )
                }
                else -> node.start(cmd.inputs, sellId) { attempt.renewed(it, cmd.request) }
            }.copy(lastQuoted = cmd.request, lastQuotedInputs = quotedFor)
        }

        is SendCmd.Progress -> when {
            attempt == null || attempt.id != cmd.value.id -> node
            cmd.value.phase == SwapSendPhase.Review && attempt.phase == SwapSendPhase.Review -> node
            cmd.value.phase == SwapSendPhase.Ended &&
                attempt.phase != SwapSendPhase.Blocked &&
                attempt.phase != SwapSendPhase.Done &&
                cmd.quoteReceivedAt != null &&
                node.lastQuoted?.quoteReceivedAt == cmd.quoteReceivedAt -> node
                .start(node.restartInputs, node.restartInputs?.sellId) { attempt.renewed(it, checkNotNull(node.lastQuoted)) }
                .copy(notice = cmd.value.message, noticeSeq = node.noticeSeq + 1)
            else -> node.copy(
                requoted = attempt.takeIf { cmd.value.phase == SwapSendPhase.Requoted },
                attempt = when (cmd.value.phase) {
                    SwapSendPhase.Ended, SwapSendPhase.Requoted -> null
                    SwapSendPhase.Review -> attempt.copy(phase = SwapSendPhase.Review, shortagePopup = true)
                    else -> attempt.copy(phase = cmd.value.phase)
                },
                notice = cmd.value.message,
                noticeSeq = node.noticeSeq + 1,
            )
        }

        is SendCmd.Disposed -> when (attempt?.id) {
            cmd.id -> node.copy(attempt = null)
            else -> node
        }

        is SendCmd.Prepared -> when {
            attempt != null && attempt.id == cmd.value.id -> node.copy(attempt = attempt.copy(prepared = cmd.value.ready))
            else -> node
        }

        is SendCmd.FeePlanned -> when {
            attempt != null && attempt.id == cmd.value.id -> node.copy(attempt = attempt.copy(plan = cmd.value.plan))
            else -> node
        }

        is SendCmd.FeePicked -> when {
            // A new attempt reports no pick until one is made: that keeps the one made before.
            attempt != null && attempt.id == cmd.value.id -> node.copy(
                attempt = attempt.copy(feeId = cmd.value.feeId),
                pickedFee = cmd.value.feeId?.let { PickedFee(attempt.sellId, it) } ?: node.pickedFee,
            )
            else -> node
        }

        is SendCmd.CloseShortage -> node.copy(attempt = attempt?.copy(shortagePopup = false))

        is SendCmd.InputKeyChanged -> dropBackground(node).copy(lastQuoted = null, lastQuotedInputs = null, requoted = null)

        is SendCmd.InputsChanged -> when (attempt?.phase) {
            null -> node
            SwapSendPhase.Retryable -> when (node.held?.isSameRetry(cmd.inputs)) {
                true -> node
                else -> node.copy(attempt = null)
            }

            SwapSendPhase.Blocked -> when (node.held?.isSameSwap(cmd.inputs)) {
                false -> node.copy(attempt = null)
                else -> node.rebased(cmd.inputs)
            }

            else -> node.rebased(cmd.inputs)
        }.copy(notice = null)
    }
}

private fun continueReview(attempt: SwapSend, quoteReceivedAt: Long?): SwapSend {
    val plan = attempt.plan
    val current = quoteReceivedAt != null && attempt.quoteReceivedAt == quoteReceivedAt
    return when {
        plan == SwapFeePlan.Failed -> attempt.copy(inSheet = true)
        plan !is SwapFeePlan.Ready -> attempt
        plan.summary.isUnpaid -> attempt.copy(shortagePopup = true)
        plan.summary == SwapFeeSummary.Unknown -> attempt.copy(inSheet = true)
        current && attempt.prepared && plan.quoteReceivedAt == quoteReceivedAt ->
            attempt.copy(phase = SwapSendPhase.Working, clicked = true)
        else -> attempt
    }
}

private fun dropBackground(node: SendNode): SendNode {
    val attempt = node.attempt
    if (attempt?.phase != SwapSendPhase.Review || attempt.inSheet) {
        return node
    }
    return node.copy(attempt = null)
}

/**
 * Starts the attempt [attempt] makes, for the quote of [sellId]. A fee belongs to the asset sold it was
 * picked for: one the attempt carries over from another asset's, and the pick kept for another, go.
 * Neither is told by [held], which inputs edited since the quote may already have rebased.
 */
private fun SendNode.start(inputs: SwapInputs?, sellId: String?, attempt: (id: Int) -> SwapSend): SendNode {
    val id = lastId + 1
    val made = attempt(id)
    val feeId = made.feeId.takeIf { made.sellId == null || made.sellId == sellId }
    val picked = pickedFee?.takeIf { it.sellId == sellId }

    return copy(
        lastId = id,
        attempt = made.copy(sellId = sellId, feeId = feeId),
        held = inputs ?: held,
        requoted = null,
        pickedFee = picked,
    )
}

// A send restarted on the last quote is that quote's swap: the held inputs while they are the same swap (a
// rebased Max or fiat toggle kept), the quote's own otherwise (a flip or an edit in the frame before the send
// froze the screen).
private val SendNode.restartInputs: SwapInputs?
    get() {
        val quoted = lastQuotedInputs ?: return held
        return held?.takeIf { it.isSameSwap(quoted) } ?: quoted
    }

private fun SendNode.rebased(inputs: SwapInputs): SendNode {
    val held = held ?: return copy(held = inputs)
    if (held.input.inFiat == inputs.input.inFiat) {
        return copy(held = held.copy(slippageBps = inputs.slippageBps))
    }
    return copy(held = inputs.copy(input = inputs.input.copy(isMax = held.input.isMax, maxUnits = held.input.maxUnits)))
}

private fun SwapInputs.isSameSwap(other: SwapInputs): Boolean {
    return isSamePairAndSide(other) && when {
        input.isMax && other.input.isMax -> input.maxUnits == other.input.maxUnits
        input.inFiat != other.input.inFiat -> !other.input.isMax
        else -> hasSameAmount(other, whenUnparsable = true)
    }
}

private fun SwapInputs.isSameRetry(other: SwapInputs): Boolean {
    return slippageBps == other.slippageBps && isSamePairAndSide(other) && when {
        input.inFiat == other.input.inFiat -> hasSameAmount(other, whenUnparsable = false)
        else -> other.input.isToggledFrom(input)
    }
}

private fun AmountInput.isToggledFrom(source: AmountInput): Boolean {
    val toggle = toggle ?: return false
    return toggle.toFiat == inFiat && toggle.toggledText == text && toggle.sourceText == source.text
}

private fun SwapInputs.isSamePairAndSide(other: SwapInputs): Boolean {
    return sellId == other.sellId && buyId == other.buyId && input.side == other.input.side
}

private fun SwapInputs.hasSameAmount(other: SwapInputs, whenUnparsable: Boolean): Boolean {
    val value = input.text.amountValue()
    val otherValue = other.input.text.amountValue()
    return if (value != null && otherValue != null) {
        value.compareTo(otherValue) == 0
    } else {
        whenUnparsable
    }
}

private fun String.amountValue() = replace(',', '.').toBigDecimalOrNull()
