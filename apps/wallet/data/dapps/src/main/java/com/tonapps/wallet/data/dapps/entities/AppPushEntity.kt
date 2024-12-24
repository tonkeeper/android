package com.tonapps.wallet.data.dapps.entities

import android.net.Uri
import android.os.Parcelable
import androidx.core.net.toUri
import com.tonapps.extensions.getLongCompat
import com.tonapps.extensions.toUriOrNull
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class AppPushEntity(
    val from: AppEntity,
    val body: Body
): Parcelable {

    @IgnoredOnParcel
    val timestamp: Long
        get() = body.dateCreate

    @IgnoredOnParcel
    val iconUrl: String
        get() = from.iconUrl

    @IgnoredOnParcel
    val title: String
        get() = body.title

    @IgnoredOnParcel
    val message: String
        get() = body.message

    @IgnoredOnParcel
    val url: Uri
        get() = body.dappUrl

    @IgnoredOnParcel
    val deeplink: String
        get() = body.link

    @Parcelize
    data class Body(
        val dappUrl: Uri,
        val title: String,
        val message: String,
        val link: String,
        val account: String,
        val dateCreate: Long
    ): Parcelable {

        constructor(json: JSONObject) : this(
            dappUrl = json.getString("dapp_url").toUriOrNull() ?: Uri.EMPTY,
            title = json.getString("title"),
            message = json.getString("message"),
            link = json.getString("link"),
            account = json.getString("account"),
            dateCreate = json.getLongCompat("date_create")
        )

        constructor(map: Map<String, String>) : this(
            dappUrl = map["dapp_url"]?.toUriOrNull() ?: Uri.EMPTY,
            title = map["title"] ?: "unknown",
            message = map["message"] ?: "",
            link = map["link"] ?: "",
            account = map["account"] ?: "",
            dateCreate = map["date_create"]?.toLongOrNull() ?: 0
        )
    }
}