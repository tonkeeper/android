package uikit.widget

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.uikit.list.LinearLayoutManager
import uikit.R
import uikit.extensions.useAttributes

open class SimpleRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RecyclerView(context, attrs, defStyle) {

    // private var isLayoutRequested = false

    var insideBottomSheet: Boolean = false
    var maxHeight: Int = 0
    var minHeight: Int = 0

    private val isRequestFixHeight: Boolean
        get() = hasFixedSize() && insideBottomSheet

    private var itemTouchHelper: ItemTouchHelper? = null

    init {
        layoutManager = LinearLayoutManager(context, VERTICAL, false)
        context.useAttributes(attrs, R.styleable.SimpleRecyclerView) {
            maxHeight = it.getDimensionPixelSize(R.styleable.SimpleRecyclerView_android_maxHeight, 0)
            minHeight = it.getDimensionPixelSize(R.styleable.SimpleRecyclerView_android_minHeight, 0)
        }
    }

    fun setTouchHelper(touchHelper: ItemTouchHelper?) {
        if (touchHelper == itemTouchHelper) {
            return
        }
        clearTouchHelper()
        itemTouchHelper = touchHelper
        itemTouchHelper?.attachToRecyclerView(this)
    }

    fun getTouchHelper() = itemTouchHelper

    private fun clearTouchHelper() {
        itemTouchHelper?.attachToRecyclerView(null)
        itemTouchHelper = null
    }

    fun setFixedHeight(newHeight: Int) {
        updateLayoutParams<ViewGroup.LayoutParams> {
            height = newHeight
        }
    }

    /*override fun requestLayout() {
        if (!isLayoutRequested) {
            isLayoutRequested = true
            post {
                isLayoutRequested = false
                super.requestLayout()
            }
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        isLayoutRequested = false
        super.onLayout(changed, l, t, r, b)
    }*/

    /*override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        if (maxHeight in 1..<measuredHeight) {
            Log.d("WalletsAdapterLog", "maxHeight=onMeasure(widthSpec=$widthSpec, heightSpec=$heightSpec)")
            val width = measuredWidth
            val height = maxHeight
            setMeasuredDimension(width, height)
        } else if (minHeight in 1..<measuredHeight) {
            Log.d("WalletsAdapterLog", "minHeight=onMeasure(widthSpec=$widthSpec, heightSpec=$heightSpec)")
            val width = measuredWidth
            val height = minHeight
            setMeasuredDimension(width, height)
        }
    }*/

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        if (maxHeight in 1..<measuredHeight) {
            val width = measuredWidth
            val height = maxHeight
            setMeasuredDimension(width, height)
        }
    }
}