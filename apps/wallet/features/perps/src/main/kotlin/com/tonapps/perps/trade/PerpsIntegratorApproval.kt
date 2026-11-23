package com.tonapps.perps.trade

import com.tonapps.chainkit.lighter.LighterConstants
import com.tonapps.chainkit.lighter.model.LighterApproveIntegratorTxReq
import com.tonapps.chainkit.lighter.trading.PerpsExecutionException
import com.tonapps.chainkit.lighter.trading.PerpsSubmissionOutcome
import com.tonapps.chainkit.perpetual.PerpsScope
import com.tonapps.log.L
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsSendReason
import com.tonapps.time.TimeProvider
import com.tonapps.wallet.data.multichain.lighter.PerpetualRepository
import com.tonapps.wallet.data.multichain.lighter.IntegratorApprovalKey
import com.tonapps.wallet.data.multichain.lighter.StoredIntegratorApproval
import io.perpsapi.models.Integrator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

fun interface PerpsOwnerMessageSigner {
    suspend fun signOwnerMessage(message: String): String?
}

/** Approval and transaction expiries are Lighter's: all of them are on [timeProvider]'s server clock. */
internal class PerpsIntegratorApproval(
    private val perpetualRepository: PerpetualRepository,
    private val relay: PerpsExecutionRelay,
    private val timeProvider: TimeProvider,
) {

    suspend fun renew(
        walletId: String,
        scope: PerpsScope,
        integrator: Integrator,
        unlock: suspend () -> PerpsOwnerMessageSigner?,
    ): PerpsTradeFailure? {
        val resyncFailure = withContext(NonCancellable) {
            perpetualRepository.forgetIntegratorApproval(walletId)
            try {
                relay.resyncNonce(scope)
                null
            } catch (e: PerpsExecutionException) {
                e.failure()
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                L.e(e)
                PerpsTradeFailure.SendFailed(PerpsError.Unknown)
            }
        }
        if (resyncFailure != null) {
            return resyncFailure
        }

        return ensure(walletId, scope, integrator) { withTimeoutOrNull(UNLOCK_TIMEOUT_MS) { unlock() } }
    }

    suspend fun ensure(
        walletId: String,
        scope: PerpsScope,
        integrator: Integrator,
        unlock: suspend () -> PerpsOwnerMessageSigner?,
    ): PerpsTradeFailure? {
        if (integrator.integratorAccountIndex == 0L) {
            perpetualRepository.forgetIntegratorApproval(walletId)
            return null
        }
        if (integrator.approvalTtlMs <= 0L ||
            integrator.approvalTtlMs > LighterConstants.MaxTimestamp - timeProvider.currentTimeMillis()
        ) {
            return PerpsTradeFailure.SendFailed(PerpsError.Unknown)
        }
        val key = IntegratorApprovalKey(
            accountIndex = scope.accountIndex,
            integratorAccountIndex = integrator.integratorAccountIndex,
            maxPerpsTakerFee = integrator.maxPerpsTakerFee,
            maxPerpsMakerFee = integrator.maxPerpsMakerFee,
            maxSpotTakerFee = integrator.maxSpotTakerFee,
            maxSpotMakerFee = integrator.maxSpotMakerFee,
        )
        val now = timeProvider.currentTimeMillis()
        val stored = perpetualRepository.integratorApproval(walletId)
        if (stored?.key == key && stored.expiryMs >= now + minOf(RENEWAL_WINDOW_MS, integrator.approvalTtlMs / 2)) {
            return null
        }

        val signer = try {
            unlock()
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            return PerpsTradeFailure.SendFailed(PerpsError.Unknown)
        } ?: return PerpsTradeFailure.ApprovalCancelled

        return withContext(NonCancellable) {
            catchUpNonce(scope)
            val signedAt = timeProvider.currentTimeMillis()
            val expiryMs = signedAt + integrator.approvalTtlMs
            val failure = try {
                val nonce = relay.nextNonce(scope).value
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
                    nonce = nonce,
                    expiredAtMs = minOf(signedAt + LighterConstants.DefaultExpireTimeMillis, LighterConstants.MaxTimestamp),
                    signOwnerMessage = signer::signOwnerMessage,
                )
                when (tx?.let { relay.submitTransaction(walletId, it.txType, it.getTxInfo(), it.getTxHash()) }) {
                    PerpsSubmissionOutcome.Accepted -> {
                        relay.markNonceAccepted(scope, nonce)
                        null
                    }
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
                perpetualRepository.saveIntegratorApproval(walletId, StoredIntegratorApproval(key, expiryMs))
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
            failure
        }
    }

    private suspend fun catchUpNonce(scope: PerpsScope) {
        try {
            relay.catchUpNonce(scope)
        } catch (e: PerpsExecutionException) {
            L.e(e)
        }
    }

    private companion object {
        const val RENEWAL_WINDOW_MS = 86_400_000L
        const val UNLOCK_TIMEOUT_MS = 120_000L
    }
}
