package uikit.insets

import android.view.View
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import uikit.extensions.bottomBarsOffset
import uikit.extensions.getRootWindowInsetsCompat
import uikit.extensions.insetsBottomTypeMask

abstract class KeyboardAnimationCallback(
    private val view: View,
    private val ignoreNavBar: Boolean,
): InsetsAnimationCallback(view, if (ignoreNavBar) WindowInsetsCompat.Type.ime() else insetsBottomTypeMask) {

    private val navigationOffset: Int by lazy {
        if (ignoreNavBar) {
            0
        } else {
            view.getRootWindowInsetsCompat()?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
        }
    }

    private val imeOffset: Int
        get() = view.getRootWindowInsetsCompat()?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0

    private val initOffset: Int by lazy {
        navigationOffset + imeOffset
    }

    private var lastOffset = 0
    private var isImeShown: Boolean = false
    private var isImeAnimating = false

    init {
        view.doOnLayout {
            isImeShown = imeOffset > 0
            keyboardOffsetChanged(initOffset, 0f)
        }
    }

    override fun onUpdateInsets(insets: WindowInsetsCompat, animation: WindowInsetsAnimationCompat) {
        val fraction = animation.interpolatedFraction
        val offset = if (ignoreNavBar) {
            insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        } else {
            insets.bottomBarsOffset
        }
        keyboardOffsetChanged(offset, fraction)
    }

    override fun onPrepare(animation: WindowInsetsAnimationCompat) {
        if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
            isImeAnimating = true
        }
        super.onPrepare(animation)
    }

    override fun onEnd(animation: WindowInsetsAnimationCompat) {
        super.onEnd(animation)
        if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
            isImeAnimating = false
        }
    }

    fun applyWindowInsets(insets: WindowInsetsCompat) {
        if (isImeAnimating) {
            return
        }
        val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val offset = if (ignoreNavBar) imeBottom else insets.bottomBarsOffset
        if (0 >= offset || offset == lastOffset) {
            return
        }
        val isShowing = imeBottom > 0
        lastOffset = offset
        onKeyboardOffsetChanged(offset, if (isShowing) 1f else 0f, isShowing)
    }

    abstract fun onKeyboardOffsetChanged(offset: Int, progress: Float, isShowing: Boolean)

    private fun keyboardOffsetChanged(offset: Int, fraction: Float) {
        if (0 >= offset) {
            return
        }

        if (lastOffset == offset) {
            if (offset == initOffset) {
                val hide = navigationOffset >= offset || 0 > (offset - lastOffset)
                onKeyboardOffsetChanged(offset, 0f, !hide)
            }
            return
        }

        val hide = navigationOffset >= offset || 0 > (offset - lastOffset)
        val progress = when {
            navigationOffset >= offset -> 0f
            hide -> 1 - fraction
            else -> fraction
        }
        onKeyboardOffsetChanged(offset, progress, !hide)
        lastOffset = offset
    }
}