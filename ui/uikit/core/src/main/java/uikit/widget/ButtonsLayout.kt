package uikit.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
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

    private val childVisibleCount: Int
        get() = getVisibleChildViews().size

    private val columnCount: Int
        get() = childVisibleCount.coerceAtMost(MAX_COLUMN_COUNT)

    private val rowCount: Int
        get() = (childVisibleCount + columnCount - 1) / columnCount

    private val drawable = ButtonsLayoutDrawable(context)

    init {
        background = drawable
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val views = getVisibleChildViews()
        if (views.isEmpty()) {
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

        for (child in views) {
            child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
        }

        drawable.rowCount = rowCount
        drawable.columnCount = columnCount
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val childWidth = (r - l) / columnCount
        val childHeight = ROW_HEIGHT
        val views = getVisibleChildViews()

        for ((i, child) in views.withIndex()) {
            val row = i / columnCount
            val column = i % columnCount
            val left = column * childWidth
            val top = row * childHeight
            val right = left + childWidth
            val bottom = top + childHeight
            child.layout(left, top, right, bottom)
        }
    }

    private fun getVisibleChildViews(): List<View> {
        return (0 until childCount).map { getChildAt(it) }.filter { it.visibility != View.GONE }
    }
}