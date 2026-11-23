package com.tonapps.perps.screens.deposit

import androidx.compose.runtime.Immutable
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.perps.funding.PerpsDepositQuoteError
import com.tonapps.perps.funding.refusesAmount
import com.tonapps.perps.funding.retryable
import java.math.BigDecimal

@Immutable
sealed interface PerpsDepositQuotePreview {
    data object None : PerpsDepositQuotePreview
    data object Loading : PerpsDepositQuotePreview
    data class Ready(
        val request: ConfirmRequest,
        val youGetUsd: BigDecimal,
        val feeUsd: BigDecimal?,
        val activatesAccount: Boolean,
    ) : PerpsDepositQuotePreview

    data class Failed(val error: PerpsDepositQuoteError) : PerpsDepositQuotePreview
}

val PerpsDepositQuotePreview.retryableFailure: Boolean
    get() = this is PerpsDepositQuotePreview.Failed && error.retryable

val PerpsDepositQuotePreview.amountRefusal: PerpsDepositQuoteError?
    get() = (this as? PerpsDepositQuotePreview.Failed)?.error?.takeIf { it.refusesAmount }
