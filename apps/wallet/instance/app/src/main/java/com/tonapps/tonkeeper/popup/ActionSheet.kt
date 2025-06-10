package com.tonapps.tonkeeper.popup

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.PopupWindow
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.backgroundContentTintColor
import com.tonapps.uikit.list.ListCell
import uikit.extensions.dp
import uikit.extensions.drawable
import uikit.extensions.findViewByClass
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.getDrawable
import uikit.extensions.getViews
import uikit.extensions.inflate
import uikit.extensions.round
import uikit.extensions.window
import uikit.widget.FrescoView

open class ActionSheet(
    val context: Context
) : PopupWindow() {

    companion object {
        private val singleLineItemHeight = 48.dp
        private val subtitleLineItemHeight = 68.dp
    }

    data class Item(
        val id: Long,
        val title: CharSequence,
        val subtitle: CharSequence?,
        val icon: Drawable?,
        val imageUri: Uri?,
        val imageTintColor: Int?,
        val onClick: ((Item) -> Unit)? = null
    )

    private val container: LinearLayoutCompat
    private val items = mutableListOf<Item>()

    var doOnItemClick: ((Item) -> Unit)? = null

    val isEmpty: Boolean
        get() = items.isEmpty()

    open val maxHeight = 220.dp

    init {
        contentView = context.inflate(R.layout.action_sheet_base)
        contentView.round(context.getDimensionPixelSize(uikit.R.dimen.cornerMedium))
        container = contentView.findViewById(R.id.action_sheet_content)
        width = 220.dp
        isOutsideTouchable = true
    }

    fun clearItems() {
        items.clear()
    }

    fun addItem(id: Long, titleRes: Int, iconRes: Int = 0, imageUri: Uri? = null) {
        val drawable = if (iconRes == 0) {
            null
        } else {
            container.getDrawable(iconRes)
        }
        addItem(id, context.getString(titleRes), null, drawable, imageUri)
    }

    fun addItem(
        id: Long,
        title: CharSequence,
        subtitle: CharSequence? = null,
        icon: Drawable? = null,
        imageUri: Uri? = null,
        imageTintColor: Int? = null,
        onClick: ((Item) -> Unit)? = null
    ) {
        val index = items.indexOfFirst { it.id == id }
        if (index == -1) {
            items.add(Item(id, title, subtitle, icon, imageUri, imageTintColor, onClick))
        }
    }

    fun show(
        target: View,
        offsetX: Int = 0,
        gravity: Int = Gravity.TOP or Gravity.START
    ) {
        if (isShowing) {
            dismiss()
        } else {
            buildView()
            showAsDropDown(target, offsetX, 8.dp, gravity)
        }
    }

    fun showPopupAboveRight(target: View) {
        if (isShowing) {
            dismiss()
            return
        }

        buildView()

        val marginBottom = 8.dp

        contentView.measure(
            View.MeasureSpec.UNSPECIFIED,
            View.MeasureSpec.UNSPECIFIED
        )
        val popupHeight = contentView.measuredHeight

        val x = target.width - width
        val y = -popupHeight - marginBottom

        showAsDropDown(target, x, y, Gravity.NO_GRAVITY)
    }

    private fun buildView() {
        container.removeAllViews()

        val backgroundColor = context.backgroundContentTintColor
        var popupHeight = 0

        for ((index, item) in items.withIndex()) {
            val position = ListCell.getPosition(items.size, index)
            val itemView = createItemView(item)
            itemView.setOnClickListener {
                item.onClick?.invoke(item)
                doOnItemClick?.invoke(item)
                dismiss()
            }
            itemView.background = position.drawable(context, backgroundColor)
            val itemHeight = if (item.subtitle == null) {
                singleLineItemHeight
            } else {
                subtitleLineItemHeight
            }
            container.addView(
                itemView,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemHeight)
            )
            popupHeight += itemHeight
        }

        if (popupHeight > maxHeight) {
            popupHeight = maxHeight
        }

        height = popupHeight
    }

    private fun createItemView(item: Item): View {
        val itemView = context.inflate(R.layout.view_action_sheet_item)
        val titleView = itemView.findViewById<AppCompatTextView>(R.id.title)
        val subtitleView = itemView.findViewById<AppCompatTextView>(R.id.subtitle)
        val iconView = itemView.findViewById<AppCompatImageView>(R.id.icon)
        val imageView = itemView.findViewById<FrescoView>(R.id.image)

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

        if (item.imageUri == null) {
            imageView.visibility = View.GONE
        } else {
            imageView.visibility = View.VISIBLE
            imageView.setImageURI(item.imageUri)
        }

        if (item.imageTintColor != null) {
            imageView.colorFilter =
                PorterDuffColorFilter(item.imageTintColor, PorterDuff.Mode.SRC_IN)
        }

        return itemView
    }

}