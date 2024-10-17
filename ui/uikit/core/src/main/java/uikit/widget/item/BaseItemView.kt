package uikit.widget.item

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.view.setPadding
import com.tonapps.uikit.list.ListCell
import uikit.R
import uikit.extensions.dp
import uikit.extensions.drawable
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.setPaddingHorizontal
import uikit.extensions.setPaddingVertical
import uikit.widget.ColumnLayout
import uikit.widget.RowLayout

open class BaseItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RowLayout(context, attrs, defStyle) {

    var position: ListCell.Position = ListCell.Position.SINGLE
        set(value) {
            field = value
            background = value.drawable(context)
        }

    init {
        setPadding(14.dp)
    }

    /*override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = context.getDimensionPixelSize(R.dimen.itemHeight)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }*/
}