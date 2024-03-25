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
    private val adapter = Adapter { item ->
        if (item is Item.Wallet) {
            pickerViewModel.setActiveWallet(item.walletId)
        } else if (item is Item.AddWallet) {
            navigation?.add(AddScreen.newInstance())
        }
        finish()
    }

    private lateinit var listView: RecyclerView
    private lateinit var skeletonView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val headerView = view.findViewById<ModalHeader>(R.id.header)
        headerView.onCloseClick = ::finish

        skeletonView = view.findViewById(R.id.skeleton)

        listView = view.findViewById<SimpleRecyclerView>(R.id.list)
        listView.adapter = adapter
        collectFlow(listView.topScrolled, headerView::setDivider)

        collectFlow(pickerViewModel.itemsFlow, ::setItems)
    }

    private fun setItems(items: List<Item>) {
        adapter.submitList(items) {
            skeletonView.visibility = View.GONE
            listView.visibility = View.VISIBLE
            HapticHelper.selection(requireContext())
        }
    }

    companion object {
        fun newInstance() = PickerScreen()
    }
}