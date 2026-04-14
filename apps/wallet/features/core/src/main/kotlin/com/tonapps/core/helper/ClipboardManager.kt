package com.tonapps.core.helper

import android.content.ClipData
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tonapps.uikit.color.backgroundContentTintColor
import com.tonapps.wallet.localization.Localization
import uikit.extensions.activity
import uikit.navigation.NavigationActivity

class ClipboardManager(
    private val context: NavigationActivity?,
) {

    fun copy(text: String, message: String? = null) {
        val context = context ?: return
        val message = message ?: context.getString(Localization.copied)

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = ClipData.newPlainText("", text)
        clipboard.setPrimaryClip(clip)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            context.toast(message, false, context.backgroundContentTintColor)
        }
    }

    fun getText(): String? {
        val context = context ?: return null
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        return clipboard.text.toString()
    }
}

@Composable
fun rememberClipboardManager(): ClipboardManager {
    val context = LocalContext.current
    val activity = remember(context) { context.activity }
    val manager = remember(context) { ClipboardManager(activity) }
    return manager
}
