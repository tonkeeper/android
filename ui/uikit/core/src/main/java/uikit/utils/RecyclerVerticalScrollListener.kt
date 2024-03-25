package uikit.utils

import androidx.recyclerview.widget.RecyclerView

abstract class RecyclerVerticalScrollListener: RecyclerView.OnScrollListener() {

    private var recyclerViewRef: RecyclerView? = null

    abstract fun onScrolled(recyclerView: RecyclerView, verticalScrollOffset: Int)

    private fun scrolled(recyclerView: RecyclerView) {
        if (recyclerViewRef == null) {
            return
        }
        recyclerView.post {
            onScrolled(recyclerView, recyclerView.computeVerticalScrollOffset())
        }
    }

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        scrolled(recyclerView)
    }

    fun attach(recyclerView: RecyclerView) {
        if (recyclerViewRef == recyclerView) {
            return
        }

        if (recyclerViewRef != null) {
            detach()
        }

        recyclerViewRef = recyclerView
        recyclerView.addOnScrollListener(this)
        scrolled(recyclerView)
    }

    fun detach() {
        recyclerViewRef?.removeOnScrollListener(this)
        recyclerViewRef = null
    }
}