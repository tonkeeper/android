package com.tonapps.signer

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.tonapps.signer.extensions.base64
import org.ton.api.pub.PublicKeyEd25519

object TonkeeperApp {

    private const val STORE_LINK = "https://play.google.com/store/apps/details?id=com.tonapps.tonkeeperx"

    private fun uriBuilder(): Uri.Builder {
        val builder = Uri.Builder()
        builder.scheme("tonkeeper")
        builder.authority("signer")
        return builder
    }

    fun buildExportUri(publicKey: PublicKeyEd25519, name: String): Uri {
        val builder = uriBuilder()
        builder.appendQueryParameter("pk", publicKey.base64())
        builder.appendQueryParameter("name", name)
        return builder.build()
    }

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
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        open(context, intent)
    }

    private fun open(context: Context, intent: Intent) {
        context.startActivity(intent)
    }
}