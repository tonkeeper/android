package uikit.widget.shimmer

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.LinearLayoutCompat
import uikit.R
import uikit.extensions.dp

class ShimmerIconButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayoutCompat(context, attrs, defStyle) {

    init {
        orientation = VERTICAL
        inflate(context, R.layout.shimmer_icon_button, this)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(72.dp, MeasureSpec.EXACTLY), heightMeasureSpec)
    }
}