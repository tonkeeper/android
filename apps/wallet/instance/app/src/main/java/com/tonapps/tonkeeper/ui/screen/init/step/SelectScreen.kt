package com.tonapps.tonkeeper.ui.screen.init.step

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.tonkeeper.ui.screen.init.InitEvent
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeper.ui.screen.init.list.AccountItem
import com.tonapps.tonkeeper.ui.screen.init.list.Adapter
import com.tonapps.tonkeeperx.R
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow

class SelectScreen: BaseFragment(R.layout.fragment_init_select) {

    private val initViewModel: InitViewModel by viewModel(ownerProducer = { requireParentFragment() })

    private val adapter = Adapter {
        initViewModel.toggleAccountSelection(it.address.toRawAddress())
    }

    private lateinit var listView: RecyclerView
    private lateinit var button: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView = view.findViewById(R.id.accounts)
        listView.adapter = adapter

        button = view.findViewById(R.id.button)
        button.setOnClickListener {
            initViewModel.nextStep(InitEvent.Step.SelectAccount)
        }

        collectFlow(initViewModel.uiTopOffset) {
            view.updatePadding(top = it)
        }

        collectFlow(initViewModel.accountsFlow, ::setItems)
    }

    private fun setItems(items: List<AccountItem>) {
        adapter.submitList(items)
        button.isEnabled = items.count { it.selected } > 0
    }

    companion object {
        fun newInstance() = SelectScreen()
    }
}