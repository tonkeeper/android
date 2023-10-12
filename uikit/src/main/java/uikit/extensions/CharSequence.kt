package com.tonkeeper.uikit.extensions

import android.content.Context
import android.text.SpannableString
import android.text.style.ForegroundColorSpan

fun CharSequence.processAnnotation(context: Context): SpannableString {
    val spannableString = this as? SpannableString ?: SpannableString(this)
    val annotations = spannableString.getSpans(0, spannableString.length, android.text.Annotation::class.java)
    for (annotation in annotations) {
        if (annotation.key == "colorRes") {
            val color = context.getColorByIdentifier(annotation.value)
            spannableString.setSpan(ForegroundColorSpan(color), spannableString.getSpanStart(annotation), spannableString.getSpanEnd(annotation), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    return spannableString
}
