package com.tonapps.tonkeeper.ui.screen.staking.unstake.amount

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.base.BaseHolderWalletScreen
import com.tonapps.tonkeeper.ui.component.coin.CoinEditText
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingViewModel
import com.tonapps.tonkeeper.ui.screen.staking.unstake.UnStakeScreen
import com.tonapps.tonkeeper.ui.screen.staking.unstake.UnStakeViewModel
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentRedColor
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.wallet.localization.Localization
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.extensions.focusWithKeyboard
import uikit.extensions.hideKeyboard
import uikit.widget.HeaderView

class UnStakeAmountFragment: BaseHolderWalletScreen.ChildFragment<UnStakeScreen, UnStakeViewModel>(R.layout.fragment_unstake_amount) {

    private lateinit var amountView: CoinEditText
    private lateinit var availableView: AppCompatTextView
    private lateinit var currencyView: AppCompatTextView
    private lateinit var cycleView: AppCompatTextView
    private lateinit var button: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val headerView = view.findViewById<HeaderView>(R.id.header)
        headerView.doOnActionClick = { finish() }

        amountView = view.findViewById(R.id.unstake_amount)
        amountView.suffix = "TON"
        amountView.doOnValueChange = primaryViewModel::updateAmount

        currencyView = view.findViewById(R.id.unstake_currency)

        cycleView = view.findViewById(R.id.cycle)

        availableView = view.findViewById(R.id.available)

        button = view.findViewById(R.id.next_button)
        button.setOnClickListener { primaryViewModel.confirm() }

        view.findViewById<View>(R.id.max).setOnClickListener { applyMax() }

        collectFlow(primaryViewModel.availableUiStateFlow, ::applyAvailableState)
        collectFlow(primaryViewModel.cycleEndFormatFlow) { date ->
            cycleView.text = getString(Localization.staking_unstake_cycle, date)
        }
    }

    private fun applyAvailableState(state: UnStakeViewModel.AvailableUiState) {
        if (state.insufficientBalance) {
            availableView.setText(Localization.insufficient_balance)
            availableView.setTextColor(requireContext().accentRedColor)
            button.isEnabled = false
        } else if (state.remainingFormat == state.balanceFormat) {
            availableView.text = getString(Localization.available_balance, state.balanceFormat)
            availableView.setTextColor(requireContext().textSecondaryColor)
            button.isEnabled = false
        } else {
            availableView.text = getString(Localization.remaining_balance, state.remainingFormat)
            availableView.setTextColor(requireContext().textSecondaryColor)
            button.isEnabled = true
        }

        currencyView.text = state.fiatFormat
    }

    private fun applyMax() {
        collectFlow(primaryViewModel.requestMax()) {
            amountView.setValue(it)
        }
    }

    override fun onVisibleState(visible: Boolean) {
        super.onVisibleState(visible)
        if (visible) {
            amountView.focusWithKeyboard()
        } else {
            amountView.hideKeyboard()
        }
    }

    override fun onKeyboardAnimation(offset: Int, progress: Float, isShowing: Boolean) {
        super.onKeyboardAnimation(offset, progress, isShowing)
        button.translationY = -offset.toFloat()
    }

    override fun getTitle() = requireContext().getString(Localization.unstake)

    companion object {

        fun newInstance() = UnStakeAmountFragment()
    }
}