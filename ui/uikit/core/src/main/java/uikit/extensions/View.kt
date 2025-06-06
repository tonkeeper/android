package uikit.extensions

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.animation.Animation
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.AnimRes
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.animation.doOnEnd
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateMargins
import androidx.core.widget.NestedScrollView
import androidx.core.widget.NestedScrollView.OnScrollChangeListener
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.Transition
import androidx.transition.TransitionListenerAdapter
import androidx.transition.TransitionManager
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import uikit.insets.KeyboardAnimationCallback
import kotlin.math.sin
import androidx.core.graphics.drawable.toDrawable

var View.scale: Float
    get() = scaleX
    set(value) {
        scaleX = value
        scaleY = value
    }

var View.pivot: Float
    get() = pivotX
    set(value) {
        pivotX = value
        pivotY = value
    }

val View.isVisibleForUser: Boolean
    get() = isAttachedToWindow && isShown && windowVisibility == View.VISIBLE

val View.window: Window?
    get() = context.window

fun View.getInsetsControllerCompat(): WindowInsetsControllerCompat? {
    val window = window ?: return null
    return WindowInsetsControllerCompat(window, this)
}

fun View.getRootWindowInsetsCompat(): WindowInsetsCompat? {
    return ViewCompat.getRootWindowInsets(this)
}

val View.statusBarHeight: Int
    get() {
        val top = getRootWindowInsetsCompat()?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        if (top == 0) {
            val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resId > 0) {
                return context.resources.getDimensionPixelSize(resId)
            }
        }
        return top
    }

fun ViewGroup.inflate(
    @LayoutRes
    layoutRes: Int
): View {
    return context.inflate(layoutRes, this, false)
}

fun View.setPaddingTop(paddingTop: Int) {
    setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
}

fun View.setPaddingBottom(paddingBottom: Int) {
    setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
}

fun View.setPaddingStart(paddingStart: Int) {
    setPadding(paddingStart, paddingTop, paddingRight, paddingBottom)
}

fun View.setPaddingEnd(paddingEnd: Int) {
    setPadding(paddingLeft, paddingTop, paddingEnd, paddingBottom)
}

fun View.setPaddingHorizontal(paddingHorizontal: Int) {
    setPadding(paddingHorizontal, paddingTop, paddingHorizontal, paddingBottom)
}

fun View.setPaddingVertical(paddingVertical: Int) {
    setPadding(paddingLeft, paddingVertical, paddingRight, paddingVertical)
}

fun ViewGroup.setView(view: View) {
    removeAllViews()
    addView(view)
}

fun View.roundTop(radius: Int) {
    if (radius == 0) {
        outlineProvider = null
        clipToOutline = false
    } else {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height + radius * 2, radius.toFloat())
            }
        }
        clipToOutline = true
    }
}

fun View.roundBottom(radius: Int) {
    if (radius == 0) {
        outlineProvider = null
        clipToOutline = false
    } else {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, -radius * 2, view.width, view.height, radius.toFloat())
            }
        }
        clipToOutline = true
    }
}

fun View.circle() {
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            val size = minOf(view.width, view.height)
            outline.setOval(0, 0, size, size)
        }
    }
    clipToOutline = true
}

fun View.round(radius: Int) {
    if (radius == 0) {
        outlineProvider = null
        clipToOutline = false
    } else {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius.toFloat())
            }
        }
        clipToOutline = true
    }
}

fun View.round(radius: Float) {
    round(radius.toInt())
}

fun View.getDrawable(@DrawableRes resId: Int): Drawable {
    return try {
        AppCompatResources.getDrawable(context, resId) ?: throw IllegalArgumentException()
    } catch (e: Throwable) {
        Color.TRANSPARENT.toDrawable()
    }
}

fun View.withAnimation(duration: Long = 120, block: () -> Unit) {
    if (this !is ViewGroup) {
        block()
        return
    }

    val transitionAdapter = object : TransitionListenerAdapter() {
        override fun onTransitionStart(transition: Transition) {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        override fun onTransitionEnd(transition: Transition) {
            transition.removeListener(this)
            setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    val transition = AutoTransition()
    transition.duration = duration
    transition.addListener(transitionAdapter)

    TransitionManager.beginDelayedTransition(this, transition)
    block()
}

fun View.expandTouchArea(extraPadding: Int) {
    val parent = parent as? View ?: return
    parent.post {
        val rect = Rect()
        getHitRect(rect)
        rect.inset(-extraPadding, -extraPadding)
        parent.touchDelegate = TouchDelegate(rect, this)
    }
}

fun View.expandTouchArea(left: Int, top: Int, right: Int, bottom: Int) {
    val parent = parent as? View ?: return
    parent.post {
        val rect = Rect()
        getHitRect(rect)
        rect.top -= top
        rect.left -= left
        rect.right += right
        rect.bottom += bottom
        parent.touchDelegate = TouchDelegate(rect, this)
    }
}

fun TextView.setEndDrawable(drawable: Drawable?) {
    setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null)
}

fun TextView.setStartDrawable(drawable: Drawable?) {
    setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
}

fun TextView.setEndDrawable(@DrawableRes resId: Int) {
    setCompoundDrawablesWithIntrinsicBounds(null, null, getDrawable(resId), null)
}

fun TextView.setStartDrawable(@DrawableRes resId: Int) {
    setCompoundDrawablesWithIntrinsicBounds(getDrawable(resId), null, null, null)
}

val ViewPager2.recyclerView: RecyclerView
    get() = getChildAt(0) as RecyclerView

val ViewPager2.layoutManager: RecyclerView.LayoutManager?
    get() = recyclerView.layoutManager

fun ViewPager2.findViewHolderForAdapterPosition(position: Int): RecyclerView.ViewHolder? {
    return recyclerView.findViewHolderForAdapterPosition(position)
}

fun View.hapticConfirm() {
    post {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
}

fun View.hapticReject() {
    post {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
}

fun View.runAnimation(@AnimRes resId: Int): Animation {
    val animation = context.getAnimation(resId)
    startAnimation(animation)
    return animation
}

fun View.startSnakeAnimation(
    count: Int = 3,
    offset: Int = 16.dp,
    duration: Long = 400
) {
    val animator = ValueAnimator.ofFloat(0f, 1f)
    animator.addUpdateListener { animation ->
        val x = animation.animatedValue as Float
        translationX = (4 * x * (1 - x) * sin(count * (x * Math.PI)) * offset).toFloat()
    }
    animator.doOnEnd {
        translationX = 0f
    }
    animator.duration = duration
    animator.start()
}

fun View.reject() {
    startSnakeAnimation()
    hapticReject()
}

inline fun View.doKeyboardAnimation(
    ignoreNavBar: Boolean = false,
    crossinline block: (
        offset: Int,
        progress: Float,
        isShowing: Boolean) -> Unit
) {
    val animationCallback = object : KeyboardAnimationCallback(this, ignoreNavBar) {
        override fun onKeyboardOffsetChanged(offset: Int, progress: Float, isShowing: Boolean) {
            block(offset, progress, isShowing)
        }
    }
    ViewCompat.setWindowInsetsAnimationCallback(this, animationCallback)
}

fun View.pinToBottomInsets() {
    doKeyboardAnimation { offset, _, _ ->
        translationY = -offset.toFloat()
    }
}

fun View.applyBottomInsets() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        view.translationY = -insets.bottomBarsOffset.toFloat()
        insets
    }
}

fun View.getViews(): List<View> {
    val result = mutableListOf<View>()
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            result.add(getChildAt(i))
        }
    }
    return result
}

fun TextView.setLeftDrawable(drawable: Drawable?) {
    setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
}

fun TextView.setRightDrawable(drawable: Drawable?) {
    setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null)
}

fun TextView.clearDrawables() {
    setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
}

fun View.setChildText(@IdRes id: Int, textRes: Int) {
    findViewById<TextView>(id).setText(textRes)
}

fun View.gone(@IdRes id: Int) {
    findViewById<View>(id).visibility = View.GONE
}

fun View.round(@IdRes id: Int, radius: Int) {
    findViewById<View>(id).round(radius)
}

fun View.roundBottom(@IdRes id: Int, radius: Int) {
    findViewById<View>(id).roundBottom(radius)
}

fun View.setOnClickListener(@IdRes id: Int, block: () -> Unit) {
    findViewById<View>(id).setOnClickListener { block() }
}

fun View.setBackgroundColor(@IdRes id: Int, color: Int) {
    findViewById<View>(id).setBackgroundColor(color)
}

fun View.setBackground(@IdRes id: Int, drawable: Drawable) {
    findViewById<View>(id).background = drawable
}

inline fun <reified R: View> View.findViewByClass(): R? {
    val clazz = R::class.java
    return findViewByClass(clazz) as R?
}

fun View.findViewByClass(clazz: Class<out View>): View? {
    if (clazz.isInstance(this)) {
        return this
    }
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (clazz.isInstance(child)) {
                return child
            } else if (child is ViewGroup) {
                val view = child.findViewByClass(clazz)
                if (view != null) {
                    return view
                }
            }
        }
    }
    return null
}

fun View.hideKeyboard(ignoreFocus: Boolean = true) {
    val editText = if (this is EditText) this else findViewByClass<EditText>() ?: return
    val controller = editText.getInsetsControllerCompat() ?: return
    if (ignoreFocus || editText.hasFocus()) {
        editText.clearFocus()
        controller.hide(WindowInsetsCompat.Type.ime())
    }
}
