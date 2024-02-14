package uikit.insets

import android.view.View
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.InsetsType
import uikit.extensions.getRootWindowInsetsCompat

abstract class InsetsAnimationCallback(
    private val view: View,
    @InsetsType private val typeMask: Int
): WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {

    private val isVisible: Boolean
        get() = view.isAttachedToWindow && view.isShown && view.windowVisibility == View.VISIBLE

    abstract fun onUpdateInsets(insets: WindowInsetsCompat, animation: WindowInsetsAnimationCompat)

    override fun onProgress(insets: WindowInsetsCompat, runningAnimations: MutableList<WindowInsetsAnimationCompat>): WindowInsetsCompat {
        val animation = findAnimation(runningAnimations) ?: return insets
        onUpdateInsets(insets, animation)
        return insets
    }

    private fun findAnimation(animations: MutableList<WindowInsetsAnimationCompat>): WindowInsetsAnimationCompat? {
        if (!isVisible) return null

        val animation = animations.find {
            it.typeMask and typeMask == typeMask
        }
        return animation ?: animations.firstOrNull()
    }

    override fun onEnd(animation: WindowInsetsAnimationCompat) {
        super.onEnd(animation)
        view.getRootWindowInsetsCompat()?.let {
            onUpdateInsets(it, animation)
        }
    }

    override fun onPrepare(animation: WindowInsetsAnimationCompat) {
        super.onPrepare(animation)
        view.getRootWindowInsetsCompat()?.let {
            onUpdateInsets(it, animation)
        }
    }

}