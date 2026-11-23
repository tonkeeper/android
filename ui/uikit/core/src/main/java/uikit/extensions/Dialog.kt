package uikit.extensions

import android.app.Dialog
import android.os.Build
import androidx.core.view.WindowCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

fun Dialog.matchHostNavigationBar() {
    val window = window ?: return
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }
    context.activity?.window?.let { hostWindow ->
        val hostController = WindowCompat.getInsetsController(hostWindow, hostWindow.decorView)
        controller.isAppearanceLightNavigationBars = hostController.isAppearanceLightNavigationBars
    }
}

fun <T : Dialog> T.safeShow(
    lifecycleOwner: LifecycleOwner,
) {
    val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            if (isShowing) {
                dismiss()
            }
        }
    }
    setOnDismissListener {
        lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
    }

    lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

    show()
}