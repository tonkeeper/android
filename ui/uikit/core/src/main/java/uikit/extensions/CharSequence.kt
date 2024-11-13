package uikit.extensions

import android.content.Context
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log

fun CharSequence.processAnnotation(context: Context): SpannableString {
    val spannableString = this as? SpannableString ?: SpannableString(this)
    val annotations = spannableString.getSpans(0, spannableString.length, android.text.Annotation::class.java)
    for (annotation in annotations) {
        if (annotation.key == "colorAttr") {
            val color = context.getColorByAttr(annotation.value)
            spannableString.setSpan(ForegroundColorSpan(color), spannableString.getSpanStart(annotation), spannableString.getSpanEnd(annotation), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    return spannableString
}
