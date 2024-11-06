package com.tonapps.wallet.data.dapps.entities

import android.net.Uri
import androidx.core.net.toUri
import com.tonapps.extensions.getLongCompat
import org.json.JSONObject

data class AppPushEntity(
    val from: AppEntity,
    val body: Body
) {

    val timestamp: Long
        get() = body.dateCreate

    val iconUrl: String
        get() = from.iconUrl

    val title: String
        get() = body.title

    val message: String
        get() = body.message

    val url: Uri
        get() = body.dappUrl

    val deeplink: String
        get() = body.link

    data class Body(
        val dappUrl: Uri,
        val title: String,
        val message: String,
        val link: String,
        val account: String,
        val dateCreate: Long
    ) {

        constructor(json: JSONObject) : this(
            dappUrl = json.getString("dapp_url").toUri(),
            title = json.getString("title"),
            message = json.getString("message"),
            link = json.getString("link"),
            account = json.getString("account"),
            dateCreate = json.getLongCompat("date_create")
        )
    }
}