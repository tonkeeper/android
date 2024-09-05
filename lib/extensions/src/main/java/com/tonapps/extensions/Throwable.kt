package com.tonapps.extensions

import android.content.Context
import androidx.annotation.StringRes

val Throwable.bestMessage: String
    get() = localizedMessage ?: message ?: toString()


fun Throwable.getUserMessage(context: Context): String? {
    return when (this) {
        is ErrorForUserException -> context.getString(stringRes)
        else -> null
    }
}

open class ErrorForUserException(
    @StringRes val stringRes: Int,
    override val cause: Throwable? = null,
) : Exception(cause)