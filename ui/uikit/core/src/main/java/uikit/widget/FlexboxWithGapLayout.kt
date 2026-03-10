package uikit.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import com.google.android.flexbox.FlexboxLayout
import uikit.R
import uikit.base.BaseDrawable
import uikit.extensions.dp
import uikit.extensions.useAttributes

class FlexboxWithGapLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FlexboxLayout(context, attrs, defStyle) {

    private var gapVertical: Int = 0
    private var gapHorizontal: Int = 0

    init {
        context.useAttributes(attrs, R.styleable.FlexboxWithGapLayout) {
            gapVertical = it.getDimensionPixelSize(R.styleable.FlexboxWithGapLayout_gapVertical, 0)
            gapHorizontal = it.getDimensionPixelSize(R.styleable.FlexboxWithGapLayout_gapHorizontal, 0)
        }
        dividerDrawableVertical = DividerDrawable(gapVertical)
        dividerDrawableHorizontal = DividerDrawable(gapHorizontal)
        setShowDivider(SHOW_DIVIDER_MIDDLE)
    }

    private class DividerDrawable(val size: Int): BaseDrawable() {
        override fun draw(canvas: Canvas) {  }
        override fun getIntrinsicWidth() = size
        override fun getIntrinsicHeight() = size
        override fun getMinimumWidth() = size
        override fun getMinimumHeight() = size
    }

}