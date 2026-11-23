package uikit.widget

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.animation.PathInterpolator
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.animation.doOnEnd
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import com.tonapps.uikit.color.backgroundContentTintColor
import uikit.R
import uikit.extensions.dp
import uikit.extensions.hapticConfirm
import uikit.extensions.hapticReject
import uikit.extensions.hapticSoftTap
import kotlin.math.abs
import kotlin.math.sign

class ToastView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RowLayout(context, attrs, defStyle) {

    private data class Data(
        val loading: Boolean,
        val text: CharSequence,
        val color: Int,
        val duration: Long,
        @DrawableRes val iconRes: Int,
        @ColorInt val iconTint: Int,
        val feedback: ToastFeedback,
    )

    private var statusBarHeight: Int = 0
    private var currentData: Data? = null
        set(value) {
            field = value
            runData()
        }

    private val dataQueue = mutableListOf<Data>()
    private val loaderView: View
    private val iconView: AppCompatImageView
    private val textView: AppCompatTextView

    private var shownFraction = 0f
    private var fractionTarget = 0f
    private var fractionAnimator: ValueAnimator? = null

    private val horizontalOffset = 24.dp
    private val horizontalOffsetWithIcon = 16.dp
    private val verticalOffset = 14.dp

    private val hideRunnable = Runnable { hide() }
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minDismissVelocity = MIN_DISMISS_VELOCITY_DPS.toFloat().dp
    private var velocityTracker: VelocityTracker? = null
    private var swipeStartX = 0f
    private var swipeDragging = false
    private var gestureData: Data? = null
    private var hideAtUptime = 0L

    // The current toast is set but waits for layout to start appearing.
    private var showPending = false

    init {
        inflate(context, R.layout.view_toast, this)
        setBackgroundResource(R.drawable.bg_content_tint_24)
        setPaddingRelative(horizontalOffset, verticalOffset, horizontalOffset, verticalOffset)
        minimumHeight = MIN_HEIGHT_DP.dp
        elevation = ELEVATION_DP.dp
        visibility = View.GONE
        loaderView = findViewById(R.id.toast_loader)
        iconView = findViewById(R.id.toast_icon)
        textView = findViewById(R.id.toast_text)
    }

    fun show(
        text: CharSequence,
        loading: Boolean,
        color: Int = context.backgroundContentTintColor,
        duration: Long = DURATION_DEFAULT,
        @DrawableRes iconRes: Int = 0,
        @ColorInt iconTint: Int = Color.TRANSPARENT,
        feedback: ToastFeedback = ToastFeedback.Neutral,
    ) {
        val data = createData(loading, text, color, duration, iconRes, iconTint, feedback)
        run(data)
    }

    private fun run(data: Data) {
        // Showing the same toast again dismisses it; `loading` stays out of the comparison because
        // that is how the loading toast is hidden — same text and color, only the flag flips.
        // Compared on the frame that sets the current toast: a hide sent right after its show would
        // otherwise miss it and wait in the queue behind a loading toast that never hides.
        postOnAnimation {
            val current = currentData
            val queuedLoading = dataQueue.indexOfFirst { it.loading && !data.loading && it.sameToast(data) }
            when {
                current != null && current.sameToast(data) -> dismissCurrent()
                current == null -> currentData = data
                // A loading toast still waiting behind another one is cancelled in the queue too.
                queuedLoading >= 0 -> dataQueue.removeAt(queuedLoading)
                else -> dataQueue.add(data)
            }
        }
    }

    private fun Data.sameToast(other: Data): Boolean {
        return text == other.text &&
                color == other.color &&
                iconRes == other.iconRes &&
                iconTint == other.iconTint
    }

    private fun createData(
        loading: Boolean,
        text: CharSequence,
        color: Int,
        duration: Long,
        @DrawableRes iconRes: Int,
        @ColorInt iconTint: Int,
        feedback: ToastFeedback,
    ): Data {
        return Data(
            loading = loading,
            text = text,
            color = if (color == Color.TRANSPARENT) {
                context.backgroundContentTintColor
            } else {
                color
            },
            duration = duration,
            iconRes = iconRes,
            iconTint = iconTint,
            feedback = feedback,
        )
    }

    private fun nextQueue() {
        currentData = dataQueue.removeFirstOrNull()
    }

    private fun runData() {
        val data = currentData ?: return
        if (!data.loading) {
            playHaptic(data.feedback)
        }
        textView.text = data.text
        applyIcon(data)
        val startOffset = if (data.loading || data.iconRes != 0) {
            horizontalOffsetWithIcon
        } else {
            horizontalOffset
        }
        setPaddingRelative(startOffset, verticalOffset, horizontalOffset, verticalOffset)
        background.setTint(data.color)
        removeCallbacks(hideRunnable)
        animate().cancel()
        stopFractionAnimation()
        translationX = 0f
        applyFraction(0f)
        visibility = View.VISIBLE

        showPending = true
        doOnLayout {
            if (data !== currentData) {
                return@doOnLayout
            }
            showPending = false
            if (data.loading) {
                showLoading()
            } else if (loaderView.visibility == View.VISIBLE) {
                hide()
            } else {
                showDefault(data.duration)
            }
        }
    }

    private fun playHaptic(feedback: ToastFeedback) {
        when (feedback) {
            ToastFeedback.Neutral -> hapticSoftTap()
            ToastFeedback.Success -> hapticConfirm()
            ToastFeedback.Failure -> hapticReject()
        }
    }

    private fun applyIcon(data: Data) {
        if (data.iconRes == 0) {
            iconView.visibility = View.GONE
            return
        }
        iconView.setImageResource(data.iconRes)
        iconView.imageTintList = if (data.iconTint == Color.TRANSPARENT) {
            null
        } else {
            ColorStateList.valueOf(data.iconTint)
        }
        iconView.visibility = View.VISIBLE
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
        animateFraction(target = 1f, duration = SHOW_DURATION, interpolator = showInterpolator)
    }

    private fun shownTranslationY(): Float = statusBarHeight + TOP_OFFSET_DP.dp

    private fun applyFraction(fraction: Float) {
        shownFraction = fraction
        alpha = fraction
        translationY = shownTranslationY() - (1f - fraction) * SLIDE_OFFSET_DP.dp
    }

    private fun animateFraction(target: Float, duration: Long, interpolator: TimeInterpolator) {
        stopFractionAnimation()
        fractionTarget = target
        val animator = ValueAnimator.ofFloat(shownFraction, target)
        animator.duration = duration
        animator.interpolator = interpolator
        animator.addUpdateListener { applyFraction(it.animatedValue as Float) }
        animator.doOnEnd {
            if (fractionAnimator !== animator) {
                return@doOnEnd
            }
            fractionAnimator = null
            if (target == 0f) {
                onHidden()
            }
        }
        fractionAnimator = animator
        animator.start()
    }

    private fun stopFractionAnimation() {
        val animator = fractionAnimator ?: return
        fractionAnimator = null
        animator.cancel()
    }

    private fun onHidden() {
        loaderView.visibility = View.GONE
        visibility = View.GONE
        nextQueue()
    }

    // A loading toast dismissed before it started appearing is dropped on the spot: hiding it would let
    // the pending layout show it afterwards, and it would then stay up for good. A regular toast shown
    // twice before it appears is shown once.
    private fun dismissCurrent() {
        if (!showPending) {
            hide()
            return
        }
        if (currentData?.loading != true) {
            return
        }
        showPending = false
        removeCallbacks(hideRunnable)
        loaderView.visibility = View.GONE
        visibility = View.GONE
        nextQueue()
    }

    private fun hide() {
        removeCallbacks(hideRunnable)
        if (visibility != View.VISIBLE) {
            return
        }
        animateFraction(target = 0f, duration = HIDE_DURATION, interpolator = hideInterpolator)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val data = currentData
        val hiding = fractionAnimator != null && fractionTarget == 0f
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            gestureData = data
        }
        if (data == null || data.loading || hiding || data !== gestureData) {
            gestureData = null
            recycleVelocityTracker()
            swipeDragging = false
            return super.onTouchEvent(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(hideRunnable)
                animate().cancel()
                if (fractionAnimator != null) {
                    stopFractionAnimation()
                    applyFraction(1f)
                }
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
        stopFractionAnimation()
        removeCallbacks(hideRunnable)
        loaderView.visibility = View.GONE
        visibility = View.GONE
        translationX = 0f
        applyFraction(0f)
        nextQueue()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val compat = WindowInsetsCompat.toWindowInsetsCompat(insets)
        statusBarHeight = compat.getInsets(WindowInsetsCompat.Type.statusBars()).top
        return super.onApplyWindowInsets(insets)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView === this) {
            ToastPresence.set(this, visibility == View.VISIBLE)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ToastPresence.set(this, false)
    }

    companion object {
        const val DURATION_DEFAULT = 2000L
        const val DURATION_LONG = 3000L

        private const val SHOW_DURATION = 300L
        private const val HIDE_DURATION = 200L
        private const val SLIDE_OFFSET_DP = 8f
        private const val SWIPE_ANIMATION_DURATION = 160L
        private const val MIN_DISMISS_VELOCITY_DPS = 400
        private const val TOP_OFFSET_DP = 8f
        private const val MIN_HEIGHT_DP = 48
        private const val ELEVATION_DP = 4f

        private val showInterpolator = PathInterpolator(0.16f, 1f, 0.3f, 1f)
        private val hideInterpolator = PathInterpolator(0.42f, 0f, 1f, 1f)
    }
}
