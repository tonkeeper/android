package com.tonapps.perps.funding

import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualConfirmDelegate
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualConfirmError
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualConfirmException
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualReview
import com.tonapps.log.L
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLoadException
import com.tonapps.perps.data.formatTokenAmount
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.perps.screens.withdraw.destinationNetwork
import com.tonapps.perps.screens.withdraw.destinationTitle
import com.tonapps.wallet.data.multichain.lighter.PerpetualDepositQuote
import com.tonapps.wallet.data.multichain.perps.PerpsDepositRoute
import kotlinx.coroutines.CancellationException
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/** Perps withdrawals behind the shared confirm screen; a deposit confirms as its quote's operation. */
class PerpsConfirmDelegate(
    private val withdrawService: PerpsWithdrawService,
) : PerpetualConfirmDelegate {

    override suspend fun review(request: ConfirmRequest, withdraw: ConfirmType.PerpetualWithdraw): PerpetualReview {
        val walletId = request.data.walletId
        val amountUnits = BigInteger(withdraw.amount.toString())
        return try {
            withdrawService.prepare(walletId, amountUnits, request.data.assetId).toReview()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw e.toConfirmException()
        }
    }

    override suspend fun submit(review: PerpetualReview, wallet: CryptoWallet) {
        try {
            when (val plan = review.plan) {
                is PerpsWithdrawPlan -> try {
                    withdrawService.submit(plan, wallet)
                } catch (e: PerpsWithdrawException.SendUnknown) {
                    L.e(e)
                }

                else -> throw IllegalArgumentException("Not a perps plan: ${plan::class.simpleName}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw e.toConfirmException()
        }
    }

    private fun PerpsWithdrawPlan.toReview(): PerpetualReview {
        val symbol = destinationTitle(destination)
        val quote = quote
        // A quoted withdrawal lands in another asset: what arrives is the quote's output, in its units.
        val receive = if (quote != null) {
            val decimals = destination.source?.decimals ?: PerpsFundingAmount.USDC_DECIMALS
            formatTokenAmount(PerpsFundingAmount.toDecimal(quote.outputUnits, decimals), symbol, decimals)
        } else {
            formatTokenAmount(amount, PerpsWithdrawService.USDC_SYMBOL, PerpsFundingAmount.USDC_DECIMALS)
        }
        val arrivalSeconds = if (quote != null) {
            quote.estimatedDurationSeconds
        } else {
            limits.delaySeconds
        }
        return PerpetualReview(
            walletId = walletId,
            expiresAtMs = quote?.expiresAtMs,
            display = PerpetualReview.Withdraw(
                symbol = symbol,
                network = destinationNetwork(destination),
                image = destination.source?.account?.asset,
                amount = formatUsdAmount(amount),
                receive = receive,
                fee = cost?.let { formatUsdAmount(it.usd) },
                feeIsEstimate = cost?.isEstimate == true,
                arrivalSeconds = arrivalSeconds,
                feeShareBps = cost?.let { feeShareBps(it.usd, amount) },
            ),
            plan = this,
        )
    }

    private fun Throwable.toConfirmException(): PerpetualConfirmException {
        val requote = this is PerpsWithdrawException.QuoteExpired ||
            this is PerpsWithdrawException.FeeChanged ||
            this is PerpsWithdrawQuoteException.Expired
        return PerpetualConfirmException(toConfirmError(), requote = requote, cause = this)
    }

    private fun Throwable.toConfirmError(): PerpetualConfirmError {
        return when (this) {
            is PerpsWithdrawException.WithdrawInFlight -> PerpetualConfirmError.InFlight
            is PerpsWithdrawException.InsufficientBalance -> PerpetualConfirmError.InsufficientBalance
            is PerpsWithdrawException.BelowMinimum -> PerpetualConfirmError.BelowMinimum(
                minAmount = PerpsFundingAmount.declaredAmount(minUnits, PerpsFundingAmount.USDC_DECIMALS),
            )
            is PerpsWithdrawException.AccountUnavailable,
            is PerpsWithdrawException.ActivationFailed -> PerpetualConfirmError.AccountUnavailable
            is PerpsWithdrawException.Refused -> PerpetualConfirmError.Refused
            is PerpsWithdrawException.QuoteExpired,
            is PerpsWithdrawException.FeeChanged,
            is PerpsWithdrawQuoteException.Expired -> PerpetualConfirmError.QuoteExpired
            is PerpsWithdrawException.SendFailed -> error.toConfirmError()
            is PerpsWithdrawException -> PerpetualConfirmError.Failed
            is PerpsWithdrawQuoteException -> PerpetualConfirmError.PrepareFailed

            is PerpsLoadException -> error.toConfirmError()
            else -> toWithdrawFundingError()
        }
    }

    private fun Throwable.toWithdrawFundingError(): PerpetualConfirmError {
        return when (val error = toPerpsFundingError()) {
            is PerpsFundingError.Transport -> error.error.toConfirmError()
            is PerpsFundingError.Refused -> when {
                error.retryable -> PerpetualConfirmError.ServiceUnavailable
                error.reason == REASON_AMOUNT_TOO_LOW -> PerpetualConfirmError.AmountTooLow
                else -> PerpetualConfirmError.PrepareFailed
            }
            PerpsFundingError.Unknown -> PerpetualConfirmError.Failed
        }
    }

    private fun PerpsError.toConfirmError(): PerpetualConfirmError {
        return when (this) {
            PerpsError.Network,
            PerpsError.ServiceUnavailable -> PerpetualConfirmError.ServiceUnavailable

            PerpsError.NoAccount -> PerpetualConfirmError.AccountUnavailable
            else -> PerpetualConfirmError.PrepareFailed
        }
    }

    private fun feeShareBps(feeUsd: BigDecimal, amountUsd: BigDecimal): Int? {
        if (amountUsd.signum() <= 0) {
            return null
        }
        return feeUsd.multiply(BPS_DECIMAL).divide(amountUsd, 0, RoundingMode.DOWN).max(BigDecimal.ZERO).toInt()
    }

    private companion object {
        val BPS_DECIMAL: BigDecimal = BigDecimal.valueOf(10_000L)
    }
}

/** Confirm requests for perps funding, as the shared confirm screen takes them. */
object PerpsConfirmRequests {

    private const val DEPOSIT_SIGNING_WINDOW_MS = 60_000L

    /** A deposit confirms as the operation [quote] came with, signed as it is; a [preview] is authorized first. */
    fun deposit(
        walletId: String,
        sourceAssetId: String,
        beneficiary: String,
        quote: PerpetualDepositQuote,
        preview: Boolean = false,
        feeShareBps: Int? = null,
    ): ConfirmRequest {
        val crossChain = quote.route == PerpsDepositRoute.CROSSCHAIN_SWAP
        return ConfirmRequest(
            data = CommonTransactionData(
                assetId = sourceAssetId,
                walletId = walletId,
                // TODO TK-4608: expire with quote.expiresAtMs. Until then a deposit is signed within a
                // minute of being quoted, whatever the quote's own deadline.
                expiresAt = System.currentTimeMillis() + DEPOSIT_SIGNING_WINDOW_MS,
                preview = preview,
            ),
            type = ConfirmType.PerpetualDeposit(
                operation = quote.operation,
                amount = quote.sourceAmount,
                minReceive = quote.depositAmount.takeIf { crossChain },
                quote = ConfirmType.PerpetualDeposit.Quote(
                    id = quote.quoteId,
                    executionId = quote.executionId,
                    beneficiary = beneficiary,
                    depositAmount = quote.depositAmount,
                    crossChain = crossChain,
                ),
                feeShareBps = feeShareBps,
                estimatedDurationSeconds = quote.estimatedDurationSeconds,
            ),
        )
    }

    /**
     * [destinationAssetId] is what the user receives: USDC on Ethereum, or another asset quoted for.
     * The confirm screen quotes a withdrawal through [PerpsConfirmDelegate].
     */
    fun withdraw(walletId: String, amountUnits: BigInteger, destinationAssetId: String): ConfirmRequest = ConfirmRequest(
        data = CommonTransactionData(assetId = destinationAssetId, walletId = walletId),
        type = ConfirmType.PerpetualWithdraw(amount = amountUnits.toChain()),
    )
}
