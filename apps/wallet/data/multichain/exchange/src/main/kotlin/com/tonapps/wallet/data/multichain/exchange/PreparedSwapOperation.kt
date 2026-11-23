package com.tonapps.wallet.data.multichain.exchange

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.common.getOrElse
import com.tonapps.chainkit.core.op.Operation
import io.exchangeapi.models.CrossSwapPrepareV3
import io.exchangeapi.models.CrossSwapValidationStatus
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * A `/v3/crosschain/routes/{route_id}/prepare` answer: the Generic Payload [operation] the wallet signs
 * as it is, and the swap it makes as the backend sums it up. What the operation spends, and where, is
 * held to the user's intent by chain-kit's validation, not here.
 */
data class PreparedSwapOperation(
    val operation: Operation,
    val providerRouteId: String?,
    val buyBaseAmount: BigInteger?,
    val minimumBuyBaseAmount: BigInteger?,
    /** Where the swap's deposit goes, for a route that pays a deposit address rather than a contract. */
    val depositAddress: String?,
    /** When the route stops being signable, in epoch milliseconds. */
    val expiresAtMillis: Long,
) {

    companion object {

        /**
         * A route still being validated may pass on the next prepare, so it fails as any prepare may;
         * one validation rejected, or an operation that does not parse, is a [SwapRouteRefusedException].
         */
        fun from(prepared: CrossSwapPrepareV3): PreparedSwapOperation {
            when (prepared.validationStatus) {
                CrossSwapValidationStatus.validated -> Unit
                CrossSwapValidationStatus.pending -> throw IllegalStateException("Route ${prepared.routeId} is not validated yet")
                CrossSwapValidationStatus.rejected -> throw SwapRouteRefusedException("Route ${prepared.routeId} was rejected by validation")
            }

            val operationId = prepared.operation["id"]?.jsonPrimitive?.contentOrNull
                ?: throw SwapRouteRefusedException("Operation has no id")
            val operation = Operation.parse(operationId, prepared.operation.toString()).getOrElse {
                throw SwapRouteRefusedException("Operation $operationId does not parse", it)
            }

            if (operation.payloads.isEmpty()) {
                throw SwapRouteRefusedException("Operation $operationId has no payloads")
            }

            val summary = prepared.humanSummary
            return PreparedSwapOperation(
                operation = operation,
                providerRouteId = prepared.providerRouteId,
                buyBaseAmount = summary.receiveAmount?.toBaseAmountOrNull(),
                minimumBuyBaseAmount = summary.minimumReceiveAmount?.toBaseAmountOrNull(),
                depositAddress = summary.depositAddress?.takeIf { it.isNotBlank() },
                expiresAtMillis = prepared.dateExpire.toInstant().toEpochMilli(),
            )
        }

        private fun String.toBaseAmountOrNull(): BigInteger? = try {
            BigInteger.parseString(this)
        } catch (_: NumberFormatException) {
            null
        }
    }
}
