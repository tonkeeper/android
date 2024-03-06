package com.tonapps.tonkeeper.ui.screen.picker

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.screen.add.AddScreen
import com.tonapps.tonkeeper.ui.screen.picker.list.Adapter
import com.tonapps.tonkeeper.ui.screen.picker.list.Item
import com.tonapps.tonkeeperx.R
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.HapticHelper
import uikit.base.BaseFragment
import uikit.drawable.FooterDrawable
import uikit.extensions.bottomScrolled
import uikit.extensions.collectFlow
import uikit.extensions.topScrolled
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.ModalHeader
import uikit.widget.SimpleRecyclerView

class PickerScreen: BaseFragment(R.layout.fragment_wallet_picker), BaseFragment.Modal {

    private val pickerViewModel: PickerViewModel by viewModel()
    private val adapter = Adapter(::setActiveWallet)

    private lateinit var listView: RecyclerView
    private lateinit var skeletonView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val headerView = view.findViewById<ModalHeader>(R.id.header)
        headerView.onCloseClick = ::finish

        val footerDrawable = FooterDrawable(requireContext())
        val footerView = view.findViewById<View>(R.id.footer)
        footerView.background = footerDrawable

        skeletonView = view.findViewById<View>(R.id.skeleton)

        listView = view.findViewById<SimpleRecyclerView>(R.id.list)
        listView.adapter = adapter
        collectFlow(listView.topScrolled, headerView::setDivider)
        collectFlow(listView.bottomScrolled, footerDrawable::setDivider)

        val addButton = view.findViewById<View>(R.id.add)
        addButton.setOnClickListener {
            navigation?.add(AddScreen.newInstance())
        }

        collectFlow(pickerViewModel.itemsFlow, ::setItems)
    }

    private fun setActiveWallet(id: Long) {
        pickerViewModel.setActiveWallet(id)
        finish()
    }

    private fun setItems(items: List<Item>) {
        val index = items.indexOfFirst { item -> item.selected }
        adapter.submitList(items) {
            if (index != -1) {
                listView.scrollToPosition(index)
            }
            skeletonView.visibility = View.GONE
            listView.visibility = View.VISIBLE
            HapticHelper.selection(requireContext())
        }
    }

    companion object {
        fun newInstance() = PickerScreen()
    }
}