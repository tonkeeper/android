package com.tonapps.tonkeeper.ui.screen.staking.confirm

import android.os.Bundle
import com.tonapps.icu.Coins
import com.tonapps.tonkeeper.ui.screen.staking.main.StakeChildFragment
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity

class StakeConfirmFragment: StakeChildFragment(R.layout.fragment_stake_confirm) {

    override fun getTitle() = ""

    companion object {

        private const val POOL_KEY = "pool"
        private const val AMOUNT_KEY = "amount"

        fun newInstance(pool: PoolInfoEntity, amount: Coins): StakeConfirmFragment {
            val fragment = StakeConfirmFragment()
            fragment.arguments = Bundle().apply {
                putParcelable(POOL_KEY, pool)
                putParcelable(AMOUNT_KEY, amount)
            }
            return fragment
        }
    }
}