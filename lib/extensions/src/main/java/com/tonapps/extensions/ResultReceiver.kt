package com.tonapps.extensions

import android.os.Parcel
import android.os.ResultReceiver

fun ResultReceiver.toIpcFriendly(): ResultReceiver? {
    val parcel: Parcel = Parcel.obtain()
    writeToParcel(parcel, 0)
    parcel.setDataPosition(0)
    val ipcFriendly = ResultReceiver.CREATOR.createFromParcel(parcel)
    parcel.recycle()
    return ipcFriendly
}
