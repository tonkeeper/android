package com.tonapps.tonkeeper.ui.screen.send.main

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SendContact(
    val type: Int,
    val address: String,
): Parcelable {

    companion object {
        const val MY_WALLET_TYPE = 1
        const val CONTACT_TYPE = 2
    }
}