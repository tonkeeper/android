package com.tonapps.tonkeeper.ui.screen.staking.stake.options

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingScreen
import com.tonapps.tonkeeper.ui.screen.staking.stake.options.list.Adapter
import com.tonapps.tonkeeper.ui.screen.staking.stake.options.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.setPaddingHorizontal

class StakeOptionsFragment: StakingScreen.ChildFragment(R.layout.fragment_simple_list) {

    private val adapter = Adapter { pool ->
        stakeViewModel.details(pool)
    }

    private lateinit var listView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        combine(
            stakeViewModel.poolsFlow,
            stakeViewModel.selectedPoolFlow
        ) { pools, selectedPool ->
            val uniquePools = pools.distinctBy { it.implementation }
            Item.map(uniquePools, selectedPool)
        }.onEach(adapter::submitList).launchIn(lifecycleScope)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView = view.findViewById(R.id.list)
        listView.adapter = adapter
        listView.setPaddingHorizontal(requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium))
    }

    override fun getTitle() = getString(Localization.staking_options)

    companion object {
        fun newInstance() = StakeOptionsFragment()
    }
}