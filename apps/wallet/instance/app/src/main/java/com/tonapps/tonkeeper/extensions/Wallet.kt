package com.tonapps.tonkeeper.extensions

import android.content.Context
import android.text.SpannableString
import android.util.Log
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.emoji.Emoji
import com.tonapps.uikit.color.textPrimaryColor
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.localization.Localization
import uikit.extensions.drawable
import uikit.extensions.sp
import uikit.span.ImageSpanCompat

fun Wallet.Label.getTitle(
    context: Context,
    textView: AppCompatTextView,
    limit: Int = -1
): CharSequence {
    return getTitle(context, textView.textSize, textView.currentTextColor, limit)
}

fun Wallet.Label.getTitle(
    context: Context,
    textSize: Float = 16f.sp,
    textColor: Int = context.textPrimaryColor,
    limit: Int = -1
): CharSequence {
    if (isEmpty) {
        return context.getString(Localization.wallet)
    }

    val nameFixed = if (limit > 0 && name.length > limit) {
        name.substring(0, limit) + "…"
    } else {
        name
    }

    if (!Emoji.isCustomIcon(emoji)) {
        return String.format("%s %s", emoji, nameFixed)
    }

    val id = Emoji.getCustomEmoji(emoji) ?: return nameFixed
    val drawable = context.drawable(id, textColor).apply {
        val offset = 3f.sp
        setBounds(0, offset.toInt(), textSize.toInt(), (textSize + offset).toInt())
    }
    val imageSpan = ImageSpanCompat(drawable)

    val spannableString = SpannableString("X $nameFixed")
    spannableString.setSpan(imageSpan, 0, 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)

    return spannableString
}