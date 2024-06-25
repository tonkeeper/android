package com.tonapps.ledger.devices

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BluetoothSpec(
    val serviceUuid: String,
    val notifyUuid: String,
    val writeUuid: String,
    val writeCmdUuid: String
): Parcelable