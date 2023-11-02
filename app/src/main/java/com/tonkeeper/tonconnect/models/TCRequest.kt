package com.tonkeeper.tonconnect.models

import android.net.Uri

data class TCRequest(
    val v: Int = 2,
    val id: String,
    val r: String
) {

    val payload = TCPayload(r)

    constructor(uri: Uri) : this(
        v = uri.getQueryParameter("v")?.toInt() ?: throw IllegalArgumentException("v is required"),
        id = uri.getQueryParameter("id") ?: throw IllegalArgumentException("id is required"),
        r = uri.getQueryParameter("r") ?: throw IllegalArgumentException("r is required")
    )
}