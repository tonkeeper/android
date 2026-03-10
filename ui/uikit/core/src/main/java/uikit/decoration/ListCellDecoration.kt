package uikit.decoration

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import uikit.R
import uikit.extensions.getDimensionPixelSize

class ListCellDecoration(
    context: Context
): RecyclerView.ItemDecoration() {

    private val offset = context.getDimensionPixelSize(R.dimen.offsetLarge)
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)
        val adapter = parent.adapter as? com.tonapps.uikit.list.BaseListAdapter ?: return
        val position = parent.getChildAdapterPosition(view)
        val item = adapter.getItem(position)
        if (item is com.tonapps.uikit.list.ListCell) {
            val cellPosition = item.position
            if (cellPosition == com.tonapps.uikit.list.ListCell.Position.SINGLE || cellPosition == com.tonapps.uikit.list.ListCell.Position.LAST) {
                outRect.bottom += offset
            }
        } else {
            outRect.top += offset
        }
    }

}