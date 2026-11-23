package com.tonapps.tonkeeper.ui.screen.staking.rewards

import androidx.compose.runtime.Composable
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.base.compose.ComposeWalletScreen
import uikit.base.BaseFragment

class StakingRewardsHistoryScreen(wallet: WalletEntity) : ComposeWalletScreen(wallet), BaseFragment.SwipeBack {

    override val viewModel: BaseWalletVM? = null

    private val feature: StakingRewardsHistoryFeature by walletViewModel()

    @Composable
    override fun ScreenContent() = StakingRewardsHistoryComposable(feature = feature, onBack = { finish() })

    companion object {

        fun newInstance(wallet: WalletEntity): StakingRewardsHistoryScreen {
            return StakingRewardsHistoryScreen(wallet)
        }
    }
}
