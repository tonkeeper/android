package com.tonapps.deposit.multicoin

import com.tonapps.deposit.screens.ramp.RampType

// The same flow sells to a card, so the new screen replaces only the buying path.
internal fun buyCryptoEnabled(rampType: RampType, flagEnabled: Boolean): Boolean {
    return rampType == RampType.RampOn && flagEnabled
}

internal fun startRoute(initial: DepositMulticoinRoutes?, buyCrypto: Boolean): DepositMulticoinRoutes {
    return when {
        initial is DepositMulticoinRoutes.Buy && buyCrypto -> DepositMulticoinRoutes.BuyCrypto(initial.assetId, initial.preferredCurrency)
        initial == null && buyCrypto -> DepositMulticoinRoutes.BuyCrypto()
        else -> initial ?: DepositMulticoinRoutes.Ramp
    }
}
