package com.tonapps.tonkeeper.ui.screen.staking.stake.pool

import android.os.Bundle
import android.view.View
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.ui.base.BaseHolderWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingScreen
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingViewModel
import com.tonapps.tonkeeper.ui.screen.staking.stake.details.StakeDetailsFragment
import com.tonapps.tonkeeper.ui.screen.staking.stake.pool.list.Adapter
import com.tonapps.tonkeeper.ui.screen.staking.stake.pool.list.Item
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity
import uikit.extensions.collectFlow

class StakePoolFragment: BaseHolderWalletScreen.ChildListScreen<ScreenContext.None, StakingScreen, StakingViewModel>(ScreenContext.None) {

    private val info: PoolInfoEntity by lazy { requireArguments().getParcelableCompat(ARG_INFO)!! }

    private val adapter = Adapter { pool ->
        setFragment(StakeDetailsFragment.newInstance(info, pool.address))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(primaryViewModel.selectedPoolFlow) { pool ->
            adapter.submitList(Item.map(info, pool.address))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setAdapter(adapter)
        setCloseIcon(UIKitIcon.ic_chevron_left_16) { popBackStack() }
        setActionIcon(UIKitIcon.ic_close_16) { finish() }
        setTitle(info.name)
    }

    companion object {

        private const val ARG_INFO = "info"

        fun newInstance(info: PoolInfoEntity): StakePoolFragment {
            val fragment = StakePoolFragment()
            fragment.arguments = Bundle().apply {
                putParcelable(ARG_INFO, info)
            }
            return fragment
        }

    }
}