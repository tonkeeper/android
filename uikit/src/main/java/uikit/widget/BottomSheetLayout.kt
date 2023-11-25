package uikit.widget

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.math.MathUtils
import androidx.core.view.doOnLayout
import androidx.customview.widget.ViewDragHelper
import uikit.R
import uikit.base.fragment.BaseFragment
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
        private val interpolator = AccelerateDecelerateInterpolator()
    }

    var fragment: BaseFragment? = null

    private val parentFragment: BaseFragment?
        get() = fragment?.parent as? BaseFragment

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
        fitsSystemWindows = true
        contentContainer = findViewById(R.id.content)

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
                        val parentView = parentFragment?.view ?: return
                        if (parentView.alpha == 1f) {
                            doOnCloseScreen?.invoke()
                        }
                    }
                }
            }
        }

        dragHelper = ViewDragHelper.create(contentContainer, 0.2f, drawCallback)
        dragHelper!!.setEdgeTrackingEnabled(ViewDragHelper.EDGE_BOTTOM)
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
        setLayerType(LAYER_TYPE_HARDWARE, null)
        doOnLayout {
            animation.doOnEnd { doOnEndShowingAnimation?.invoke() }
            animation.start()
        }
    }

    fun startHideAnimation(callback: () -> Unit) {
        animation.doOnEnd { callback() }
        animation.reverse()
    }

    override fun onAnimationUpdate(animation: ValueAnimator) {
        val value = animation.animatedValue as Float
        onAnimationUpdateParent(value)
        val height = measuredHeight
        contentContainer.translationY = height * (1 - value)
    }

    private fun onAnimationUpdateParent(progress: Float) {
        val parentView = parentFragment?.view ?: return
        val radius = context.getDimensionPixelSize(R.dimen.cornerMedium)
        parentView.roundTop(progress.range(0, radius))
        parentView.scale = progress.range(1f, parentScale)
        parentView.alpha = progress.range(1f, parentAlpha)
    }

    override fun hasOverlappingRendering() = false
}