package uikit.utils

import androidx.recyclerview.widget.RecyclerView

abstract class RecyclerVerticalScrollListener: RecyclerView.OnScrollListener() {

    private var isAttached = false

    abstract fun onScrolled(recyclerView: RecyclerView, verticalScrollOffset: Int)

    private fun scrolled(recyclerView: RecyclerView) {
        if (!isAttached) {
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
        isAttached = true
        scrolled(recyclerView)
    }

    fun detach() {
        isAttached = false

    }
}