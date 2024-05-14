package com.tonapps.tonkeeper.fragment.stake

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import com.tonapps.tonkeeper.extensions.doOnAmountChange
import com.tonapps.tonkeeper.fragment.send.view.AmountInput
import com.tonapps.tonkeeperx.R
import core.extensions.observeFlow
import uikit.base.BaseFragment
import uikit.widget.HeaderView
import org.koin.androidx.viewmodel.ext.android.viewModel

class StakeFragment : BaseFragment(R.layout.fragment_stake), BaseFragment.BottomSheet {

    companion object {
        fun newInstance() = StakeFragment()
    }

    private val viewModel: StakeViewModel by viewModel()
    private val header: HeaderView?
        get() = view?.findViewById(R.id.fragment_stake_header)
    private val input: AmountInput?
        get() = view?.findViewById(R.id.fragment_stake_input)
    private val fiatTextView: TextView?
        get() = view?.findViewById(R.id.fragment_stake_fiat)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        header?.setIcon(R.drawable.ic_info_16)
        header?.doOnActionClick = { viewModel.onCloseClicked() }
        header?.doOnCloseClick = { viewModel.onInfoClicked() }

        input?.doOnAmountChange { viewModel.onAmountChanged(it) }

        observeFlow(viewModel.events, ::handleEvent)
        observeFlow(viewModel.fiatAmount) { fiatTextView?.text = it }
    }

    private fun handleEvent(event: StakeEvent) {
        when (event) {
            StakeEvent.NavigateBack -> finish()
            StakeEvent.ShowInfo -> Log.wtf("###", "showInfo")
        }
    }
}