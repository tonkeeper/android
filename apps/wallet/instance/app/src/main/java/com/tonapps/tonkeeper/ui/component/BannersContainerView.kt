package com.tonapps.tonkeeper.ui.component

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

class BannersContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.makeMeasureSpec((width / 2.17877095).toInt(), MeasureSpec.EXACTLY)
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), height)
    }
}