package uikit.popup

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import uikit.R
import uikit.drawable.CellBackgroundDrawable
import uikit.extensions.dp
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.getDrawable
import uikit.extensions.inflate
import uikit.list.ListCell

open class ActionSheet(
    val context: Context
): PopupWindow() {

    private companion object {
        private val singleLineItemHeight = 48.dp
        private val subtitleLineItemHeight = 68.dp
    }

    data class Item(
        val id: Long,
        val title: String,
        val subtitle: String?,
        val icon: Drawable?
    )

    private val container = LinearLayoutCompat(context).apply {
        orientation = LinearLayoutCompat.VERTICAL
    }

    private val items = mutableListOf<Item>()

    var doOnItemClick: ((Item) -> Unit)? = null

    init {
        contentView = container
        width = 196.dp
        isOutsideTouchable = true
    }

    fun addItem(id: Long, titleRes: Int, iconRes: Int) {
        val drawable = if (iconRes == 0) {
            null
        } else {
            container.getDrawable(iconRes)
        }
        addItem(id, context.getString(titleRes), null, drawable)
    }

    fun addItem(id: Long, title: String, subtitle: String?, icon: Drawable?) {
        val index = items.indexOfFirst { it.id == id }
        if (index == -1) {
            items.add(Item(id, title, subtitle, icon))
        }
    }

    fun show(target: View) {
        if (isShowing) {
            dismiss()
            return
        }

        buildView()
        val xoff = (target.width - width) / 2
        showAsDropDown(target, xoff, 8.dp)
    }

    private fun buildView() {
        container.removeAllViews()
        height = 0

        val backgroundColor = context.getColor(R.color.backgroundContentTint)

        for ((index, item) in items.withIndex()) {
            val position = ListCell.getPosition(items.size, index)
            val itemView = createItemView(item)
            itemView.setOnClickListener {
                doOnItemClick?.invoke(item)
                dismiss()
            }
            itemView.background = CellBackgroundDrawable(context, position, backgroundColor)
            val itemHeight = if (item.subtitle == null) {
                singleLineItemHeight
            } else {
                subtitleLineItemHeight
            }
            container.addView(itemView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemHeight))
            height += itemHeight
        }
    }

    private fun createItemView(item: Item): View {
        val itemView = context.inflate(R.layout.view_action_sheet_item)
        val titleView = itemView.findViewById<AppCompatTextView>(R.id.title)
        val subtitleView = itemView.findViewById<AppCompatTextView>(R.id.subtitle)
        val iconView = itemView.findViewById<AppCompatImageView>(R.id.icon)

        titleView.text = item.title
        if (item.icon == null) {
            iconView.visibility = View.GONE
        } else {
            iconView.visibility = View.VISIBLE
            iconView.setImageDrawable(item.icon)
        }


        if (item.subtitle == null) {
            subtitleView.visibility = View.GONE
        } else {
            subtitleView.visibility = View.VISIBLE
            subtitleView.text = item.subtitle
        }
        return itemView
    }

}