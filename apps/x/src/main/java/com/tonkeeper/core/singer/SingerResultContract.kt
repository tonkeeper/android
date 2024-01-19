package com.tonkeeper.core.singer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract

class SingerResultContract : ActivityResultContract<String, String?>() {

    override fun createIntent(context: Context, input: String): Intent {
        val uri = SignerApp.createSignUri(input)
        return Intent(Intent.ACTION_SEND, uri)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): String? {
        if (resultCode == Activity.RESULT_OK) {
            var boc = intent?.getStringExtra("boc")
            if (boc == null) {
                boc = intent?.data?.getQueryParameter("boc")
            }
            return boc
        }
        return null
    }
}