package com.tonapps.wallet.data.push.entities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.google.firebase.messaging.Constants

class WalletPushEntity(
    val firebaseMessageId: String,
    val account: String,
    val deeplink: String,
    val notificationBody: String?,
    val transactionHash: String?
) {

    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse(deeplink)
    }

    constructor(bundle: Bundle) : this(
        firebaseMessageId = bundle.getMessageId() ?: throw IllegalArgumentException("MSGID not found"),
        account = bundle.getString("account") ?: throw IllegalArgumentException("account not found"),
        deeplink = bundle.getString("deeplink") ?: throw IllegalArgumentException("deeplink not found"),
        notificationBody = bundle.getNotificationBody()?.trim(),
        transactionHash = bundle.getString("hash")
    )

    private companion object {

        private fun Bundle.getMessageId(): String? {
            return getString(Constants.MessagePayloadKeys.MSGID) ?: getString(Constants.MessagePayloadKeys.MSGID_SERVER)
        }

        private fun Bundle.getNotificationBody(): String? {
            return getString(Constants.MessageNotificationKeys.BODY) ?: getString("gcm.notification.body")
        }

    }

}