package com.tonapps.wallet.data.passcode

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference

/**
 * The screen the user is looking at, for prompts that need an Activity window (the passcode dialog,
 * the biometric prompt). Registered once on the Application; held weakly, so it never keeps a
 * closed screen alive.
 */
class ForegroundActivityTracker : Application.ActivityLifecycleCallbacks {

    private val resumed = MutableStateFlow<WeakReference<FragmentActivity>?>(null)

    /** The resumed screen, or the next one to resume within [timeoutMs]; null when none does. */
    suspend fun awaitResumed(timeoutMs: Long): FragmentActivity? {
        return withTimeoutOrNull(timeoutMs) {
            resumed
                .mapNotNull { ref -> ref?.get()?.takeUnless { it.isFinishing || it.isDestroyed } }
                .first()
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity is FragmentActivity) {
            resumed.value = WeakReference(activity)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        clear(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        clear(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    // Another screen may have resumed in between: only the one that leaves is dropped.
    private fun clear(activity: Activity) {
        if (resumed.value?.get() === activity) {
            resumed.value = null
        }
    }
}
