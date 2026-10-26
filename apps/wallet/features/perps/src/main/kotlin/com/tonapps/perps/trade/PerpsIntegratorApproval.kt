package com.tonapps.perps.trade

import com.tonapps.chainkit.lighter.LighterConstants
import com.tonapps.chainkit.lighter.model.LighterApproveIntegratorTxReq
import com.tonapps.chainkit.lighter.trading.PerpsExecutionException
import com.tonapps.chainkit.lighter.trading.PerpsSubmissionOutcome
import com.tonapps.chainkit.perpetual.PerpsScope
import com.tonapps.log.L
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsSendReason
import com.tonapps.wallet.data.multichain.lighter.PerpetualRepository
import io.perpsapi.models.Integrator
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

internal class PerpsIntegratorApproval(
    private val perpetualRepository: PerpetualRepository,
    private val relay: PerpsExecutionRelay,
) {

    private data class Approval(val integrator: Integrator, val accountIndex: Long, val expiryMs: Long)

    private val approvals = ConcurrentHashMap<String, Approval>()

    suspend fun renew(walletId: String, scope: PerpsScope, integrator: Integrator): PerpsTradeFailure? {
        approvals.remove(walletId)
        try {
            relay.resyncNonce(scope)
        } catch (e: PerpsExecutionException) {
            return e.failure()
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            return PerpsTradeFailure.SendFailed(PerpsError.Unknown)
        }

        return ensure(walletId, scope, integrator)
    }

    suspend fun ensure(walletId: String, scope: PerpsScope, integrator: Integrator): PerpsTradeFailure? {
        if (integrator.integratorAccountIndex == 0L) {
            approvals.remove(walletId)
            return null
        }
        if (integrator.approvalTtlMs <= 0L) {
            return PerpsTradeFailure.SendFailed(PerpsError.Unknown)
        }
        val now = System.currentTimeMillis()
        val cached = approvals[walletId]
        if (cached?.integrator == integrator && cached.accountIndex == scope.accountIndex &&
            cached.expiryMs >= now + MIN_REMAINING_MS
        ) {
            return null
        }

        val expiryMs = now + integrator.approvalTtlMs
        val failure = try {
            val tx = perpetualRepository.approveIntegratorTransaction(
                walletId = walletId,
                request = LighterApproveIntegratorTxReq(
                    integratorAccountIndex = integrator.integratorAccountIndex,
                    maxPerpsTakerFee = integrator.maxPerpsTakerFee,
                    maxPerpsMakerFee = integrator.maxPerpsMakerFee,
                    maxSpotTakerFee = integrator.maxSpotTakerFee,
                    maxSpotMakerFee = integrator.maxSpotMakerFee,
                    approvalExpiry = expiryMs,
                ),
                nonce = relay.nextNonce(scope).value,
                expiredAtMs = minOf(now + LighterConstants.DefaultExpireTimeMillis, LighterConstants.MaxTimestamp),
            )
            when (tx?.let { relay.submitTransaction(walletId, it.txType, it.getTxInfo(), it.getTxHash()) }) {
                PerpsSubmissionOutcome.Accepted -> null
                PerpsSubmissionOutcome.ResignRequired -> PerpsTradeFailure.SendRefused(PerpsSendReason.RESIGN_REQUIRED, "")
                else -> PerpsTradeFailure.SendFailed(PerpsError.Unknown)
            }
        } catch (e: PerpsExecutionException) {
            e.failure()
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            PerpsTradeFailure.SendFailed(PerpsError.Unknown)
        }

        if (failure == null) {
            approvals[walletId] = Approval(integrator, scope.accountIndex, expiryMs)
        } else {
            try {
                relay.resyncNonce(scope)
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                L.e(e)
            }
        }
        return failure
    }

    private companion object {
        const val MIN_REMAINING_MS = 1_000L
    }
}
