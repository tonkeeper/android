package uikit.list

import androidx.recyclerview.widget.RecyclerView

abstract class ListPaginationListener: RecyclerView.OnScrollListener() {

    abstract fun onLoadMore()

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
        val totalItemCount = layoutManager.itemCount
        if (lastVisibleItemPosition == totalItemCount - 1) {
            onLoadMore()
        }
    }

}