package com.tonapps.perps.screens.withdraw

import androidx.compose.runtime.Immutable
import com.tonapps.perps.funding.PerpsWithdrawDestination
import com.tonapps.perps.screens.deposit.PerpsDepositSourceUi
import com.tonapps.perps.screens.deposit.toUi

@Immutable
data class PerpsWithdrawDestinationUi(
    val destination: PerpsWithdrawDestination,
    val source: PerpsDepositSourceUi?,
)

internal fun PerpsWithdrawDestination.toUi(hiddenBalance: Boolean): PerpsWithdrawDestinationUi =
    PerpsWithdrawDestinationUi(
        destination = this,
        source = source?.toUi(hiddenBalance),
    )
