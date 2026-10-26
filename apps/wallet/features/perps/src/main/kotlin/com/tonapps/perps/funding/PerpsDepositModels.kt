package com.tonapps.perps.funding

import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationErrorCause
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.lighter.PerpsQuoteError
import java.math.BigDecimal
import java.math.BigInteger

data class PerpsDepositSource(
    val account: AccountWithDetails,
    val fundingAsset: PerpsFundingAssetId,
) {
    val assetId: String
        get() = account.asset.id

    val symbol: String
        get() = account.asset.symbol

    val decimals: Int
        get() = account.asset.decimals

    val imageUrl: String
        get() = account.asset.imageUrl

    val balanceUnits: BigInteger
        get() = BigInteger(account.unitBalance.value.toString())

    val balance: BigDecimal
        get() = PerpsFundingAmount.toDecimal(balanceUnits, decimals)

    val price: BigDecimal?
        get() = account.rate?.value?.value?.toString()?.let(::BigDecimal)

    val fiatValue: BigDecimal
        get() = BigDecimal(account.fiatValue.toString())

    val isBestValue: Boolean
        get() = fundingAsset.raw.equals(PerpsFundingAssetId.USDC_ETHEREUM, ignoreCase = true)
}

data class PerpsDepositMinimum(
    val usd: BigDecimal,
    val units: BigInteger,
)

sealed class PerpsDepositException(message: String) : IllegalStateException(message) {
    class QuoteRejected(val error: PerpsQuoteError) : PerpsDepositException("quote rejected: $error")
    class InsufficientBalance : PerpsDepositException("insufficient balance")
    class InsufficientNetworkFee(val symbol: String) : PerpsDepositException("insufficient $symbol for the network fee")
    class SigningFailed(reason: String?) : PerpsDepositException("signing failed: $reason")
    class WalletUnavailable : PerpsDepositException("wallet has no account on the source chain")
    class PaymentInFlight(operationId: String) :
        PerpsDepositException("deposit $operationId is still in flight"),
        ConfirmationErrorCause {
        override val confirmationError: ConfirmationError = ConfirmationError.DepositInFlight
    }
}
