package uikit.widget

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.math.MathUtils.clamp
import androidx.core.view.children
import androidx.core.view.doOnLayout
import androidx.customview.widget.ViewDragHelper
import uikit.R
import uikit.extensions.hasSwipeBackExclusionAt
import uikit.extensions.dp
import uikit.extensions.setView
import kotlin.math.abs

class SwipeBackLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle),
    ValueAnimator.AnimatorUpdateListener {

    private var edgeTouch = false
    private var isHiding = false
    private val layoutLocation = IntArray(2)
    private val childLocation = IntArray(2)

    private val animation = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 225
        interpolator = SwipeBackLayout.interpolator
        addUpdateListener(this@SwipeBackLayout)
        doOnStart { setLayerType(LAYER_TYPE_HARDWARE, null) }
        doOnEnd { setLayerType(LAYER_TYPE_NONE, null) }
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
            }

            override fun onViewDragStateChanged(state: Int) {
                super.onViewDragStateChanged(state)
                when(state) {
                    ViewDragHelper.STATE_IDLE -> {
                        val content: View? = contentContainer.getChildAt(0)
                        if (bgView.alpha == 0.0f || (content != null && content.left >= measuredWidth)) {
                            doOnCloseScreen?.invoke()
                        } else if (content != null && content.left != 0) {
                            // Self-heal: an interrupted settle (touch race, swallowed drag
                            // exception) can leave the content parked mid-way; snap it home.
                            post {
                                if (dragHelper?.viewDragState == ViewDragHelper.STATE_IDLE && content.left != 0) {
                                    dragHelper?.smoothSlideViewTo(content, 0, content.top)
                                    postInvalidateOnAnimation()
                                }
                            }
                        }
                    }
                }
            }

            override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
                super.onViewReleased(releasedChild, xvel, yvel)
                val isHorizontalFling = xvel > 5f && abs(yvel) <= 5f
                val isPastThreshold = xvel >= 0f && releasedChild.left > measuredWidth * 0.25f
                val settleLeft = if (isHorizontalFling || isPastThreshold) {
                    measuredWidth
                } else {
                    0
                }
                dragHelper?.settleCapturedViewAt(settleLeft, releasedChild.top)
                postInvalidateOnAnimation()
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
            // A swallowed ViewDragHelper error on the final event would leave the drag
            // hanging mid-gesture with no settle; abort() forces STATE_IDLE, where
            // onViewDragStateChanged snaps the content back into place.
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                try {
                    dragHelper?.abort()
                } catch (_: Throwable) {
                }
            }
            false
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            edgeTouch = ev.x <= edgeSwipeWidth && !isExcludedFromSwipeBack(ev.x.toInt(), ev.y.toInt())
        }
        return try {
            dragHelper!!.shouldInterceptTouchEvent(ev)
        } catch (e: Throwable) {
            false
        }
    }

    // Children that grab horizontal gestures (chart scrubbing, web views) disallow parent
    // interception and silently kill the swipe-back. A drag that starts at the left edge is a
    // navigation gesture, so the edge keeps interception rights no matter what the child wants,
    // unless the child excluded that spot from swipe-back.
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept && edgeTouch) {
            return
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    private fun isExcludedFromSwipeBack(x: Int, y: Int): Boolean {
        getLocationInWindow(layoutLocation)
        return isExcludedFromSwipeBack(contentContainer, x + layoutLocation[0], y + layoutLocation[1])
    }

    private fun isExcludedFromSwipeBack(view: View, windowX: Int, windowY: Int): Boolean {
        if (view.visibility != View.VISIBLE) {
            return false
        }
        view.getLocationInWindow(childLocation)
        if (view.hasSwipeBackExclusionAt(windowX - childLocation[0], windowY - childLocation[1])) {
            return true
        }
        return view is ViewGroup && view.children.any { isExcludedFromSwipeBack(it, windowX, windowY) }
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
        if (isHiding) {
            return
        }
        isHiding = true

        animation.doOnEnd { doOnCloseScreen?.invoke() }
        animation.reverse()
    }

    override fun onAnimationUpdate(animation: ValueAnimator) {
        val progress = animation.animatedValue as Float
        alpha = progress
        contentContainer.translationX = animationOffsetX * (1f - progress)
        shadowView.translationX = animationOffsetX * (1f - progress)
    }

    override fun hasOverlappingRendering() = false

    private companion object {
        private val animationOffsetX = 60f.dp
        private val interpolator = PathInterpolator(.42f, 0f, .58f, 1f)
        private val edgeSwipeWidth = 32f.dp
    }
}