package com.tonapps.core.helper

import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tonapps.core.extensions.rememberActivity
import com.tonapps.wallet.localization.Localization

class ShareManager(
    private val activity: Activity,
) {
    private val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
    }

    fun share(text: String) {
        intent.putExtra(Intent.EXTRA_TEXT, text)
        activity.startActivity(Intent.createChooser(intent, activity.getString(Localization.share)))
    }
}

@Composable
fun rememberShareManager(): ShareManager {
    val activity = rememberActivity()
    val manager = remember { ShareManager(activity) }
    return manager
}
