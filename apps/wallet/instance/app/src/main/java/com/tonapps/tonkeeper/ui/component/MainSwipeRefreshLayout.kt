package com.tonapps.tonkeeper.ui.component

import android.content.Context
import android.util.AttributeSet
import androidx.core.view.doOnLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tonapps.uikit.color.accentBlueColor
import uikit.extensions.dp
import uikit.extensions.findViewByClass

class MainSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {

    private val recyclerView: MainRecyclerView? by lazy { findViewByClass() }

    private val topPadding: Int
        get() = recyclerView?.topPadding ?: 0

    init {
        setColorSchemeColors(context.accentBlueColor)
    }

    override fun setRefreshing(refreshing: Boolean) {
        if (refreshing) {
            doOnLayout { postOnAnimation { super.setRefreshing(true) } }
        } else {
            super.setRefreshing(false)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        setProgressViewOffset(true, topPadding, topPadding + 40.dp)
    }
}