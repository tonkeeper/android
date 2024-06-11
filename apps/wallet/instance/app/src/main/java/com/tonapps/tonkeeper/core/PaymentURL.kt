package com.tonapps.tonkeeper.core

import android.net.Uri

class PaymentURL(val uri: Uri) {

    companion object {
        const val ACTION_TRANSFER = "transfer"
    }

    constructor(url: String) : this(
        Uri.parse(url)
    )

    val action: String?
        get() {
            return if (uri.scheme == "ton") {
                uri.host
            } else {
                uri.pathSegments.firstOrNull()
            }
        }

    val address: String?
        get() = uri.lastPathSegment

    val text: String?
        get() = uri.getQueryParameter("text")

    val amount: Long
        get() = uri.getQueryParameter("amount")?.toLongOrNull() ?: 0L

    val jettonAddress: String?
        get() = uri.getQueryParameter("jetton")

    override fun toString(): String {
        return "PaymentURL(action=$action;address=$address;text=$text;amount=$amount)"
    }
}