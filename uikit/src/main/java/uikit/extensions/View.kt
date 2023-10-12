package com.tonkeeper.uikit.extensions

import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import androidx.annotation.LayoutRes
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

var View.scale: Float
    get() = scaleX
    set(value) {
        scaleX = value
        scaleY = value
    }

val View.window: Window?
    get() = context.window

fun View.getInsetsControllerCompat(): WindowInsetsControllerCompat? {
    val window = window ?: return null
    return WindowInsetsControllerCompat(window, this)
}

fun EditText.focusWidthKeyboard() {
    requestFocus()
    getInsetsControllerCompat()?.show(WindowInsetsCompat.Type.ime())
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

