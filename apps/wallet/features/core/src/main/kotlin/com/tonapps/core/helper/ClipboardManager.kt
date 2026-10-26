package com.tonapps.core.helper

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tonapps.uikit.color.accentGreenColor
import com.tonapps.uikit.color.backgroundContentTintColor
import com.tonapps.uikit.icon.UIKitIcon
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

        context.toast(
            message = message,
            loading = false,
            color = context.backgroundContentTintColor,
            iconRes = UIKitIcon.ic_checkmark_circle_16,
            iconTint = context.accentGreenColor,
        )
    }

    fun getText(): String? {
        val context = context ?: return null

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clipDescription = clipboard.primaryClipDescription

        return if (
            clipboard.hasPrimaryClip()
                || (clipDescription != null
                && clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN))
        ) {
            clipboard.primaryClip
                ?.getItemAt(0)
                ?.text
                ?.toString()
        } else {
            null
        }
    }
}

@Composable
fun rememberClipboardManager(): ClipboardManager {
    val context = LocalContext.current
    val activity = remember(context) { context.activity }
    val manager = remember(context) { ClipboardManager(activity) }
    return manager
}
