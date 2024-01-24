package uikit.widget

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.math.MathUtils
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.customview.widget.ViewDragHelper
import uikit.R
import uikit.base.BaseFragment
import uikit.extensions.activity
import uikit.extensions.dp
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
) : FrameLayout(context, attrs, defStyle),
    ValueAnimator.AnimatorUpdateListener {

    private companion object {
        private val parentScale = .92f
        private val parentAlpha = .8f
        private val defaultTopMargin = 22.dp
        private val interpolator = AccelerateDecelerateInterpolator()
    }

    var fragment: BaseFragment? = null

    private val parentRootView: View? by lazy {
        val v = context.activity?.findViewById<View>(R.id.root_container)
        v
    }

    private val contentContainer: FrameLayout
    private var dragHelper: ViewDragHelper? = null
    private val drawCallback: ViewDragHelper.Callback

    private val animation = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 220
        interpolator = BottomSheetLayout.interpolator
        addUpdateListener(this@BottomSheetLayout)
        doOnStart { setLayerType(LAYER_TYPE_HARDWARE, null) }
        doOnEnd { setLayerType(LAYER_TYPE_NONE, null) }
    }

    var doOnCloseScreen: (() -> Unit)? = null
    var doOnEndShowingAnimation: (() -> Unit)? = null

    init {
        inflate(context, R.layout.view_bottom_sheet, this)
        contentContainer = findViewById(R.id.sheet_content)

        drawCallback = object : ViewDragHelper.Callback() {
            override fun tryCaptureView(child: View, pointerId: Int): Boolean {
                return true
            }

            override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
                return MathUtils.clamp(top, 0, measuredHeight)
            }

            override fun getViewVerticalDragRange(child: View): Int {
                return measuredHeight
            }

            override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
                super.onViewPositionChanged(changedView, left, top, dx, dy)
                val percent = 1f - (top.toFloat() / measuredHeight)
                onAnimationUpdateParent(percent)
            }

            override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
                super.onViewReleased(releasedChild, xvel, yvel)
                val top = releasedChild.top
                var settleTop = 0
                val topThreshold = (measuredHeight * 0.25f).toInt()
                val isHorizontalSwiping = abs(xvel) > 5f
                if (yvel > 0) {
                    if (abs(yvel) > 5f && !isHorizontalSwiping) {
                        settleTop = measuredHeight
                    } else if (top > topThreshold) {
                        settleTop = measuredHeight
                    }
                } else if (yvel < 0) {
                    if (abs(yvel) > 5f && !isHorizontalSwiping) {
                        settleTop = -measuredHeight
                    } else if (top < -topThreshold) {
                        settleTop = -measuredHeight
                    }
                } else {
                    if (top > topThreshold) {
                        settleTop = measuredHeight
                    } else if (top < -topThreshold) {
                        settleTop = -measuredHeight
                    }
                }
                if (settleTop >= 0) {
                    dragHelper?.settleCapturedViewAt(releasedChild.left, settleTop)
                    postInvalidateOnAnimation()
                }
            }

            override fun onViewDragStateChanged(state: Int) {
                super.onViewDragStateChanged(state)
                when(state) {
                    ViewDragHelper.STATE_IDLE -> {
                        val parentView = parentRootView ?: return
                        if (parentView.alpha == 1f) {
                            releaseScreen()
                        }
                    }
                }
            }
        }

        dragHelper = ViewDragHelper.create(contentContainer, 0.2f, drawCallback)
        dragHelper!!.setEdgeTrackingEnabled(ViewDragHelper.EDGE_BOTTOM)
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val compatInsets = WindowInsetsCompat.toWindowInsetsCompat(insets)
        val statusInsets = compatInsets.getInsets(WindowInsetsCompat.Type.statusBars())
        setPadding(0, statusInsets.top + defaultTopMargin, 0, 0)
        return super.onApplyWindowInsets(insets)
    }

    fun setContentView(content: View) {
        content.roundTop(context.getDimensionPixelSize(R.dimen.cornerMedium))
        contentContainer.setView(content)
    }

    override fun computeScroll() {
        super.computeScroll()
        if (dragHelper!!.continueSettling(true)) {
            postInvalidateOnAnimation()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return try {
            dragHelper!!.processTouchEvent(event)
            true
        } catch (e: Throwable) {
            false
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return try {
            dragHelper!!.shouldInterceptTouchEvent(ev)
        } catch (e: Throwable) {
            false
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        dragHelper!!.minVelocity = measuredHeight / 2f
    }

    fun startShowAnimation() {
        doOnLayout {
            animation.doOnEnd { doOnEndShowingAnimation?.invoke() }
            animation.start()
        }
    }

    fun startHideAnimation() {
        if (animation.isRunning) {
            return
        }

        animation.doOnEnd { releaseScreen() }
        animation.reverse()
    }

    private fun releaseScreen() {
        doOnCloseScreen?.invoke()
        onAnimationUpdateParent(0f)
    }

    override fun onAnimationUpdate(animation: ValueAnimator) {
        val value = animation.animatedValue as Float
        onAnimationUpdateParent(value)
        val height = measuredHeight
        contentContainer.translationY = height * (1 - value)
    }

    private fun onAnimationUpdateParent(progress: Float) {
        val parentView = parentRootView ?: return
        val radius = context.getDimensionPixelSize(R.dimen.cornerMedium)
        parentView.roundTop(progress.range(0, radius))
        parentView.scale = progress.range(1f, parentScale)
        parentView.alpha = progress.range(1f, parentAlpha)
    }

    override fun hasOverlappingRendering() = false
}