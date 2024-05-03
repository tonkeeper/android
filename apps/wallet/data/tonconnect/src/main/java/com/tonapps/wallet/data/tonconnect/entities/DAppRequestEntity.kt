package com.tonapps.wallet.data.tonconnect.entities


import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class DAppRequestEntity(
    val v: Int = 2,
    val id: String,
    val r: String,
    val ret: String? = null,
) : Parcelable {

    @IgnoredOnParcel
    val payload = DAppPayloadEntity(JSONObject(r))

    constructor(uri: Uri) : this(
        v = uri.getQueryParameter("v")?.toInt() ?: throw IllegalArgumentException("v is required"),
        id = uri.getQueryParameter("id") ?: throw IllegalArgumentException("id is required"),
        r = uri.getQueryParameter("r") ?: throw IllegalArgumentException("r is required"),
        ret = uri.getQueryParameter("ret")
    )
}