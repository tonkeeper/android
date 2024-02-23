package uikit.insets

import android.view.View
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import uikit.extensions.bottomBarsOffset
import uikit.extensions.getRootWindowInsetsCompat
import uikit.extensions.insetsBottomTypeMask

abstract class KeyboardAnimationCallback(view: View): InsetsAnimationCallback(view, insetsBottomTypeMask) {

    private val initOffset: Int by lazy { view.getRootWindowInsetsCompat()?.bottomBarsOffset ?: 0 }
    private var lastOffset = 0

    init {
        view.doOnLayout {
            keyboardOffsetChanged(initOffset, 0f)
        }
    }

    override fun onUpdateInsets(insets: WindowInsetsCompat, animation: WindowInsetsAnimationCompat) {
        val fraction = animation.interpolatedFraction
        val offset = insets.bottomBarsOffset
        keyboardOffsetChanged(offset, fraction)
    }

    abstract fun onKeyboardOffsetChanged(offset: Int, progress: Float)

    private fun keyboardOffsetChanged(offset: Int, fraction: Float) {
        if (0 >= offset) {
            return
        }

        if (lastOffset == offset) {
            if (offset == initOffset) {
                onKeyboardOffsetChanged(offset, 0f)
            }
            return
        }

        val hide = 0 > (offset - lastOffset)
        val progress = if (hide) 1 - fraction else fraction
        onKeyboardOffsetChanged(offset, progress)
        lastOffset = offset
    }
}