package com.tonapps.tonkeeper.ui.screen.picker

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.fragment.root.RootViewModel
import com.tonapps.tonkeeper.ui.screen.add.AddFragment
import com.tonapps.tonkeeper.ui.screen.picker.list.WalletPickerAdapter
import com.tonapps.tonkeeper.ui.screen.picker.list.WalletPickerItem
import com.tonapps.tonkeeperx.R
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import ton.wallet.Wallet
import uikit.base.BaseFragment
import uikit.drawable.FooterDrawable
import uikit.extensions.bottomScrolled
import uikit.extensions.collectFlow
import uikit.extensions.topScrolled
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.ModalHeader
import uikit.widget.ModalView
import uikit.widget.SimpleRecyclerView

class WalletPickerFragment: BaseFragment(R.layout.fragment_wallet_picker), BaseFragment.Modal {

    companion object {
        fun newInstance() = WalletPickerFragment()
    }

    private val rootViewModel: RootViewModel by activityViewModel()
    private val pickerViewModel: WalletPickerViewModel by viewModel()
    private val adapter = WalletPickerAdapter(::selectWallet)

    private lateinit var listView: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val headerView = view.findViewById<ModalHeader>(R.id.header)
        headerView.onCloseClick = ::finish

        val footerDrawable = FooterDrawable(requireContext())
        val footerView = view.findViewById<View>(R.id.footer)
        footerView.background = footerDrawable

        listView = view.findViewById<SimpleRecyclerView>(R.id.list)
        listView.adapter = adapter
        collectFlow(listView.topScrolled, headerView::setDivider)
        collectFlow(listView.bottomScrolled, footerDrawable::setDivider)

        val addButton = view.findViewById<View>(R.id.add)
        addButton.setOnClickListener {
            navigation?.add(AddFragment.newInstance())
        }

        collectFlow(pickerViewModel.walletsFlow, ::setItems)
    }

    private fun setItems(items: List<WalletPickerItem>) {
        val index = items.indexOfFirst { item -> item.selected }
        adapter.submitList(items) {
            if (index != -1) {
                listView.scrollToPosition(index)
            }
        }
    }

    private fun selectWallet(wallet: Wallet) {
        collectFlow(pickerViewModel.setWallet(wallet)) { reload ->
            if (reload) {
                notifyChangeWallet()
            }
            finish()
        }
    }

    private fun notifyChangeWallet() {
        postDelayed(ModalView.animationDuration) {
            rootViewModel.notifyChangeWallet()
        }
    }
}