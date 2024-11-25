package com.tonapps.emoji.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import com.tonapps.emoji.Emoji
import com.tonapps.emoji.R
import com.tonapps.emoji.ui.drawable.EmojiDrawable

class EmojiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.textViewStyle,
) : View(context, attrs, defStyle) {

    private var emoji: CharSequence = ""
    private val drawable = EmojiDrawable(context)

    private var fadeEnable: Boolean
        get() = drawable.fadeEnable
        set(value) {
            drawable.fadeEnable = value
        }

    init {
        background = drawable
        context.theme.obtainStyledAttributes(attrs, R.styleable.EmojiView, 0, 0).apply {
            try {
                fadeEnable = getBoolean(R.styleable.EmojiView_android_fadeEnabled, false)
            } finally {
                recycle()
            }
        }
    }

    fun setEmoji(emoji: CharSequence, tintColor: Int): Boolean {
        if (this.emoji != emoji) {
            this.emoji = emoji
            drawable.setEmoji(emoji, tintColor)
            return true
        }
        return false
    }

    fun getEmoji(): CharSequence {
        return emoji
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = minOf(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
        setMeasuredDimension(size, size)
    }

    override fun hasOverlappingRendering() = false
}