package com.tonapps.tonkeeper.ui.screen.init.step

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.tonkeeper.ui.screen.init.InitEvent
import com.tonapps.tonkeeper.ui.screen.init.InitRoute
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeper.ui.screen.init.list.AccountItem
import com.tonapps.tonkeeper.ui.screen.init.list.Adapter
import com.tonapps.tonkeeperx.R
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.applyNavBottomPadding
import uikit.extensions.collectFlow
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.pinToBottomInsets

class SelectScreen: BaseFragment(R.layout.fragment_init_select) {

    private val initViewModel: InitViewModel by viewModel(ownerProducer = { requireParentFragment() })

    private val adapter = Adapter { account, checked ->
        initViewModel.toggleAccountSelection(account.address.toRawAddress(), checked)
    }

    private lateinit var listView: RecyclerView
    private lateinit var button: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView = view.findViewById(R.id.accounts)
        listView.adapter = adapter
        listView.applyNavBottomPadding(requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium))

        button = view.findViewById(R.id.button)
        button.setOnClickListener {
            initViewModel.nextStep(requireContext(), InitRoute.SelectAccount)
        }

        collectFlow(initViewModel.uiTopOffset) {
            view.updatePadding(top = it)
        }

        collectFlow(initViewModel.accountsFlow, ::setItems)

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val insetsNav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val bottom = insetsNav + requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium)
            listView.updatePadding(bottom = bottom)
            button.translationY = -bottom.toFloat()
            insets
        }
    }

    private fun setItems(items: List<AccountItem>) {
        adapter.submitList(items)
        button.isEnabled = items.count { it.selected } > 0
    }

    companion object {
        fun newInstance() = SelectScreen()
    }
}