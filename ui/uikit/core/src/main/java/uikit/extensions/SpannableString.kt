package uikit.extensions

import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan

fun SpannableString.setColor(color: Int, start: Int, end: Int) {
    val what = ForegroundColorSpan(color)
    setSpan(what, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
}
