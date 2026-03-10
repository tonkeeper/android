package com.tonapps.tonkeeper.manager.tonconnect.bridge

import android.os.Parcelable
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import kotlinx.parcelize.Parcelize

@Parcelize
class BridgeException(
    val connect: AppConnectEntity? = null,
    override val cause: Throwable? = null,
    override val message: String = if (cause is BridgeException) cause.message else "",
): Exception(message, cause), Parcelable