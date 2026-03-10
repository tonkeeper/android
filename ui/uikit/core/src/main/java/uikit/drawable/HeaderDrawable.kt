package uikit.drawable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.tonapps.uikit.color.separatorCommonColor
import uikit.base.BaseDrawable
import uikit.extensions.dp

class HeaderDrawable(context: Context): BarDrawable(context) {

    override val y: Float
        get() = bounds.bottom.toFloat()

}