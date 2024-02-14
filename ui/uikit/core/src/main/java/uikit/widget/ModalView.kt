package uikit.widget

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.marginBottom
import androidx.core.view.updatePadding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import uikit.R
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.roundTop
import uikit.extensions.setPaddingBottom
import kotlin.math.abs

class ModalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle), ValueAnimator.AnimatorUpdateListener {

    private companion object {
        private val interpolator = PathInterpolator(.2f, 0f, 0f, 1f)
    }

    var doOnHide: (() -> Unit)? = null

    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                doOnHide?.invoke()
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            if (slideOffset > 0f) {
                return
            }
            val opacity = 1f - abs(slideOffset)
            bgView.alpha = opacity
        }
    }

    val behavior: BottomSheetBehavior<FrameLayout>
    private val bgView: View
    private val coordinatorView: CoordinatorLayout
    private val bottomSheetView: FrameLayout

    private val animation = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 220
        interpolator = ModalView.interpolator
        addUpdateListener(this@ModalView)
        doOnStart { setLayerType(LAYER_TYPE_HARDWARE, null) }
        doOnEnd { setLayerType(LAYER_TYPE_NONE, null) }
    }

    init {
        inflate(context, R.layout.view_modal, this)
        bgView = findViewById(R.id.bg)

        bottomSheetView = findViewById(R.id.modal_design_bottom_sheet)
        bottomSheetView.roundTop(context.getDimensionPixelSize(R.dimen.cornerMedium))

        coordinatorView = findViewById(R.id.modal_coordinator)

        behavior = BottomSheetBehavior.from(bottomSheetView)
        behavior.addBottomSheetCallback(bottomSheetCallback)
        behavior.state = BottomSheetBehavior.STATE_HIDDEN

        findViewById<View>(R.id.modal_touch_outside).setOnClickListener {
            hide(false)
        }
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val compatInsets = WindowInsetsCompat.toWindowInsetsCompat(insets)
        val statusInsets = compatInsets.getInsets(WindowInsetsCompat.Type.statusBars())
        val navigationInsets = compatInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
        bottomSheetView.layoutParams = (bottomSheetView.layoutParams as CoordinatorLayout.LayoutParams).apply {
            topMargin = statusInsets.top + context.getDimensionPixelSize(R.dimen.offsetMedium)
        }
        // containerView.updatePadding(top = statusInsets.top, bottom = navigationInsets.bottom)
        return super.onApplyWindowInsets(insets)
    }

    fun startShowAnimation() {
        doOnLayout {
            animation.start()
        }
    }

    fun setContentView(view: View) {
        bottomSheetView.removeAllViews()
        bottomSheetView.addView(view)
    }

    fun show() {
        if (behavior.state == BottomSheetBehavior.STATE_HIDDEN) {
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    fun hide(force: Boolean) {
        if (force) {
            behavior.isHideable = true
        }
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    override fun onAnimationUpdate(animation: ValueAnimator) {
        val value = animation.animatedValue as Float
        val height = measuredHeight
        bottomSheetView.translationY = height * (1 - value)
        bgView.alpha = value
    }

    fun fixPeekHeight() {
        bottomSheetView.requestLayout()
        bottomSheetView.post {
            behavior.peekHeight = bottomSheetView.measuredHeight
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun hasOverlappingRendering() = false
}