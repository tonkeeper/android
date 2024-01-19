package com.tonapps.singer.core

import android.content.Context
import android.content.Intent
import android.net.Uri

object TonkeeperApp {

    private const val STORE_LINK = "https://play.google.com/store/apps/details?id=com.tonapps.tonkeeperx"

    private const val deepLinkScheme = "tonkeeper"
    private const val deepLinkAuthority = "signer"

    fun openOrInstall(context: Context, uri: Uri) {
        try {
            openUri(context, uri)
        } catch (e: Throwable) {
            openStore(context)
        }
    }

    private fun openUri(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        open(context, intent)
    }

    private fun openStore(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(STORE_LINK))
        open(context, intent)
    }

    private fun open(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}