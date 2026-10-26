package uikit.extensions

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import androidx.annotation.IdRes
import com.tonapps.uikit.list.ListCell
import uikit.R
import uikit.drawable.CellBackgroundDrawable
import kotlin.math.hypot

private const val DEFAULT_PRESSED_SCALE = 0.985f
private const val PRESS_DOWN_DURATION = 120L
private const val PRESS_UP_DURATION = 420L
private const val MOVE_CANCEL_SLOP_DP = 10f

private const val PRESS_SCALE_WHOLE = 1
private const val PRESS_SCALE_CONTENT = 2

// Haptic played on a click. Controls whose action already gives feedback of its own pass the matching
// value (and drop the legacy call) or None, so a tap never fires two haptics.
enum class MoonHaptic { Tick, Confirm, Reject, None }

private fun View.playMoonHaptic(haptic: MoonHaptic) {
    when (haptic) {
        MoonHaptic.Tick -> hapticTick()
        MoonHaptic.Confirm -> hapticConfirm()
        MoonHaptic.Reject -> hapticReject()
        MoonHaptic.None -> Unit
    }
}

private val View.pressScaleMode: Int
    get() = getTag(R.id.press_scale_attached) as? Int ?: 0

private fun View.setPressScaleMode(mode: Int) {
    setTag(R.id.press_scale_attached, mode)
}

// Press/release curves from design: 120ms squeeze, 420ms springy return with a slight overshoot.
private val pressDownInterpolator = PathInterpolator(0.3f, 0.75f, 0.45f, 1f)
private val pressUpInterpolator = PathInterpolator(0.34f, 1.7f, 0.45f, 1f)

/**
 * Ripple replacement for stand-alone clickable Views (icon buttons, tabs): scales the view down
 * while pressed, around the touch point. For grouped list cells use [applyPressScaleContent]
 * instead — scaling the view there would shrink the shared cell background and break the grouped
 * card.
 */
fun View.applyPressScale(pressedScale: Float = DEFAULT_PRESSED_SCALE, haptic: MoonHaptic = MoonHaptic.Tick) {
    if (pressScaleMode == PRESS_SCALE_WHOLE) {
        return
    }
    setPressScaleMode(PRESS_SCALE_WHOLE)
    pressScaleTouchListener(
        haptic = haptic,
        onPress = { x, y ->
            pivotX = x
            pivotY = y
            animate()
                .scaleX(pressedScale)
                .scaleY(pressedScale)
                .setStartDelay(0)
                .setDuration(PRESS_DOWN_DURATION)
                .setInterpolator(pressDownInterpolator)
                .start()
        },
        onRelease = { startDelay ->
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(startDelay)
                .setDuration(PRESS_UP_DURATION)
                .setInterpolator(pressUpInterpolator)
                .start()
        },
    )
}

/**
 * Ripple replacement for grouped list cells: scales only the children, leaving the view and its
 * shared cell background put so neighbouring cells keep forming one card. Children scale around
 * the touch point so the content shrinks as one block anchored where the finger is.
 */
fun ViewGroup.applyPressScaleContent(pressedScale: Float = DEFAULT_PRESSED_SCALE, haptic: MoonHaptic = MoonHaptic.Tick) {
    if (pressScaleMode == PRESS_SCALE_CONTENT) {
        return
    }
    setPressScaleMode(PRESS_SCALE_CONTENT)
    var animator: ValueAnimator? = null
    var currentScale = 1f
    var tapX = 0f
    var tapY = 0f

    fun applyScale(scale: Float) {
        currentScale = scale
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.pivotX = tapX - child.left
            child.pivotY = tapY - child.top
            child.scaleX = scale
            child.scaleY = scale
        }
    }

    fun animateScale(target: Float, duration: Long, interpolator: TimeInterpolator, startDelay: Long) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(currentScale, target).apply {
            this.duration = duration
            this.startDelay = startDelay
            this.interpolator = interpolator
            addUpdateListener { applyScale(it.animatedValue as Float) }
            start()
        }
    }

    pressScaleTouchListener(
        haptic = haptic,
        onPress = { x, y ->
            tapX = x
            tapY = y
            animateScale(pressedScale, PRESS_DOWN_DURATION, pressDownInterpolator, 0L)
        },
        onRelease = { startDelay -> animateScale(1f, PRESS_UP_DURATION, pressUpInterpolator, startDelay) },
    )
}

/**
 * Ripple replacement for making a View clickable: press-scale + [haptic], then the click listener.
 * `scale = false` for a view that must not scale (a scrim); pass a non-default [haptic] (or None)
 * when the click's own action already gives feedback.
 */
fun View.setMoonOnClickListener(scale: Boolean = true, haptic: MoonHaptic = MoonHaptic.Tick, onClick: (View) -> Unit) {
    if (scale) {
        attachPressScale(haptic)
    }
    setOnClickListener { onClick(it) }
}

fun View.setMoonOnClickListener(@IdRes id: Int, scale: Boolean = true, haptic: MoonHaptic = MoonHaptic.Tick, onClick: () -> Unit) {
    findViewById<View>(id).setMoonOnClickListener(scale, haptic) { onClick() }
}

fun View.setMoonOnLongClickListener(scale: Boolean = true, haptic: MoonHaptic = MoonHaptic.Tick, onLongClick: (View) -> Boolean) {
    if (scale) {
        attachPressScale(haptic)
    }
    setOnLongClickListener { onLongClick(it) }
}

/** Consumes taps so they don't fall through to views behind (dialog scrims, loader overlays). */
fun View.swallowClicks() {
    setOnClickListener { }
}

private fun View.attachPressScale(haptic: MoonHaptic) {
    val position = CellBackgroundDrawable.find(this)?.position
    if (this is ViewGroup && position != null && position != ListCell.Position.SINGLE) {
        applyPressScaleContent(haptic = haptic)
    } else {
        applyPressScale(haptic = haptic)
    }
}

/**
 * Shared press gesture for the scale helpers: on down (only when the view is clickable) runs
 * [onPress] with the touch position; on up/cancel runs [onRelease] with a start delay that lets
 * the shrink finish first, so a fast tap still shows it. The press is also cancelled once the
 * finger drifts more than 10dp from the down point (a scroll), matching the parent-intercept
 * cancel for containers that never intercept. A finger lifted inside the view (a click, as
 * opposed to a cancelled press or a scroll) also plays the standard tick haptic, so every
 * press-scaled element gives the same feedback on tap. Returns `false` so the view keeps handling
 * its own clicks.
 */
@SuppressLint("ClickableViewAccessibility")
private fun View.pressScaleTouchListener(
    haptic: MoonHaptic = MoonHaptic.Tick,
    onPress: (x: Float, y: Float) -> Unit,
    onRelease: (startDelay: Long) -> Unit,
) {
    var downAt = 0L
    var downX = 0f
    var downY = 0f
    var scaledDown = false

    fun release(withHaptic: Boolean, event: MotionEvent, view: View) {
        scaledDown = false
        val elapsed = SystemClock.uptimeMillis() - downAt
        onRelease((PRESS_DOWN_DURATION - elapsed).coerceAtLeast(0L))
        val isClick = withHaptic &&
            view.isClickable &&
            event.x >= 0f && event.x <= view.width && event.y >= 0f && event.y <= view.height &&
            // a lift after a long-press is not a click; the long-press played its own haptic
            !(view.isLongClickable && elapsed >= ViewConfiguration.getLongPressTimeout())
        if (isClick) {
            view.playMoonHaptic(haptic)
        }
    }

    setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (v.isClickable || v.isLongClickable) {
                v.muteRippleHighlight()
                downAt = SystemClock.uptimeMillis()
                downX = event.x
                downY = event.y
                scaledDown = true
                onPress(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> if (scaledDown) {
                val slop = MOVE_CANCEL_SLOP_DP * v.resources.displayMetrics.density
                if (hypot(event.x - downX, event.y - downY) > slop) {
                    release(withHaptic = false, event = event, view = v)
                }
            }
            MotionEvent.ACTION_UP -> if (scaledDown) {
                release(withHaptic = true, event = event, view = v)
            }
            MotionEvent.ACTION_CANCEL -> if (scaledDown) {
                release(withHaptic = false, event = event, view = v)
            }
        }
        false
    }
}

private val transparentRipple = ColorStateList.valueOf(Color.TRANSPARENT)

/**
 * Press-scale replaces the ripple, so silence the ripple highlight the view may have picked up from
 * its background or foreground (the shared button style drawables wrap their shape in a `<ripple>`).
 * The drawable's content (the button shape and its state selectors) keeps drawing — only the
 * highlight is muted. Runs on every press-down so a background assigned after the helper was
 * applied is muted too; mutate first so other views sharing the same drawable resource keep theirs.
 */
private fun View.muteRippleHighlight() {
    background?.muteRippleHighlight()
    foreground?.muteRippleHighlight()
}

private fun Drawable.muteRippleHighlight() {
    if (this is RippleDrawable) {
        (mutate() as RippleDrawable).setColor(transparentRipple)
    }
}
