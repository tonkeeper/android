package uikit.extensions

import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.transition.AutoTransition
import androidx.transition.Transition
import androidx.transition.TransitionListenerAdapter
import androidx.transition.TransitionManager

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

fun EditText.focusWidthKeyboard() {
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
