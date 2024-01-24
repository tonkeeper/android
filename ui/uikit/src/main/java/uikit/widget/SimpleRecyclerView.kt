package uikit.widget

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import uikit.list.LinearLayoutManager

class SimpleRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RecyclerView(context, attrs, defStyle) {

    init {
        layoutManager = LinearLayoutManager(context, VERTICAL, false)
    }
}