package com.tonapps.wallet.data.push.entities

import android.content.Intent
import android.net.Uri
import android.os.Bundle

class BridgeAppPushEntity(
    val name: String,
    val message: String,
    val hash: String,
    val from: String
) {

    val intent = Intent(Intent.ACTION_VIEW).apply {
        val builder = Uri.Builder()
        builder.scheme("tonkeeper")
        builder.authority("request")
        // builder.appendQueryParameter("link", link)
        // builder.appendQueryParameter("account", account)
        // builder.appendQueryParameter("url", dappUrl)
        data = builder.build()
    }

    constructor(bundle: Bundle) : this(
        name = bundle.getString("name") ?: throw IllegalArgumentException("name not found"),
        message = bundle.getString("message") ?: throw IllegalArgumentException("message not found"),
        hash = bundle.getString("hash") ?: throw IllegalArgumentException("hash not found"),
        from = bundle.getString("from") ?: throw IllegalArgumentException("from not found")
    )
}