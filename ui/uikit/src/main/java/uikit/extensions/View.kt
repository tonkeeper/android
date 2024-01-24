package uikit.extensions

import android.animation.ValueAnimator
import android.graphics.Insets
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowInsets
import android.view.animation.Animation
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.AnimRes
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.animation.doOnEnd
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import kotlin.math.sin

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

val View.window: Window?
    get() = context.window

fun ScrollView.scrollToView(view: View) {
    val scrollBounds = Rect()
    getHitRect(scrollBounds)
    if (!view.getLocalVisibleRect(scrollBounds)) {
        post {
            smoothScrollTo(0, view.top)
        }
    }
}

fun ScrollView.scrollToBottom() {
    post {
        fullScroll(View.FOCUS_DOWN)
    }
}

fun ScrollView.scrollToTop() {
    post {
        fullScroll(View.FOCUS_UP)
    }
}

fun View.getInsetsControllerCompat(): WindowInsetsControllerCompat? {
    val window = window ?: return null
    return WindowInsetsControllerCompat(window, this)
}

fun EditText.focusWithKeyboard() {
    requestFocus()
    selectionAll()
    post {
        requestFocus()
        getInsetsControllerCompat()?.show(WindowInsetsCompat.Type.ime())
    }
}

fun EditText.hideKeyboard() {
    clearFocus()
    getInsetsControllerCompat()?.hide(WindowInsetsCompat.Type.ime())
}

fun EditText.selectionAll() {
    if (text.isNotEmpty()) {
        try {
            setSelection(text.length)
        } catch (ignored: Throwable) { }
    }
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

fun View.getDrawable(@DrawableRes resId: Int): Drawable {
    return AppCompatResources.getDrawable(context, resId)!!
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

fun View.getColor(resId: Int): Int {
    return context.getColor(resId)
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

fun View.startAnimation(@AnimRes resId: Int): Animation {
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

val NestedScrollView.verticalOffset: Flow<Int>
    get() = callbackFlow {
        val listener = object : OnScrollChangeListener {

            var verticalOffset = 0

            override fun onScrollChange(
                v: NestedScrollView,
                scrollX: Int,
                scrollY: Int,
                oldScrollX: Int,
                oldScrollY: Int
            ) {
                verticalOffset = scrollY
                trySend(verticalOffset)
            }
        }
        setOnScrollChangeListener(listener)
        awaitClose()
    }

val NestedScrollView.verticalScrolled: Flow<Boolean>
    get() = verticalOffset.map {
        it > 0
    }.distinctUntilChanged()


fun View.doOnOnApplyWindowInsets(block: (WindowInsetsCompat) -> WindowInsetsCompat) {
    setOnApplyWindowInsetsListener { v, insets ->
        val insetsCompat = WindowInsetsCompat.toWindowInsetsCompat(insets, v)
        block(insetsCompat).toWindowInsets() ?: insets
    }
}

inline fun View.doOnBottomInsetsChanged(crossinline block: (Int) -> Unit) {
    val callback = object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {

        override fun onProgress(insets: WindowInsetsCompat, runningAnimations: MutableList<WindowInsetsAnimationCompat>): WindowInsetsCompat {
            val keyboardInsets = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val barInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val bottomOffset = keyboardInsets.coerceAtLeast(barInsets)
            block(bottomOffset)
            return insets
        }
    }

    ViewCompat.setWindowInsetsAnimationCallback(this, callback)
}

fun View.pinToBottomInsets() {
    doOnBottomInsetsChanged {
        translationY = -it.toFloat()
    }
}