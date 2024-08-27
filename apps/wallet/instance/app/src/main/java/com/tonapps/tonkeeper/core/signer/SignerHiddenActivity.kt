package com.tonapps.tonkeeper.core.signer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import uikit.base.BaseHiddenActivity

class SignerHiddenActivity: BaseHiddenActivity() {

    companion object {
        private const val REQUEST_CODE = 100

        const val ARG_RESULT_RECEIVER = "result_receiver"
        const val ARG_PUBLIC_KEY = "public_key"
        const val ARG_BODY = "body"
        const val ARG_SIGN = "sign"

        private fun parseSign(intent: Intent): String? {
            val sign = intent.getStringExtra(ARG_SIGN) ?: intent.data?.getQueryParameter(ARG_SIGN)
            return sign?.ifBlank { null }
        }
    }

    private var resultReceiver: ResultReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resultReceiver = intent.getParcelableExtra(ARG_RESULT_RECEIVER)
        if (resultReceiver == null) {
            finish()
            return
        }

        val publicKey = intent.getStringExtra(ARG_PUBLIC_KEY)
        val body = intent.getStringExtra(ARG_BODY)

        if (publicKey == null || body == null) {
            finish()
            return
        }

        val requestUri = "tonsign://v1/?network=ton&pk=$publicKey&body=$body"
        val requestIntent = Intent(Intent.ACTION_SEND, Uri.parse(requestUri))
        startActivityForResult(requestIntent, REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CODE) {
            return
        }

        val sign = if (resultCode == RESULT_OK) data?.let { parseSign(it) } else null
        val bundle = Bundle()
        bundle.putString(ARG_SIGN, sign)
        resultReceiver?.send(resultCode, bundle)
        finish()
    }

}