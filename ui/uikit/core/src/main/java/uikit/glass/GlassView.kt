package uikit.glass

import android.os.Build
import android.view.View
import androidx.annotation.ColorInt
import com.tonapps.uikit.color.GlassSpec
import com.tonapps.uikit.color.GlassStyle
import com.tonapps.uikit.color.glassShadowColor
import uikit.drawable.GlassDrawable
import uikit.extensions.dp

fun View.applyGlass(style: GlassStyle, @ColorInt tint: Int? = null) {
    background = GlassDrawable(context, style, tint)
    if (style == GlassStyle.Prominent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        elevation = GlassSpec.PROMINENT_ELEVATION_DP.dp
        outlineSpotShadowColor = context.glassShadowColor
        outlineAmbientShadowColor = context.glassShadowColor
    } else {
        elevation = 0f
    }
}
