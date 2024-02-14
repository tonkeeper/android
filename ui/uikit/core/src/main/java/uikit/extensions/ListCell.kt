package uikit.extensions

import android.content.Context
import android.graphics.drawable.Drawable
import com.tonapps.uikit.color.UIKitColor
import com.tonapps.uikit.list.ListCell
import uikit.drawable.CellBackgroundDrawable

fun ListCell.Position.drawable(
    context: Context,
    backgroundColor: Int = context.getColor(UIKitColor.backgroundContent)
): Drawable {
    return CellBackgroundDrawable.create(context, this, backgroundColor)
}