package com.tonapps.tonkeeper.ui.screen.staking.stake

import android.os.Bundle
import android.view.View
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseHolderWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.staking.stake.amount.StakeAmountFragment
import com.tonapps.blockchain.model.legacy.WalletEntity
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.hideKeyboard

class StakingScreen(wallet: WalletEntity) :
    BaseHolderWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)),
    BaseFragment.BottomSheet {

    override val fragmentName: String = "StakingScreen"

    private val poolAddress: String by lazy { arguments?.getString(POOL_ADDRESS_KEY) ?: "" }

    private val from: String by lazy { arguments?.getString(FROM_KEY) ?: "" }

    private val utm: UtmParameters by lazy {
        arguments?.getParcelableCompat<UtmParameters>(UTM_KEY) ?: UtmParameters.Empty
    }

    override val viewModel: StakingViewModel by walletViewModel { parametersOf(poolAddress, from, utm) }

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
        private const val FROM_KEY = "from"
        private const val UTM_KEY = "utm"

        fun newInstance(
            wallet: WalletEntity,
            from: String,
            poolAddress: String? = null,
            utm: UtmParameters = UtmParameters.Empty,
        ): BaseFragment {
            val fragment = StakingScreen(wallet)
            fragment.putStringArg(POOL_ADDRESS_KEY, poolAddress)
            fragment.putStringArg(FROM_KEY, from)
            fragment.putParcelableArg(UTM_KEY, utm)
            return fragment
        }
    }
}
