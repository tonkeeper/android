package com.tonapps.perps.funding

import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.perps.data.toWireDecimalOrNull
import com.tonapps.wallet.data.multichain.perps.PerpsDepositPendingStep
import com.tonapps.wallet.data.multichain.perps.PerpsDepositRoute
import com.tonapps.wallet.data.multichain.perps.PerpsDepositStage
import com.tonapps.wallet.data.multichain.perps.PerpsFundingKind
import com.tonapps.wallet.data.multichain.perps.PerpsFundingRecord
import java.math.BigDecimal

data class PerpsDepositStatus(
    val id: String,
    val createdAt: Long,
    val sourceAmount: BigDecimal,
    val sourceSymbol: String,
    val sourceNetwork: String,
    val expectedAmount: BigDecimal?,
    val creditedAmount: BigDecimal?,
    val estimatedDurationSeconds: Long?,
    val state: State,
    val step: PerpsDepositPendingStep?,
    val skipsConversion: Boolean,
    val transactions: Set<String>,
    val acknowledged: Boolean = false,
) {

    enum class State {
        PENDING,
        COMPLETED,
        FAILED,
        REFUNDED,
        UNCONFIRMED,
    }

    val isProblem: Boolean
        get() = state == State.FAILED || state == State.REFUNDED || state == State.UNCONFIRMED

    val sourceIsUsdc: Boolean
        get() = sourceSymbol.equals(USDC, ignoreCase = true)

    fun isDelayed(now: Long): Boolean {
        val estimate = estimatedDurationSeconds ?: return false
        return state == State.PENDING && now - createdAt > estimate * MILLIS_IN_SECOND
    }

    private companion object {
        const val MILLIS_IN_SECOND = 1_000L
    }
}

fun PerpsFundingRecord.toDepositStatus(): PerpsDepositStatus? {
    if (kind != PerpsFundingKind.DEPOSIT) {
        return null
    }
    val state = depositState() ?: return null
    val sourceUnits = sourceAmount.toUnitsOrNull() ?: return null
    val skipsConversion = skipsConversion()
    return PerpsDepositStatus(
        id = operationId,
        createdAt = createdAt,
        sourceAmount = PerpsFundingAmount.toDecimal(sourceUnits, sourceDecimals),
        sourceSymbol = sourceSymbol,
        sourceNetwork = networkName(sourceAssetId),
        expectedAmount = targetAmount.toUnitsOrNull()?.let {
            PerpsFundingAmount.toDecimal(it, PerpsFundingAmount.USDC_DECIMALS)
        },
        creditedAmount = creditedAmount.toWireDecimalOrNull(),
        estimatedDurationSeconds = estimatedDurationSeconds?.takeIf { it > 0 },
        state = state,
        step = if (skipsConversion && pendingStep == PerpsDepositPendingStep.CONVERTING) {
            PerpsDepositPendingStep.ADDING
        } else {
            pendingStep
        },
        skipsConversion = skipsConversion,
        transactions = (listOfNotNull(sourceTxHash, l1TxHash) + steps.mapNotNull { it.txHash })
            .mapNotNullTo(HashSet()) { perpsTransactionHash(it) },
        acknowledged = statusAcknowledged,
    )
}

fun perpsTransactionHash(txId: String): String? {
    val hash = txId.split(':').let { parts -> parts.getOrElse(1) { parts[0] } }
        .trim()
        .lowercase()
        .removePrefix(HEX_PREFIX)
    return hash.ifEmpty { null }
}

// Mirrors how tk-perps routes a deposit. It reports the converting step only for a deposit tracked
// through the aggregator (Relay, a swaps-backend execution id), whatever the asset, USDC included.
// A direct deposit (no execution id) and one through Lighter's fun.xyz bridge (execution id
// "funxyz_…") go from sending straight to adding, so their status has two steps, not three; a
// converting step reported for them anyway is shown as adding, so the status never stalls on a hidden step.
private fun PerpsFundingRecord.skipsConversion(): Boolean {
    val execution = executionId?.trim().orEmpty()
    return route == PerpsDepositRoute.DIRECT_GATEWAY || execution.isEmpty() || execution.startsWith(FUNXYZ_EXECUTION_PREFIX)
}

private fun PerpsFundingRecord.depositState(): PerpsDepositStatus.State? = when (stage) {
    PerpsDepositStage.CREDITED -> PerpsDepositStatus.State.COMPLETED
    PerpsDepositStage.FAILED -> PerpsDepositStatus.State.FAILED
    PerpsDepositStage.REFUNDED -> PerpsDepositStatus.State.REFUNDED
    PerpsDepositStage.UNCONFIRMED -> PerpsDepositStatus.State.UNCONFIRMED
    PerpsDepositStage.SOURCE_SIGNED,
    PerpsDepositStage.SOURCE_SENDING,
    PerpsDepositStage.SOURCE_UNKNOWN,
    PerpsDepositStage.AWAITING_DELIVERY,
    PerpsDepositStage.REGISTRATION_PENDING,
    PerpsDepositStage.REGISTERED_PENDING,
    PerpsDepositStage.NEEDS_AUTHORIZATION -> PerpsDepositStatus.State.PENDING

    PerpsDepositStage.PREPARING,
    PerpsDepositStage.ACCEPTED,
    PerpsDepositStage.AWAITING_L1,
    PerpsDepositStage.SETTLED -> null
}

private fun networkName(assetId: String): String {
    val chainId = PerpsFundingAssetId.parse(assetId)?.chainId
    val chain = chainId?.let { Chain.find(it.substringBefore('/'), it.substringAfter('/')) }
    return chain?.name ?: assetId.substringBefore('/').uppercase()
}

private fun String.toUnitsOrNull() = try {
    PerpsFundingAmount.integerUnits(this)
} catch (_: PerpsFundingAmountException) {
    null
}

private const val USDC = "USDC"
private const val FUNXYZ_EXECUTION_PREFIX = "funxyz_"
private const val HEX_PREFIX = "0x"
