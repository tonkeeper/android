package uikit.widget

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.uikit.list.LinearLayoutManager
import uikit.R
import uikit.extensions.useAttributes

open class SimpleRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RecyclerView(context, attrs, defStyle) {

    private var maxHeight: Int = 0

    init {
        layoutManager = LinearLayoutManager(context, VERTICAL, false)
        context.useAttributes(attrs, R.styleable.SimpleRecyclerView) {
            maxHeight = it.getDimensionPixelSize(R.styleable.SimpleRecyclerView_android_maxHeight, 0)
        }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        if (maxHeight in 1..<measuredHeight) {
            val width = measuredWidth
            val height = maxHeight
            setMeasuredDimension(width, height)
        }
    }
}