package com.tonapps.deposit.usecase.emulation

import android.os.Parcelable
import com.tonapps.icu.Coins
import kotlinx.parcelize.Parcelize

@Parcelize
data class TronFeesEmulation(
    val ton: Coins?,
    val trx: Coins?,
    val batteryCharges: Int?,
): Parcelable