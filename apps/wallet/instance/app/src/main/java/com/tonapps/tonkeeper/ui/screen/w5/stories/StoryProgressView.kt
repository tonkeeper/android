package com.tonapps.tonkeeper.ui.screen.w5.stories

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.View

class StoryProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val drawable = StoryProgressDrawable(context)

    var progress: Float
        get() = drawable.progress
        set(value) {
            drawable.progress = value
        }

    init {
        background = drawable
    }
}