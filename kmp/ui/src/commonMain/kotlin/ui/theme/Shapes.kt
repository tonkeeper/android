package ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import ui.UiPosition

object Shapes {
    val large = RoundedCornerShape(Dimens.cornerLarge)
    val medium12 = RoundedCornerShape(12.dp)
    val medium = RoundedCornerShape(Dimens.cornerMedium)
    val none = RectangleShape

    val itemSingle = RoundedCornerShape(Dimens.cornerMedium)
    val itemStart = RoundedCornerShape(topStart = Dimens.cornerMedium, topEnd = Dimens.cornerMedium)
    val itemEnd = RoundedCornerShape(bottomStart = Dimens.cornerMedium, bottomEnd = Dimens.cornerMedium)

    fun UiPosition.shape() = when (this) {
        UiPosition.Single -> itemSingle
        UiPosition.Start -> itemStart
        UiPosition.Middle -> RectangleShape
        UiPosition.End -> itemEnd
    }
}
