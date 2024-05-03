package com.tonapps.tonkeeper.ui.screen.picker

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.screen.add.AddScreen
import com.tonapps.tonkeeper.ui.screen.picker.list.WalletPickerAdapter
import com.tonapps.tonkeeper.ui.screen.picker.list.Item
import com.tonapps.tonkeeperx.R
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.HapticHelper
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.topScrolled
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.ModalHeader
import uikit.widget.SimpleRecyclerView

class PickerScreen: BaseFragment(R.layout.fragment_wallet_picker), BaseFragment.Modal {

    override val scaleBackground: Boolean = true

    private val pickerViewModel: PickerViewModel by viewModel()
    private val adapter: WalletPickerAdapter by inject()

    private lateinit var listView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(pickerViewModel.walletChangedFlow) {
            finish()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val headerView = view.findViewById<ModalHeader>(R.id.header)
        headerView.onCloseClick = ::finish

        listView = view.findViewById<SimpleRecyclerView>(R.id.list)
        listView.adapter = adapter
        collectFlow(listView.topScrolled, headerView::setDivider)
    }

    companion object {
        fun newInstance() = PickerScreen()
    }
}