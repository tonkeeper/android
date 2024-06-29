package com.tonapps.tonkeeper.ui.screen.staking.details

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.flexbox.FlexboxLayout
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.ui.screen.staking.main.StakeChildFragment
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity

class StakeDetailsFragment: StakeChildFragment(R.layout.fragment_stake_details) {

    private val pool: PoolInfoEntity by lazy { requireArguments().getParcelableCompat(POOL_KEY)!! }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val apyView = view.findViewById<AppCompatTextView>(R.id.pool_apy)
        apyView.text = "≈ ${pool.apyPercent}%"

        val minDepositView = view.findViewById<AppCompatTextView>(R.id.pool_min_deposit)
        minDepositView.text = CurrencyFormatter.format(TokenEntity.TON.symbol, pool.minStake)

        val linksView = view.findViewById<FlexboxLayout>(R.id.links)

        val chooseButton = view.findViewById<Button>(R.id.choose_button)
        chooseButton.setOnClickListener {
            stakeViewModel.selectPool(pool)
            finish()
        }
    }

    override fun getTitle() = pool.details.name

    companion object {
        private const val POOL_KEY = "pool"

        fun newInstance(pool: PoolInfoEntity): StakeDetailsFragment {
            val fragment = StakeDetailsFragment()
            fragment.arguments = Bundle().apply {
                putParcelable(POOL_KEY, pool)
            }
            return fragment
        }
    }
}