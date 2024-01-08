package uikit.widget

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import uikit.drawable.ButtonsLayoutDrawable
import uikit.extensions.dp

class ButtonsLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ViewGroup(context, attrs, defStyle) {

    private companion object {
        private const val MAX_COLUMN_COUNT = 3
        private val ROW_HEIGHT = 80.dp
    }

    private val columnCount: Int
        get() = childCount.coerceAtMost(MAX_COLUMN_COUNT)

    private val rowCount: Int
        get() = (childCount + columnCount - 1) / columnCount

    private val drawable = ButtonsLayoutDrawable(context)

    init {
        background = drawable
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (childCount == 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec((ROW_HEIGHT * rowCount), MeasureSpec.EXACTLY)
        )

        val childWidth = MeasureSpec.getSize(widthMeasureSpec) / columnCount
        val childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY)
        val childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(ROW_HEIGHT, MeasureSpec.EXACTLY)

        for (i in 0 until childCount) {
            getChildAt(i).measure(childWidthMeasureSpec, childHeightMeasureSpec)
        }

        drawable.rowCount = rowCount
        drawable.columnCount = columnCount
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val childWidth = (r - l) / columnCount
        val childHeight = ROW_HEIGHT

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val row = i / columnCount
            val column = i % columnCount
            val left = column * childWidth
            val top = row * childHeight
            val right = left + childWidth
            val bottom = top + childHeight
            child.layout(left, top, right, bottom)
        }
    }

}