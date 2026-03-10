package com.tonapps.tonkeeper.extensions

import android.app.Dialog
import android.view.View
import androidx.lifecycle.findViewTreeLifecycleOwner
import uikit.extensions.safeShow

fun <T : Dialog> T.safeShow(view: View) {
    val lifecycleOwner = view.findViewTreeLifecycleOwner()
    if (lifecycleOwner == null) {
        show()
    } else {
        safeShow(lifecycleOwner)
    }
}