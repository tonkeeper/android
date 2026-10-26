package com.tonapps.deposit.multicoin.screens.confirm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmOperations
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.chainkit.core.chain.model.num.toDisplayUnit
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.deposit.multicoin.screens.confirm.base.Signing
import com.tonapps.deposit.multicoin.screens.confirm.fee.FeeAccount
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.Plurals
import kotlin.math.abs

@get:Composable
val ConfirmationError.errorText: String? get() = when (this) {
    ConfirmationError.Unknown -> stringResource(Localization.unknown_error)
    ConfirmationError.OutcomeUnknown -> stringResource(Localization.unknown_error)
    ConfirmationError.InsufficientBalance -> stringResource(Localization.insufficient_balance)
    ConfirmationError.Expired -> stringResource(Localization.transaction_expired)
    ConfirmationError.InvalidAddress -> stringResource(Localization.confirmation_error_invalid_address)
    ConfirmationError.UnsupportedTransaction -> stringResource(Localization.confirmation_error_unsupported_transaction)
    ConfirmationError.UnsupportedAsset -> stringResource(Localization.confirmation_error_unsupported_asset)
    ConfirmationError.MissingField -> stringResource(Localization.confirmation_error_missing_field)
    ConfirmationError.DustAmount -> stringResource(Localization.confirmation_error_dust_amount)
    ConfirmationError.VerificationFailed -> stringResource(Localization.confirmation_error_verification_failed)
    ConfirmationError.SignInternalError -> stringResource(Localization.confirmation_error_sign_internal)
    ConfirmationError.Unauthorized -> stringResource(Localization.confirmation_error_unauthorized)
    ConfirmationError.NoAvailableNodes -> stringResource(Localization.confirmation_error_no_nodes)
    ConfirmationError.NetworkError -> stringResource(Localization.confirmation_error_network)
    ConfirmationError.BadResponse -> stringResource(Localization.confirmation_error_bad_response)
    ConfirmationError.BitcoinError -> stringResource(Localization.confirmation_error_bitcoin)
    ConfirmationError.MemPoolConflict -> stringResource(Localization.confirmation_error_mempool_conflict)
    ConfirmationError.UtxoError -> stringResource(Localization.confirmation_error_utxo)
    ConfirmationError.InternalError -> stringResource(Localization.confirmation_error_internal)
    is ConfirmationError.NodeRejected -> message.ifBlank { stringResource(Localization.unknown_error) }
    is ConfirmationError.RelayerRejected -> stringResource(Localization.failed)
    ConfirmationError.DepositInFlight -> stringResource(Localization.perps_deposit_payment_in_flight)
}

@Composable
fun PendingTransaction.signingContent(): String? {
    return when (signing) {
        is Signing.Msg -> signing.value.data
        is Signing.Tx -> when (signing.value) {
            is Transaction.Call -> signing.value.data
            else -> null
        }
    }
}

@Composable
fun PendingTransaction.amount(pickedFeeId: String?): Pair<String, String?>? {
    val amount = when (signing) {
        is Signing.Msg -> null
        is Signing.Tx -> when (val sent = signing.sentWith(fee.resolve(pickedFeeId))) {
            is Transaction.Call if (sent.amount == BigInteger.ZERO) -> null
            else -> sent.amount
        }
    }

    amount ?: return null

    val asset = account.asset
    val rate = account.rate?.value

    val amountDisplay = asset.value.toDisplayUnit(amount)
    val amountFormatted = remember(amountDisplay) {
        Formatter.formatShort(value = amountDisplay, asset = asset.value)
    }
    val amountFiatFormatted = remember(amountDisplay, fee) {
        rate?.let { Formatter.formatFiat(value = amountDisplay, rate = it, separator = " ") }
    }

    return amountFormatted to amountFiatFormatted
}

@Composable
fun PendingTransaction.comment(): String? {
    return when (signing) {
        is Signing.Msg -> null
        is Signing.Tx -> when (val tx = signing.value) {
            // An encrypted comment is out of the transaction until signing; the operation keeps it.
            is Transaction.Transfer -> tx.memo ?: (request.type as? ConfirmType.Transfer)?.operation?.let(ConfirmOperations::comment)
            else -> null
        }
    }
}

@Composable
fun PendingTransaction.isCommentEncrypted(): Boolean = (request.type as? ConfirmType.Transfer)?.commentEncryptionKey != null

@Composable
fun PendingTransaction.isMax(): Boolean? {
    return when (signing) {
        is Signing.Msg -> null
        is Signing.Tx -> when (val tx = signing.value) {
            is Transaction.Transfer -> tx.isMax
            else -> null
        }
    }
}

@Composable
fun PendingTransaction.recipientAddress(): String? {
    return when (signing) {
        is Signing.Msg -> null
        is Signing.Tx -> when (val tx = signing.value) {
            is Transaction.Transfer -> tx.to.display
            is Transaction.Call -> tx.contract.display
            is Transaction.Staking.Claim -> tx.contract
            is Transaction.Staking.Stake -> tx.contract
            is Transaction.Staking.Unstake -> tx.contract
            is Transaction.Staking.Compound -> null
            is Transaction.Staking.Restake -> null
            is Transaction.Swap -> tx.to.display
        }
    }
}

@Composable
fun PendingTransaction.appName(): String? {
    return when (val context = request.context) {
        is ConfirmContext.Dapp -> context.name
        else -> null
    }
}

private val RATE_DIVISION_MODE = DecimalMode(
    decimalPrecision = 30L,
    roundingMode = RoundingMode.ROUND_HALF_CEILING,
    scale = 8L,
)


// [Transaction.Swap.amount] is the chain-level value the aggregator asked us to attach — the message
// value in nanotons for a TON jetton swap, zero for an ERC20 sell — so the sold amount can only come
// from the quote.
@Composable
fun PendingTransaction.sellAmount(): String? {
    val quote = quote ?: return null
    val asset = account.asset.value

    val display = asset.toDisplayUnit(quote.sourceBaseAmount)
    return remember(display) {
        Formatter.formatShort(value = display, asset = asset)
    }
}

@Composable
fun PendingTransaction.minReceived(): Pair<String, String?>? {
    val quote = quote ?: return null
    val destination = destination ?: return null
    val asset = destination.asset
    val rate = destination.rate?.value

    val display = asset.value.toDisplayUnit(quote.minimumBuyBaseAmount)
    val amountFormatted = remember(display) {
        Formatter.formatShort(value = display, asset = asset.value, approximate = true)
    }
    val fiatFormatted = remember(display, rate) {
        rate?.let { Formatter.formatFiat(value = display, rate = it, separator = " ", approximate = true) }
    }
    return amountFormatted to fiatFormatted
}

@Composable
fun PendingTransaction.slippage(): String? {
    val bps = quote?.slippageBps ?: return null
    val percent = BigDecimal.fromInt(bps).divide(BigDecimal.fromInt(100), RATE_DIVISION_MODE)
    return Formatter.formatPercent(percent, scale = 2)
}

enum class PriceImpactSeverity { Normal, Warning, Danger }

data class PriceImpact(
    val text: String,
    val severity: PriceImpactSeverity,
)

@Composable
fun PendingTransaction.priceImpact(): PriceImpact? {
    val bps = quote?.priceImpactBps ?: return null
    if (abs(bps) < 300) {
        return null
    }
    val percent = BigDecimal.fromInt(bps).divide(BigDecimal.fromInt(100), RATE_DIVISION_MODE)
    val formatted = Formatter.formatPercent(percent, scale = 2)
    // Mirrors valueDifferenceBps in OmnistonScreen: positive moves price in your favor, negative against.
    val text = if (bps > 0) {
        "+$formatted"
    } else {
        formatted
    }
    val severity = when {
        percent < BigDecimal.fromInt(-5) -> PriceImpactSeverity.Danger
        percent <= BigDecimal.fromInt(-3) -> PriceImpactSeverity.Warning
        else -> PriceImpactSeverity.Normal
    }
    return PriceImpact(text = text, severity = severity)
}

@Composable
fun TxFee.title(): String = when (val account = account) {
    is FeeAccount.Keeper -> stringResource(Localization.battery_refill_title)
    is FeeAccount.Chain -> account.value.asset.symbol
}

// Fits next to the amount in the fee row, where the full "Keeper Battery" would crowd it out.
@Composable
fun TxFee.shortTitle(): String = when (val account = account) {
    is FeeAccount.Keeper -> stringResource(Localization.battery)
    is FeeAccount.Chain -> account.value.asset.symbol
}

/**
 * What the method is quoted in: charges for battery — its own unit, and the only one a user can
 * check against their balance — the fiat value for anything settled on chain.
 */
@Composable
fun TxFee.quotedValue(): String {
    val (amount, fiat) = formattedValue()
    return when (account) {
        is FeeAccount.Keeper -> amount
        is FeeAccount.Chain -> fiat ?: amount
    }
}

/** Same rule, plus the asset amount as the detail line for chain methods. */
@Composable
fun TxFee.quotedDetails(): String {
    val (amount, fiat) = formattedValue()
    return when (account) {
        is FeeAccount.Keeper -> amount
        is FeeAccount.Chain -> listOfNotNull(fiat, amount).joinToString(" · ")
    }
}

@Composable
fun TxFee.formattedValue(): Pair<String, String?> {
    // Nothing to quote yet: a dash, and no fiat "≈ 0".
    if (isUnpriced) {
        return "—" to null
    }
    val display = fee.asset.decimals.toDisplayUnit(fee.amount)
    val fiat = account.rate?.let {
        Formatter.formatFiat(value = display, rate = it, separator = " ", approximate = true)
    }
    val amount = when (val account = account) {
        // Battery is quoted in charges, so the amount is the charge count itself.
        is FeeAccount.Keeper -> {
            val charges = fee.amount.intValue(exactRequired = false)
            pluralStringResource(Plurals.battery_charges, charges, charges)
        }

        is FeeAccount.Chain -> remember(display) {
            "${Formatter.formatShort(value = display, approximate = true)} ${account.value.asset.symbol}"
        }
    }
    return amount to fiat
}

@Composable
fun PendingTransaction.fee(): Pair<String, String?>? {
    val estimated = fee.estimated ?: return null
    val energy = fee.energy ?: return null

    val feeDisplay = energy.value.asset.decimals.toDisplayUnit(estimated.amount)

    val feeFormatted = remember(feeDisplay) {
        "${Formatter.formatShort(value = feeDisplay, approximate = true)} ${energy.asset.symbol}"
    }

    val feeFiatFormatted = remember(feeDisplay, energy.rate) {
        energy.rate?.let {
            Formatter.formatFiat(value = feeDisplay, rate = it.value, separator = " ", approximate = true)
        }
    }

    return feeFormatted to feeFiatFormatted
}
