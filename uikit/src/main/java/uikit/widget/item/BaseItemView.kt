package uikit.widget.item

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.LinearLayoutCompat
import uikit.R
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.setPaddingHorizontal
import uikit.list.ListCell
import uikit.list.ListCell.Companion.drawable

open class BaseItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayoutCompat(context, attrs, defStyle) {

    var position: ListCell.Position = ListCell.Position.SINGLE
        set(value) {
            field = value
            background = value.drawable(context)
        }

    init {
        setPaddingHorizontal(context.getDimensionPixelSize(R.dimen.offsetMedium))
        orientation = HORIZONTAL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = context.getDimensionPixelSize(R.dimen.itemHeight)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }
}