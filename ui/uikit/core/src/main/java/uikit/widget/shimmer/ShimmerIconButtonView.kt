package uikit.widget.shimmer

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import uikit.R

class ShimmerIconButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    init {
        inflate(context, R.layout.shimmer_icon_button, this)
    }
}