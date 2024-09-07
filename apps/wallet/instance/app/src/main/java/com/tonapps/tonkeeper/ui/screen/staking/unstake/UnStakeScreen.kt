package com.tonapps.tonkeeper.ui.screen.staking.unstake

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.ui.base.BaseHolderWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.staking.unstake.amount.UnStakeAmountFragment
import com.tonapps.tonkeeper.ui.screen.staking.unstake.confirm.UnStakeConfirmFragment
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.hideKeyboard

class UnStakeScreen: BaseHolderWalletScreen<ScreenContext.None>(ScreenContext.None), BaseFragment.BottomSheet {

    private val poolAddress: String by lazy { arguments?.getString(POOL_ADDRESS_KEY) ?:"" }

    override val viewModel: UnStakeViewModel by viewModel { parametersOf(poolAddress) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectFlow(viewModel.eventFlow, ::onEvent)
    }

    private fun onEvent(event: UnStakeEvent) {
        when(event) {
            is UnStakeEvent.RouteToAmount -> setFragment(UnStakeAmountFragment.newInstance())
            is UnStakeEvent.OpenConfirm -> setFragment(UnStakeConfirmFragment.newInstance())
            is UnStakeEvent.Finish -> {
                navigation?.openURL("tonkeeper://activity")
                finish()
            }
        }
    }

    override fun onDragging() {
        super.onDragging()
        requireContext().hideKeyboard()
    }

    companion object {

        private const val POOL_ADDRESS_KEY = "pool_address"

        fun newInstance(poolAddress: String): UnStakeScreen {
            val fragment = UnStakeScreen()
            fragment.arguments = Bundle().apply {
                putString(POOL_ADDRESS_KEY, poolAddress)
            }
            return fragment
        }

    }

}