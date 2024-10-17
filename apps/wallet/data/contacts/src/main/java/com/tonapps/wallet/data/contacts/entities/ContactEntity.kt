package com.tonapps.wallet.data.contacts.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ContactEntity(
    val id: Long,
    val name: String,
    val address: String,
    val date: Long
): Parcelable