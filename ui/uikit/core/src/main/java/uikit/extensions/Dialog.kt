package uikit.extensions

import android.app.Dialog
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

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