package uikit.widget

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.math.MathUtils.clamp
import androidx.core.view.doOnLayout
import androidx.customview.widget.ViewDragHelper
import uikit.R
import uikit.base.BaseFragment
import uikit.extensions.dp
import uikit.extensions.setView
import kotlin.math.abs

class SwipeBackLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle),
    ValueAnimator.AnimatorUpdateListener {

    private companion object {
        private val animationOffsetX = 42f.dp
        private val interpolator = AccelerateDecelerateInterpolator()
    }

    private val animation = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 225
        interpolator = SwipeBackLayout.interpolator
        addUpdateListener(this@SwipeBackLayout)
        doOnStart { setLayerType(LAYER_TYPE_HARDWARE, null) }
        doOnEnd { setLayerType(LAYER_TYPE_NONE, null) }
    }

    var fragment: BaseFragment? = null

    private val parentFragment: BaseFragment? by lazy {
        fragment?.parent as? BaseFragment
    }

    private val bgView: View
    private val shadowView: View
    private val contentContainer: FrameLayout
    private var dragHelper: ViewDragHelper? = null
    private val drawCallback: ViewDragHelper.Callback

    var doOnCloseScreen: (() -> Unit)? = null
    var doOnEndShowingAnimation: (() -> Unit)? = null

    init {
        inflate(context, R.layout.view_swipe_back, this)

        bgView = findViewById(R.id.bg)
        shadowView = findViewById(R.id.shadow)
        contentContainer = findViewById(R.id.swipe_content)

        drawCallback = object : ViewDragHelper.Callback() {
            override fun tryCaptureView(child: View, pointerId: Int): Boolean {
                return true
            }

            override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
                return clamp(left, 0, measuredWidth)
            }

            override fun getViewHorizontalDragRange(child: View): Int {
                return measuredWidth
            }

            override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
                super.onViewPositionChanged(changedView, left, top, dx, dy)
                val percent = 1f - (left.toFloat() / measuredWidth)
                bgView.alpha = percent
                shadowView.x = left - shadowView.width.toFloat()
                onAnimationUpdateParent(percent)
            }

            override fun onViewDragStateChanged(state: Int) {
                super.onViewDragStateChanged(state)
                when(state) {
                    ViewDragHelper.STATE_IDLE -> {
                        if (bgView.alpha == 0.0f) {
                            doOnCloseScreen?.invoke()
                        }
                    }
                }
            }

            override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
                super.onViewReleased(releasedChild, xvel, yvel)
                val left = releasedChild.left
                var settleLeft = 0
                val leftThreshold = (measuredWidth * 0.25f).toInt()
                val isVerticalSwiping = abs(yvel) > 5f
                if (xvel > 0) {
                    if (abs(xvel) > 5f && !isVerticalSwiping) {
                        settleLeft = measuredWidth
                    } else if (left > leftThreshold) {
                        settleLeft = measuredWidth
                    }
                } else if (xvel < 0) {
                    if (abs(xvel) > 5f && !isVerticalSwiping) {
                        settleLeft = -measuredWidth
                    } else if (left < -leftThreshold) {
                        settleLeft = -measuredWidth
                    }
                } else {
                    if (left > leftThreshold) {
                        settleLeft = measuredWidth
                    } else if (left < -leftThreshold) {
                        settleLeft = -measuredWidth
                    }
                }
                if (settleLeft >= 0) {
                    dragHelper?.settleCapturedViewAt(settleLeft, releasedChild.top)
                    postInvalidateOnAnimation()
                }
            }
        }

        dragHelper = ViewDragHelper.create(contentContainer, 0.2f, drawCallback)
        dragHelper!!.setEdgeTrackingEnabled(ViewDragHelper.EDGE_LEFT)
    }

    fun setContentView(content: View) {
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
        dragHelper!!.minVelocity = measuredWidth / 2f
    }

    fun startShowAnimation() {
        alpha = 0f
        contentContainer.translationX = animationOffsetX
        shadowView.translationX = animationOffsetX
        setLayerType(LAYER_TYPE_HARDWARE, null)
        doOnLayout {
            animation.doOnEnd { doOnEndShowingAnimation?.invoke() }
            animation.start()
        }
    }

    fun startHideAnimation() {
        if (animation.isRunning) {
            return
        }

        animation.doOnEnd { doOnCloseScreen?.invoke() }
        animation.reverse()
    }

    override fun onAnimationUpdate(animation: ValueAnimator) {
        val progress = animation.animatedValue as Float
        onAnimationUpdateParent(progress)
        alpha = progress
        contentContainer.translationX = animationOffsetX * (1f - progress)
        shadowView.translationX = animationOffsetX * (1f - progress)
    }

    private fun onAnimationUpdateParent(progress: Float) {
        val parentView = parentFragment?.view ?: return

        parentView.translationX = -animationOffsetX * progress
    }

    override fun hasOverlappingRendering() = false
}