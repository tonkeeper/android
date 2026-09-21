package uikit.widget

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.setPadding
import com.tonapps.uikit.color.backgroundContentTintColor
import uikit.R
import uikit.extensions.dp
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.hapticConfirm
import kotlin.math.abs
import kotlin.math.sign

class ToastView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RowLayout(context, attrs, defStyle), ValueAnimator.AnimatorUpdateListener,
    Animator.AnimatorListener {

    private data class Data(
        val loading: Boolean,
        val text: CharSequence,
        val color: Int,
        val duration: Long
    )

    private var statusBarHeight: Int = 0
    private var currentData: Data? = null
        set(value) {
            field = value
            runData()
        }

    private val dataQueue = mutableListOf<Data>()
    private val loaderView: View
    private val textView: AppCompatTextView

    private val animator: ValueAnimator by lazy {
        val startY = -height.toFloat()
        val endY = statusBarHeight + 24f.dp

        val valueAnimator = ValueAnimator.ofFloat(startY, endY)
        valueAnimator.duration = 160L
        valueAnimator.addListener(this@ToastView)
        valueAnimator.addUpdateListener(this@ToastView)
        valueAnimator
    }

    private val horizontalOffset = 24.dp
    private val verticalOffset = context.getDimensionPixelSize(R.dimen.offsetMedium)

    private val hideRunnable = Runnable { hide() }
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minDismissVelocity = MIN_DISMISS_VELOCITY_DPS.toFloat().dp
    private var velocityTracker: VelocityTracker? = null
    private var swipeStartX = 0f
    private var swipeDragging = false
    private var hideAtUptime = 0L

    init {
        inflate(context, R.layout.view_toast, this)
        setBackgroundResource(R.drawable.bg_content_tint_24)
        setPadding(horizontalOffset, verticalOffset, horizontalOffset, verticalOffset)
        visibility = View.GONE
        loaderView = findViewById(R.id.toast_loader)
        textView = findViewById(R.id.toast_text)
    }

    fun show(
        text: CharSequence,
        loading: Boolean,
        color: Int = context.backgroundContentTintColor,
        duration: Long = DURATION_DEFAULT
    ) {
        val data = createData(loading, text, color, duration)
        run(data)
    }

    private fun run(data: Data) {
        val cancelCurrent = currentData != null && currentData?.text == data.text && currentData?.color == data.color
        postOnAnimation {
            if (cancelCurrent) {
                hide()
            } else if (currentData == null) {
                currentData = data
            } else {
                dataQueue.add(data)
            }
        }
    }

    private fun createData(loading: Boolean, text: CharSequence, color: Int, duration: Long): Data {
        return Data(
            loading = loading,
            text = text,
            color = if (color == Color.TRANSPARENT) {
                context.backgroundContentTintColor
            } else {
                color
            },
            duration = duration
        )
    }

    private fun nextQueue() {
        currentData = dataQueue.removeFirstOrNull()
    }

    private fun runData() {
        val data = currentData ?: return
        hapticConfirm()
        textView.text = data.text
        background.setTint(data.color)
        removeCallbacks(hideRunnable)
        animate().cancel()
        translationX = 0f
        alpha = 1f
        visibility = View.VISIBLE

        doOnLayout {
            if (data !== currentData) {
                return@doOnLayout
            }
            if (data.loading) {
                showLoading()
            } else if (loaderView.visibility == View.VISIBLE) {
                hide()
            } else {
                showDefault(data.duration)
            }
        }
    }

    private fun showLoading() {
        loaderView.visibility = View.VISIBLE
        show()
    }

    private fun showDefault(duration: Long) {
        loaderView.visibility = View.GONE
        show()
        scheduleHide(duration)
    }

    private fun scheduleHide(delay: Long) {
        hideAtUptime = SystemClock.uptimeMillis() + delay
        postDelayed(hideRunnable, delay)
    }

    private fun resumeHide(minDelay: Long = 0L) {
        postDelayed(hideRunnable, (hideAtUptime - SystemClock.uptimeMillis()).coerceAtLeast(minDelay))
    }

    private fun show() {
        animator.start()
    }

    private fun hide() {
        removeCallbacks(hideRunnable)
        if (visibility != View.VISIBLE) {
            return
        }
        animator.reverse()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val data = currentData
        if (data == null || data.loading) {
            recycleVelocityTracker()
            swipeDragging = false
            return super.onTouchEvent(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(hideRunnable)
                animate().cancel()
                recycleVelocityTracker()
                velocityTracker = VelocityTracker.obtain()
                trackVelocity(event)
                swipeStartX = event.rawX - translationX
                swipeDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                trackVelocity(event)
                val dx = event.rawX - swipeStartX
                if (!swipeDragging && abs(dx) > touchSlop) {
                    swipeDragging = true
                }
                if (swipeDragging) {
                    translationX = dx
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                var velocityX = 0f
                velocityTracker?.let { tracker ->
                    tracker.computeCurrentVelocity(1000)
                    velocityX = tracker.xVelocity
                }
                recycleVelocityTracker()
                if (event.actionMasked == MotionEvent.ACTION_UP && !swipeDragging) {
                    performClick()
                }
                finishSwipe(velocityX, data)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun trackVelocity(event: MotionEvent) {
        val tracker = velocityTracker ?: return
        val rawEvent = MotionEvent.obtain(event)
        rawEvent.setLocation(event.rawX, event.rawY)
        tracker.addMovement(rawEvent)
        rawEvent.recycle()
    }

    private fun finishSwipe(velocityX: Float, data: Data) {
        if (visibility != View.VISIBLE) {
            swipeDragging = false
            return
        }
        if (!swipeDragging && translationX == 0f) {
            resumeHide()
            return
        }
        swipeDragging = false
        val dismiss = abs(translationX) > width / 3f ||
            (abs(velocityX) > minDismissVelocity && sign(velocityX) == sign(translationX))
        if (dismiss) {
            val direction = sign(translationX).takeIf { it != 0f } ?: sign(velocityX).takeIf { it != 0f } ?: 1f
            val distance = ((parent as? View)?.width ?: width).toFloat()
            animate()
                .translationX(direction * distance)
                .alpha(0f)
                .setDuration(SWIPE_ANIMATION_DURATION)
                .withEndAction { dismissBySwipe(data) }
                .start()
        } else {
            animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(SWIPE_ANIMATION_DURATION)
                .start()
            resumeHide(minDelay = SWIPE_ANIMATION_DURATION)
        }
    }

    private fun dismissBySwipe(data: Data) {
        if (data !== currentData) {
            return
        }
        animator.cancel()
        if (data !== currentData) {
            return
        }
        removeCallbacks(hideRunnable)
        loaderView.visibility = View.GONE
        visibility = View.GONE
        translationX = 0f
        translationY = -height.toFloat()
        alpha = 1f
        nextQueue()
    }

    override fun onAnimationUpdate(animation: ValueAnimator) {
        translationY = animation.animatedValue as Float
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val compat = WindowInsetsCompat.toWindowInsetsCompat(insets)
        statusBarHeight = compat.getInsets(WindowInsetsCompat.Type.statusBars()).top
        return super.onApplyWindowInsets(insets)
    }

    override fun onAnimationStart(animation: Animator) {
        visibility = View.VISIBLE
    }

    override fun onAnimationEnd(animation: Animator) {
        if (translationY <= -height.toFloat() + 1) {
            loaderView.visibility = View.GONE
            visibility = View.GONE
            nextQueue()
        }
    }

    override fun onAnimationCancel(animation: Animator) {
    }

    override fun onAnimationRepeat(animation: Animator) {
    }

    companion object {
        const val DURATION_DEFAULT = 2000L
        const val DURATION_LONG = 3000L

        private const val SWIPE_ANIMATION_DURATION = 160L
        private const val MIN_DISMISS_VELOCITY_DPS = 400
    }
}