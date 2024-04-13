package com.tonapps.wallet.data.push.entities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class AppPushEntity(
    val account: String,
    val link: String,
    val title: String?,
    val message: String,
    val dappUrl: String,
    val from: String,
    val dateUnix: Long
): Parcelable {

    val intent = Intent(Intent.ACTION_VIEW).apply {
        val builder = Uri.Builder()
        builder.scheme("tonkeeper")
        builder.authority("dapp")
        builder.appendQueryParameter("link", link)
        builder.appendQueryParameter("account", account)
        builder.appendQueryParameter("url", dappUrl)
        data = builder.build()
    }

    constructor(bundle: Bundle) : this(
        account = bundle.getString("account") ?: throw IllegalArgumentException("account not found"),
        link = bundle.getString("link") ?: throw IllegalArgumentException("deeplink not found"),
        title = bundle.getString("title"),
        message = bundle.getString("message") ?: throw IllegalArgumentException("message not found"),
        dappUrl = bundle.getString("dapp_url") ?: throw IllegalArgumentException("dapp_url not found"),
        from = bundle.getString("from") ?: throw IllegalArgumentException("from not found"),
        dateUnix = System.currentTimeMillis() / 1000
    )

    constructor(json: JSONObject) : this(
        account = json.getString("account"),
        link = json.getString("link"),
        title = json.optString("title"),
        message = json.getString("message"),
        dappUrl = json.getString("dapp_url"),
        from = "",
        dateUnix = json.getLong("date_create")
    )

}