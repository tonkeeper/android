package com.tonapps.tonkeeper.ui.screen.picker

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.tonapps.tonkeeper.dialog.IntroWalletDialog
import com.tonapps.tonkeeper.extensions.launch
import com.tonapps.tonkeeper.ui.screen.picker.list.WalletPickerAdapter
import com.tonapps.tonkeeper.ui.screen.picker.list.WalletPickerItem
import com.tonapps.tonkeeperx.R
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import ton.wallet.Wallet
import uikit.base.BaseFragment
import uikit.drawable.FooterDrawable
import uikit.extensions.bottomScrolled
import uikit.extensions.collectFlow
import uikit.extensions.isMaxScrollReached
import uikit.extensions.topScrolled
import uikit.extensions.verticalOffset
import uikit.extensions.verticalScrolled
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.ModalHeader
import uikit.widget.SimpleRecyclerView

class WalletPickerFragment: BaseFragment(R.layout.fragment_wallet_picker), BaseFragment.Modal {

    companion object {
        fun newInstance() = WalletPickerFragment()
    }

    private val pickerViewModel: WalletPickerViewModel by viewModels()
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
            IntroWalletDialog(requireContext()).show()
            finish()
        }

        collectFlow(pickerViewModel.walletsFlow, ::setItems)
    }

    private fun setItems(items: List<WalletPickerItem>) {
        val index = items.indexOfFirst { item -> item.selected }
        adapter.submitList(items) {
            if (index != -1) {
                listView.scrollToPosition(index)
            }
            fixPeekHeight()
        }
    }

    private fun selectWallet(wallet: Wallet) {
        collectFlow(pickerViewModel.setWallet(wallet)) { reload ->
            if (reload) {
                navigation?.initRoot(true)
            }
            finish()
        }
    }
}