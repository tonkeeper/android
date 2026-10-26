package com.tonapps.perps.funding

import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsSendError
import com.tonapps.perps.data.PerpsSendReason
import com.tonapps.wallet.data.multichain.perps.PerpsFundingRecord
import java.math.BigDecimal
import java.math.BigInteger

data class PerpsWithdrawDestination(
    val assetId: String,
    val recipient: String,
    val source: PerpsDepositSource?,
    val transferFeeUnits: BigInteger?,
) {
    val isLighterWithdraw: Boolean
        get() = assetId.equals(PerpsFundingAssetId.USDC_ETHEREUM, ignoreCase = true)
}

data class PerpsWithdrawLimits(
    val minWithdrawalUnits: BigInteger,
    val minTransferUnits: BigInteger,
    val delaySeconds: Long?,
) {
    fun minimumFor(destination: PerpsWithdrawDestination): BigInteger {
        if (destination.isLighterWithdraw) {
            return minWithdrawalUnits
        }
        return minTransferUnits + (destination.transferFeeUnits ?: BigInteger.ZERO)
    }
}

data class PerpsWithdrawCost(
    val usd: BigDecimal,
    val isEstimate: Boolean,
)

data class PerpsWithdrawPlan(
    val walletId: String,
    val amountUnits: BigInteger,
    val destination: PerpsWithdrawDestination,
    val limits: PerpsWithdrawLimits,
    val quote: PerpsWithdrawQuote?,
    val cost: PerpsWithdrawCost?,
) {
    val amount: BigDecimal
        get() = PerpsFundingAmount.toDecimal(amountUnits, PerpsFundingAmount.USDC_DECIMALS)
}

class PerpsWithdrawSend(
    val record: PerpsFundingRecord,
    val error: PerpsSendError?,
)

sealed class PerpsWithdrawException(message: String) : IllegalStateException(message) {
    class WithdrawInFlight(val operationId: String) : PerpsWithdrawException("withdrawal $operationId is still unresolved")
    class InsufficientBalance : PerpsWithdrawException("insufficient perps balance")
    class BelowMinimum(val minUnits: BigInteger) : PerpsWithdrawException("withdrawal is below $minUnits units")
    class AccountUnavailable : PerpsWithdrawException("perps account is not bound to an address")
    class DestinationUnavailable(assetId: String) : PerpsWithdrawException("$assetId is not a withdrawal destination")
    class QuoteExpired : PerpsWithdrawException("withdraw quote expired")
    class FeeChanged(quoted: BigInteger, current: BigInteger) :
        PerpsWithdrawException("Lighter transfer fee is $current units, the quote has $quoted")
    class ActivationFailed : PerpsWithdrawException("perps account activation failed")
    class SigningFailed(reason: String?) : PerpsWithdrawException("signing failed: $reason")
    class Refused(val reason: PerpsSendReason, val code: String) : PerpsWithdrawException("send refused: $reason $code")
    class SendFailed(val error: PerpsError) : PerpsWithdrawException("send failed: $error")
    class SendUnknown : PerpsWithdrawException("send outcome unknown")
}
