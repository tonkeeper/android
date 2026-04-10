package com.tonapps.core.extensions

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberActivity(): Activity {
    val context = LocalContext.current
    val activity = remember(context) {
        var ptr = context

        while (true) {
            when (ptr) {
                is Activity -> return@remember ptr
                is ContextWrapper -> ptr = ptr.baseContext
                else -> break
            }
        }

        error("No activity found")
    }

    return activity
}
