package com.tonapps.tonkeeper.ui.screen.staking.stake

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.ui.base.BaseHolderWalletScreen
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.staking.stake.amount.StakeAmountFragment
import com.tonapps.tonkeeper.ui.screen.staking.stake.confirm.StakeConfirmFragment
import com.tonapps.tonkeeper.ui.screen.staking.stake.details.StakeDetailsFragment
import com.tonapps.tonkeeper.ui.screen.staking.stake.options.StakeOptionsFragment
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.account.entities.WalletEntity
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.base.SimpleFragment
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.extensions.hideKeyboard
import uikit.widget.HeaderView

class StakingScreen(wallet: WalletEntity): BaseHolderWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)), BaseFragment.BottomSheet {

    private val poolAddress: String by lazy { arguments?.getString(POOL_ADDRESS_KEY) ?:"" }

    override val viewModel: StakingViewModel by viewModel {
        parametersOf(screenContext.wallet, poolAddress)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnalyticsHelper.trackEvent("staking_open")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setFragment(StakeAmountFragment.newInstance())
    }

    override fun onDragging() {
        super.onDragging()
        requireContext().hideKeyboard()
    }

    companion object {

        private const val POOL_ADDRESS_KEY = "pool_address"

        fun newInstance(wallet: WalletEntity, poolAddress: String? = null): StakingScreen {
            val fragment = StakingScreen(wallet)
            fragment.putStringArg(POOL_ADDRESS_KEY, poolAddress)
            return fragment
        }
    }
}