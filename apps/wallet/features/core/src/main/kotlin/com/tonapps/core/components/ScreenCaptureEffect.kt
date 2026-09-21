package com.tonapps.core.components

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import uikit.extensions.atLeastApi34

@Composable
fun ScreenCaptureEffect(onCaptured: () -> Unit) {
    if (!atLeastApi34) {
        return
    }
    val activity = LocalActivity.current ?: return
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    LifecycleResumeEffect(key1 = activity) {
        val callback = Activity.ScreenCaptureCallback { currentOnCaptured() }
        activity.registerScreenCaptureCallback(ContextCompat.getMainExecutor(activity), callback)
        onPauseOrDispose {
            activity.unregisterScreenCaptureCallback(callback)
        }
    }
}
