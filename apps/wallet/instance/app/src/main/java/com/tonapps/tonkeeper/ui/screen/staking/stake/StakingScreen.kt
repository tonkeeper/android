package com.tonapps.tonkeeper.ui.screen.staking.stake

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.ui.base.BaseHolderWalletScreen
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.screen.staking.stake.amount.StakeAmountFragment
import com.tonapps.tonkeeper.ui.screen.staking.stake.confirm.StakeConfirmFragment
import com.tonapps.tonkeeper.ui.screen.staking.stake.details.StakeDetailsFragment
import com.tonapps.tonkeeper.ui.screen.staking.stake.options.StakeOptionsFragment
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.base.SimpleFragment
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.extensions.hideKeyboard
import uikit.widget.HeaderView

class StakingScreen: BaseHolderWalletScreen(), BaseFragment.BottomSheet {

    private val poolAddress: String by lazy { arguments?.getString(POOL_ADDRESS_KEY) ?:"" }

    override val viewModel: StakingViewModel by viewModel { parametersOf(poolAddress) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnalyticsHelper.trackEvent("staking_open")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectFlow(viewModel.eventFlow, ::onEvent)
    }

    override fun onDragging() {
        super.onDragging()
        requireContext().hideKeyboard()
    }

    private fun onEvent(event: StakingEvent) {
        when(event) {
            is StakingEvent.OpenAmount -> setFragment(StakeAmountFragment.newInstance())
            is StakingEvent.OpenOptions -> setFragment(StakeOptionsFragment.newInstance())
            is StakingEvent.OpenDetails -> setFragment(StakeDetailsFragment.newInstance(event.pool))
            is StakingEvent.OpenConfirm -> setFragment(StakeConfirmFragment.newInstance())
            is StakingEvent.Finish -> {
                navigation?.openURL("tonkeeper://activity")
                finish()
            }
        }
    }

    companion object {

        private const val POOL_ADDRESS_KEY = "pool_address"

        fun newInstance(poolAddress: String? = null): StakingScreen {
            val fragment = StakingScreen()
            fragment.arguments = Bundle().apply {
                putString(POOL_ADDRESS_KEY, poolAddress)
            }
            return fragment
        }
    }
}