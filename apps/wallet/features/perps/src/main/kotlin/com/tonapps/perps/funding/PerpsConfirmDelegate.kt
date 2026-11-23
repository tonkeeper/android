package com.tonapps.perps.funding

import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.generated.Events.Perps.PerpsSourceScreen
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualConfirmDelegate
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualConfirmError
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualConfirmException
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualReview
import com.tonapps.log.L
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLoadException
import com.tonapps.perps.data.PerpsSendReason
import com.tonapps.perps.data.WITHDRAWAL_RATE_LIMITED
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

internal const val RELAY_PROVIDER = "Relay"
internal const val OMNISTON_PROVIDER = "Omniston"

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

    override suspend fun submit(review: PerpetualReview, wallet: MnemonicWallet) {
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
        val decimals = destination.source?.decimals ?: PerpsFundingAmount.USDC_DECIMALS
        return PerpetualReview(
            walletId = walletId,
            expiresAtMs = quote.expiresAtMs,
            display = PerpetualReview.Withdraw(
                symbol = symbol,
                network = destinationNetwork(destination),
                image = destination.source?.account?.asset,
                amount = formatUsdAmount(amount),
                receive = formatTokenAmount(PerpsFundingAmount.toDecimal(quote.outputUnits, decimals), symbol, decimals),
                fee = formatUsdAmount(cost.usd),
                feeIsEstimate = cost.isEstimate,
                arrivalSeconds = quote.estimatedDurationSeconds,
                recipient = destination.source?.account?.data?.displayAddress ?: destination.recipient,
                provider = RELAY_PROVIDER,
                feeShareBps = feeShareBps(cost.usd, amount),
            ),
            plan = this,
        )
    }

    private fun Throwable.toConfirmException(): PerpetualConfirmException {
        val requote = this is PerpsWithdrawException.QuoteExpired ||
            this is PerpsWithdrawException.QuoteChanged ||
            this is PerpsWithdrawException.FeeChanged ||
            this is PerpsWithdrawQuoteException.Expired
        val requoted = (this as? PerpsWithdrawException.QuoteChanged)?.plan?.toReview()
        return PerpetualConfirmException(toConfirmError(), requote = requote, requoted = requoted, cause = this)
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
            is PerpsWithdrawException.Refused -> when (reason) {
                PerpsSendReason.WITHDRAWAL_RATE_LIMITED -> PerpetualConfirmError.WithdrawalRateLimited
                PerpsSendReason.RATE_LIMITED -> PerpetualConfirmError.TooManyRequests
                PerpsSendReason.RESIGN_REQUIRED,
                PerpsSendReason.NO_RO_TOKEN,
                PerpsSendReason.ORDER_GONE,
                PerpsSendReason.BATCH_NOT_ACCEPTED,
                PerpsSendReason.INVALID_TRANSACTION,
                PerpsSendReason.LIGHTER_REJECTED,
                PerpsSendReason.INTEGRATOR_FEE_MISMATCH,
                PerpsSendReason.INTEGRATOR_NOT_APPROVED,
                PerpsSendReason.OTHER -> PerpetualConfirmError.Refused
            }
            is PerpsWithdrawException.QuoteExpired,
            is PerpsWithdrawException.QuoteChanged,
            is PerpsWithdrawException.FeeChanged,
            is PerpsWithdrawQuoteException.Expired -> PerpetualConfirmError.QuoteExpired
            is PerpsWithdrawException.SendFailed -> error.toConfirmError()
            is PerpsWithdrawException.RelayUnverified -> PerpetualConfirmError.PrepareFailed
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
                !error.retryable && (error.code == WITHDRAWAL_RATE_LIMITED || error.reason == WITHDRAWAL_RATE_LIMITED) ->
                    PerpetualConfirmError.WithdrawalRateLimited
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

    /**
     * A deposit confirms as the operation [quote] came with, signed as it is; a [preview] is authorized first.
     * [amountUnits] is what the user entered, not the quote's echo of it: the operation is validated against it.
     * [serverNowMs] is the server's time: the confirm screen expires the request on that clock.
     */
    fun deposit(
        walletId: String,
        sourceAssetId: String,
        amountUnits: BigInteger,
        beneficiary: String,
        quote: PerpetualDepositQuote,
        sourceScreen: PerpsSourceScreen?,
        serverNowMs: Long,
        preview: Boolean = false,
        payment: PerpetualDepositQuote? = null,
        payableByRelayer: Boolean = true,
        verifiedPayment: ConfirmType.PerpetualDeposit.VerifiedPayment? = null,
    ): ConfirmRequest {
        val crossChain = quote.route == PerpsDepositRoute.CROSSCHAIN_SWAP
        return ConfirmRequest(
            data = CommonTransactionData(
                assetId = sourceAssetId,
                walletId = walletId,
                // TODO TK-4608: expire with quote.expiresAtMs. Until then a deposit is signed within a
                // minute of being quoted, whatever the quote's own deadline.
                expiresAt = serverNowMs + DEPOSIT_SIGNING_WINDOW_MS,
                preview = preview,
            ),
            type = ConfirmType.PerpetualDeposit(
                operation = quote.operation,
                amount = amountUnits.toChain(),
                minReceive = quote.depositAmount.takeIf { crossChain },
                quote = ConfirmType.PerpetualDeposit.Quote(
                    id = quote.quoteId,
                    executionId = quote.executionId,
                    beneficiary = beneficiary,
                    depositAmount = quote.depositAmount,
                    crossChain = crossChain,
                ),
                estimatedDurationSeconds = quote.estimatedDurationSeconds,
                activatesAccount = quote.activatesAccount,
                providerFees = quote.providerFees?.let {
                    ConfirmType.PerpetualDeposit.ProviderFees(
                        destinationGasUsd = it.destinationGasUsd,
                        providerUsd = it.providerUsd,
                    )
                },
                sourceScreen = sourceScreen,
                paymentOperation = payment?.operation,
                payableByRelayer = payableByRelayer,
                verifiedPayment = verifiedPayment,
            ),
        )
    }

    /**
     * [destinationAssetId] is the asset the user receives, as quoted for.
     * The confirm screen quotes a withdrawal through [PerpsConfirmDelegate].
     */
    fun withdraw(walletId: String, amountUnits: BigInteger, destinationAssetId: String): ConfirmRequest = ConfirmRequest(
        data = CommonTransactionData(assetId = destinationAssetId, walletId = walletId),
        type = ConfirmType.PerpetualWithdraw(amount = amountUnits.toChain()),
    )
}
