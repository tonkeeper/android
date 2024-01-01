package uikit.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import uikit.drawable.DotDrawable
import uikit.extensions.dp

class DotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    init {
        background = DotDrawable(context)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(DotDrawable.size, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(DotDrawable.size, MeasureSpec.EXACTLY))
    }

    override fun hasOverlappingRendering() = false
}
