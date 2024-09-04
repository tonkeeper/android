package com.tonapps.tonkeeper.ui.screen.staking.stake.amount

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.ui.base.BaseHolderWalletScreen
import com.tonapps.tonkeeper.ui.component.coin.CoinEditText
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingScreen
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingViewModel
import com.tonapps.tonkeeper.ui.screen.staking.unstake.UnStakeScreen
import com.tonapps.tonkeeper.ui.screen.staking.unstake.UnStakeViewModel
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentGreenColor
import com.tonapps.uikit.color.accentRedColor
import com.tonapps.uikit.color.stateList
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.staking.StakingPool
import com.tonapps.wallet.data.staking.entities.PoolEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.map
import uikit.extensions.collectFlow
import uikit.extensions.focusWithKeyboard
import uikit.extensions.hideKeyboard
import uikit.extensions.withAlpha
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.FrescoView
import uikit.widget.HeaderView

class StakeAmountFragment: BaseHolderWalletScreen.ChildFragment<StakingScreen, StakingViewModel>(R.layout.fragment_stake_amount) {

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
        val headerView = view.findViewById<HeaderView>(R.id.header)
        headerView.doOnCloseClick = { navigation?.openURL("https://ton.org/stake", true) }
        headerView.doOnActionClick = { finish() }

        amountView = view.findViewById(R.id.stake_amount)
        amountView.suffix = "TON"
        amountView.doOnValueChange = primaryViewModel::updateAmount

        currencyView = view.findViewById(R.id.stake_currency)

        poolItemView = view.findViewById(R.id.pool_item)
        poolItemView.setOnClickListener { primaryViewModel.openOptions() }

        poolIconView = view.findViewById(R.id.pool_icon)
        poolIconView.setCircular()

        poolTitleView = view.findViewById(R.id.pool_name)
        poolMaxApyView = view.findViewById(R.id.pool_max_apy)
        poolMaxApyView.backgroundTintList = requireContext().accentGreenColor.withAlpha(.16f).stateList

        poolDescriptionView = view.findViewById(R.id.pool_description)

        availableView = view.findViewById(R.id.available)

        button = view.findViewById(R.id.next_button)
        button.setOnClickListener { primaryViewModel.confirm() }

        view.findViewById<View>(R.id.max).setOnClickListener { applyMax() }

        collectFlow(primaryViewModel.selectedPoolFlow, ::applyPoolInfo)
        collectFlow(primaryViewModel.availableUiStateFlow, ::applyAvailableState)
        collectFlow(primaryViewModel.fiatFormatFlow, ::applyFiat)
        collectFlow(primaryViewModel.apyFormatFlow.map {
            it.withCustomSymbol(requireContext())
        }, poolDescriptionView::setText)
    }

    private fun applyMax() {
        collectFlow(primaryViewModel.requestMax()) {
            amountView.setValue(it.value)
        }
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

    override fun toString() = TAG

    private fun applyFiat(fiatFormat: CharSequence) {
        currencyView.text = fiatFormat.withCustomSymbol(requireContext())
    }

    private fun applyAvailableState(state: StakingViewModel.AvailableUiState) {
        if (state.insufficientBalance) {
            availableView.setText(Localization.insufficient_balance)
            availableView.setTextColor(requireContext().accentRedColor)
            button.isEnabled = false
        } else if (state.remainingFormat == state.balanceFormat) {
            availableView.text = if (state.hiddenBalance) HIDDEN_BALANCE else getString(Localization.available_balance, state.balanceFormat).withCustomSymbol(requireContext())
            availableView.setTextColor(requireContext().textSecondaryColor)
            button.isEnabled = false
        } else if (state.requestMinStake) {
            availableView.text = getString(Localization.minimum_amount, state.minStakeFormat).withCustomSymbol(requireContext())
            availableView.setTextColor(requireContext().accentRedColor)
            button.isEnabled = false
        } else {
            availableView.text = getString(Localization.remaining_balance, state.remainingFormat).withCustomSymbol(requireContext())
            availableView.setTextColor(requireContext().textSecondaryColor)
            button.isEnabled = true
        }
    }

    private fun applyPoolInfo(pool: PoolEntity) {
        poolIconView.setLocalRes(StakingPool.getIcon(pool.implementation))
        poolTitleView.setText(StakingPool.getTitle(pool.implementation))
        poolMaxApyView.visibility = if (pool.maxApy) View.VISIBLE else View.GONE
    }

    companion object {

        const val TAG = "stake_amount_fragment"

        fun newInstance() = StakeAmountFragment()
    }
}