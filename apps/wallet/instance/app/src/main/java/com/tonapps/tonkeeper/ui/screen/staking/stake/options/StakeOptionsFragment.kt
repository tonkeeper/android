package com.tonapps.tonkeeper.ui.screen.staking.stake.options

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.base.BaseHolderWalletScreen
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingScreen
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingViewModel
import com.tonapps.tonkeeper.ui.screen.staking.stake.details.StakeDetailsFragment
import com.tonapps.tonkeeper.ui.screen.staking.stake.options.list.Adapter
import com.tonapps.tonkeeper.ui.screen.staking.stake.options.list.Item
import com.tonapps.tonkeeper.ui.screen.staking.stake.pool.StakePoolFragment
import com.tonapps.tonkeeper.ui.screen.staking.unstake.UnStakeScreen
import com.tonapps.tonkeeper.ui.screen.staking.unstake.UnStakeViewModel
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.setPaddingHorizontal

class StakeOptionsFragment: BaseHolderWalletScreen.ChildListScreen<StakingScreen, StakingViewModel>() {

    private val adapter = Adapter { info ->
        openPool(info)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        combine(
            primaryViewModel.poolsFlow,
            primaryViewModel.selectedPoolFlow
        ) { pools, selectedPool ->
            val uniquePools = pools.distinctBy { it.implementation }
            Item.map(uniquePools, selectedPool)
        }.onEach(adapter::submitList).launchIn(lifecycleScope)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setAdapter(adapter)
        setCloseIcon(UIKitIcon.ic_chevron_left_16) { popBackStack() }
        setActionIcon(UIKitIcon.ic_close_16) { finish() }
        setTitle(getString(Localization.staking_options))
    }

    private fun openPool(info: PoolInfoEntity) {
        if (info.pools.size > 1) {
            setFragment(StakePoolFragment.newInstance(info))
        } else {
            val singlePool = info.pools.firstOrNull() ?: return
            setFragment(StakeDetailsFragment.newInstance(info, singlePool.address))
        }
    }

    companion object {
        fun newInstance() = StakeOptionsFragment()
    }
}