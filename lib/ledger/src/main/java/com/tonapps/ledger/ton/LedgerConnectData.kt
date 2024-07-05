package com.tonapps.ledger.ton

import android.os.Parcelable
import com.tonapps.ledger.devices.DeviceModel
import kotlinx.parcelize.Parcelize

@Parcelize
data class LedgerConnectData(
    val accounts: List<LedgerAccount>,
    val deviceId: String,
    val model: DeviceModel
): Parcelable
