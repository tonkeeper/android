package com.tonapps.tonkeeper.ui.base

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.uikit.icon.UIKitIcon
import uikit.R
import uikit.extensions.applyNavBottomPadding
import uikit.extensions.collectFlow
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.topScrolled
import uikit.widget.HeaderView
import uikit.widget.SimpleRecyclerView

abstract class BaseListWalletScreen<C: ScreenContext>(
    screenContext: C
): BaseWalletScreen<C>(
    layoutId = R.layout.fragment_list,
    screenContext = screenContext
) {

    protected lateinit var headerContainer: FrameLayout
    protected lateinit var headerView: HeaderView
    protected lateinit var listView: SimpleRecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerContainer = view.findViewById(R.id.header_container)

        headerView = view.findViewById(R.id.header)
        listView = view.findViewById(R.id.list)

        if (this is SwipeBack) {
            headerView.setIcon(UIKitIcon.ic_chevron_left_16)
            headerView.doOnCloseClick = { finish() }
        } else if (this is BottomSheet || this is Modal) {
            headerView.ignoreSystemOffset = true
            headerView.setAction(UIKitIcon.ic_close_16)
            headerView.doOnActionClick = { finish() }
            listView.insideBottomSheet = true
        } else if (parentFragment != null) {
            headerView.ignoreSystemOffset = true
        } else {
            headerView.ignoreSystemOffset = false
        }

        listView.applyNavBottomPadding(listView.paddingBottom)
        collectFlow(listView.topScrolled, headerView::setDivider)
    }

    fun addViewHeader(view: View, params: FrameLayout.LayoutParams? = null) {
        headerContainer.addView(view, params)
    }

    fun findListItemView(position: Int): View? {
        return listView.findViewHolderForAdapterPosition(position)?.itemView
    }

    fun setAdapter(adapter: RecyclerView.Adapter<*>) {
        listView.adapter = adapter
    }

    fun addItemDecoration(decoration: RecyclerView.ItemDecoration) {
        listView.addItemDecoration(decoration)
    }

    fun addScrollListener(listener: RecyclerView.OnScrollListener) {
        listView.addOnScrollListener(listener)
    }

    fun setTitle(title: String) {
        headerView.title = title
    }

    fun setTouchHelperCallback(callback: ItemTouchHelper.SimpleCallback) {
        listView.setTouchHelper(ItemTouchHelper(callback))
    }

    fun getTouchHelper() = listView.getTouchHelper()

    fun setListPadding(left: Int, top: Int, right: Int, bottom: Int) {
        listView.setPadding(left, top, right, bottom)
    }

    fun updateListPadding(
        left: Int = listView.paddingLeft,
        top: Int = listView.paddingTop,
        right: Int = listView.paddingRight,
        bottom: Int = listView.paddingBottom
    ) {
        listView.updatePadding(left, top, right, bottom)
    }

    fun setActionIcon(@DrawableRes resId: Int, onClick: (view: View) -> Unit) {
        headerView.setAction(resId)
        headerView.doOnActionClick = onClick
    }

    fun setCloseIcon(@DrawableRes resId: Int, onClick: () -> Unit) {
        headerView.setIcon(resId)
        headerView.doOnCloseClick = onClick
    }

    fun setHeaderBackground(@DrawableRes resId: Int) {
        headerView.setBackgroundResource(resId)
    }

    fun setLayoutManager(layoutManager: RecyclerView.LayoutManager) {
        listView.layoutManager = layoutManager
    }

}