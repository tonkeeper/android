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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import uikit.R
import uikit.extensions.activity
import uikit.extensions.dp
import uikit.extensions.findFragments
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.range
import uikit.extensions.roundTop
import uikit.extensions.scale
import uikit.extensions.setView
import kotlin.math.abs

class BottomSheetLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle), ValueAnimator.AnimatorUpdateListener {

    private companion object {
        private const val parentScale = .92f
        private const val parentAlpha = .8f
        private val defaultTopMargin = 22.dp
        private val interpolator = PathInterpolator(.2f, 0f, 0f, 1f)
    }

    var doOnHide: (() -> Unit)? = null
    var doOnAnimationEnd: (() -> Unit)? = null
    var doOnDragging: (() -> Unit)? = null
    var fragment: Fragment? = null

    private val parentRadius = context.getDimensionPixelSize(R.dimen.cornerMedium)

    private val fragmentManager: FragmentManager?
        get() = (context.activity as? FragmentActivity)?.supportFragmentManager

    private val parentRootView: View? by lazy {
        context.activity?.findViewById(R.id.root_container)
    }

    private val sheetContainerView: View? by lazy {
        context.activity?.findViewById(R.id.sheet_container)
    }

    private val stackFragments: List<Fragment> by lazy {
        fragmentManager?.findFragments(R.id.sheet_container) ?: emptyList()
    }

    private val stackViews: List<View> by lazy {
        val stackViews = stackFragments.dropLast(1).map { it.view }
        (listOf(parentRootView) + stackViews).asReversed().filterNotNull()
    }

    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                releaseScreen()
            } else if (newState == BottomSheetBehavior.STATE_DRAGGING) {
                doOnDragging?.invoke()
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            if (slideOffset > 0f) {
                return
            }
            val progress = 1f - abs(slideOffset)
            onAnimationUpdateParent(progress)
        }
    }

    private val showAnimation = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 285
        interpolator = BottomSheetLayout.interpolator
        addUpdateListener(this@BottomSheetLayout)
        doOnStart { setLayerType(LAYER_TYPE_HARDWARE, null) }
        doOnEnd {
            setLayerType(LAYER_TYPE_NONE, null)
            doOnAnimationEnd?.invoke()
        }
    }

    private val coordinatorView: CoordinatorLayout
    private val contentView: FrameLayout

    val behavior: BottomSheetBehavior<FrameLayout>

    init {
        inflate(context, R.layout.view_bottom_sheet, this)
        coordinatorView = findViewById(R.id.sheet_coordinator)
        contentView = findViewById(R.id.sheet_content)

        behavior = BottomSheetBehavior.from(contentView)
        behavior.hideFriction = 0.4f
        behavior.addBottomSheetCallback(bottomSheetCallback)
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val compatInsets = WindowInsetsCompat.toWindowInsetsCompat(insets)
        val statusInsets = compatInsets.getInsets(WindowInsetsCompat.Type.statusBars())
        setPadding(0, statusInsets.top + defaultTopMargin, 0, 0)
        return super.onApplyWindowInsets(insets)
    }

    fun setContentView(view: View) {
        view.roundTop(context.getDimensionPixelSize(R.dimen.cornerMedium))
        contentView.setView(view)
    }

    fun startShowAnimation() {
        doOnLayout {
            behavior.peekHeight = contentView.measuredHeight
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            showAnimation.start()
        }
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
        onAnimationUpdateParent(value)
        val height = measuredHeight
        contentView.translationY = height * (1 - value)
    }

    private fun onAnimationUpdateParent(progress: Float) {
        if (stackViews.size > 1) {
            return
        }

        val lastView = stackViews.lastOrNull() ?: return
        lastView.roundTop(progress.range(0, parentRadius))
        lastView.scale = progress.range(1f, parentScale)
        lastView.alpha = progress.range(1f, parentAlpha)
    }

    private fun releaseScreen() {
        doOnHide?.invoke()
        onAnimationUpdateParent(0f)
    }

    override fun hasOverlappingRendering() = false
}