package com.tonapps.tonkeeper.core.widget.rate

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class WidgetRateEntity(
    val diff24h: String,
    val diff7d: String,
    val price: CharSequence,
    val date: String
): Parcelable