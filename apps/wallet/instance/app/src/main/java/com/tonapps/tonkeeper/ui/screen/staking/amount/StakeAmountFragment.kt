package com.tonapps.tonkeeper.ui.screen.staking.amount

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.FragmentOnAttachListener
import com.tonapps.tonkeeper.ui.component.coin.CoinEditText
import com.tonapps.tonkeeper.ui.screen.staking.StakingViewModel
import com.tonapps.tonkeeper.ui.screen.staking.main.StakeChildFragment
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentGreenColor
import com.tonapps.uikit.color.accentRedColor
import com.tonapps.uikit.color.stateList
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.wallet.data.staking.StakingPool
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity
import com.tonapps.wallet.localization.Localization
import uikit.extensions.collectFlow
import uikit.extensions.focusWithKeyboard
import uikit.extensions.hideKeyboard
import uikit.extensions.withAlpha
import uikit.widget.FrescoView

class StakeAmountFragment: StakeChildFragment(R.layout.fragment_stake_amount) {

    private lateinit var amountView: CoinEditText
    private lateinit var poolItemView: View
    private lateinit var poolIconView: FrescoView
    private lateinit var poolTitleView: AppCompatTextView
    private lateinit var poolMaxApyView: View
    private lateinit var poolDescriptionView: AppCompatTextView
    private lateinit var availableView: AppCompatTextView
    private lateinit var currencyView: AppCompatTextView
    private lateinit var button: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        amountView = view.findViewById(R.id.stake_amount)
        amountView.suffix = "TON"
        amountView.doOnValueChange = stakeViewModel::updateAmount

        currencyView = view.findViewById(R.id.stake_currency)

        poolItemView = view.findViewById(R.id.pool_item)
        poolItemView.setOnClickListener { stakeViewModel.openOptions() }

        poolIconView = view.findViewById(R.id.pool_icon)
        poolIconView.setCircular()

        poolTitleView = view.findViewById(R.id.pool_name)
        poolMaxApyView = view.findViewById(R.id.pool_max_apy)
        poolMaxApyView.backgroundTintList = requireContext().accentGreenColor.withAlpha(.16f).stateList

        poolDescriptionView = view.findViewById(R.id.pool_description)

        availableView = view.findViewById(R.id.available)

        button = view.findViewById(R.id.next_button)
        button.setOnClickListener { stakeViewModel.confirm() }

        collectFlow(stakeViewModel.selectedPoolFlow, ::applyPoolInfo)
        collectFlow(stakeViewModel.availableUiStateFlow, ::applyAvailableState)
        collectFlow(stakeViewModel.fiatFormatFlow, ::applyFiat)
        collectFlow(stakeViewModel.apyFormatFlow, poolDescriptionView::setText)
    }

    override fun onKeyboardAnimation(offset: Int, progress: Float, isShowing: Boolean) {
        super.onKeyboardAnimation(offset, progress, isShowing)
        button.translationY = -offset.toFloat()
    }

    override fun onVisibleState(visible: Boolean) {
        super.onVisibleState(visible)
        if (visible) {
            amountView.focusWithKeyboard()
        } else {
            amountView.hideKeyboard()
        }
    }

    override fun getTitle() = getString(Localization.stake)

    private fun applyFiat(fiatFormat: CharSequence) {
        currencyView.text = fiatFormat
    }

    private fun applyAvailableState(state: StakingViewModel.AvailableUiState) {
        if (state.insufficientBalance) {
            availableView.setText(Localization.insufficient_balance)
            availableView.setTextColor(requireContext().accentRedColor)
            button.isEnabled = false
        } else if (state.remainingFormat == state.balanceFormat) {
            availableView.text = getString(Localization.available_balance, state.balanceFormat)
            availableView.setTextColor(requireContext().textSecondaryColor)
            button.isEnabled = false
        } else if (state.requestMinStake) {
            availableView.text = getString(Localization.staking_minimum_deposit, state.minStakeFormat)
            availableView.setTextColor(requireContext().textSecondaryColor)
            button.isEnabled = false
        } else {
            availableView.text = getString(Localization.remaining_balance, state.remainingFormat)
            availableView.setTextColor(requireContext().textSecondaryColor)
            button.isEnabled = true
        }
    }

    private fun applyPoolInfo(poolInfo: PoolInfoEntity) {
        poolIconView.setLocalRes(StakingPool.getIcon(poolInfo.implementation))
        poolTitleView.setText(StakingPool.getTitle(poolInfo.implementation))
    }

    companion object {
        fun newInstance() = StakeAmountFragment()
    }
}