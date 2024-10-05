package com.tonapps.tonkeeper.ui.screen.staking.stake.details

import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.flexbox.FlexboxLayout
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.ui.base.BaseHolderWalletScreen
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingScreen
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingViewModel
import com.tonapps.tonkeeper.ui.screen.staking.stake.amount.StakeAmountFragment
import com.tonapps.tonkeeper.ui.screen.staking.unstake.UnStakeScreen
import com.tonapps.tonkeeper.ui.screen.staking.unstake.UnStakeViewModel
import com.tonapps.tonkeeper.ui.screen.swap.SwapArgs
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.staking.entities.PoolDetailsEntity
import com.tonapps.wallet.data.staking.entities.PoolEntity
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity
import com.tonapps.wallet.localization.Localization
import uikit.extensions.applyBottomInsets
import uikit.extensions.dp
import uikit.extensions.drawable
import uikit.extensions.inflate
import uikit.extensions.pinToBottomInsets
import uikit.extensions.setLeftDrawable
import uikit.extensions.withGreenBadge
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView

class StakeDetailsFragment: BaseHolderWalletScreen.ChildFragment<StakingScreen, StakingViewModel>(R.layout.fragment_stake_details) {

    private val args: StakeDetailsArgs by lazy { StakeDetailsArgs(requireArguments()) }

    private lateinit var poolApyTitleView: AppCompatTextView
    private lateinit var linkDrawable: Drawable
    private lateinit var linksView: FlexboxLayout
    private lateinit var button: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val headerView = view.findViewById<HeaderView>(R.id.header)
        headerView.doOnCloseClick = { popBackStack() }
        headerView.doOnActionClick = { finish() }
        headerView.title = args.name

        poolApyTitleView = view.findViewById(R.id.pool_apy_title)
        if (args.maxApy) {
            poolApyTitleView.text = getString(Localization.staking_apy).withGreenBadge(requireContext(), Localization.staking_max_apy)
        } else {
            poolApyTitleView.text = getString(Localization.staking_apy)
        }

        linkDrawable = requireContext().drawable(UIKitIcon.ic_globe_16)
        val apyView = view.findViewById<AppCompatTextView>(R.id.pool_apy)
        apyView.text = "≈ ${CurrencyFormatter.formatPercent(args.apy)}"

        val minDepositView = view.findViewById<AppCompatTextView>(R.id.pool_min_deposit)
        minDepositView.text = CurrencyFormatter.format(TokenEntity.TON.symbol, args.minStake)

        linksView = view.findViewById(R.id.links)
        applyLinks(args.links)

        button = view.findViewById(R.id.choose_button)
        button.setOnClickListener {
            primaryViewModel.selectPool(args.pool)
            popBackStack(StakeAmountFragment.TAG)
        }
        button.applyBottomInsets()
    }

    private fun applyLinks(links: List<String>) {
        linksView.removeAllViews()
        for (link in links) {
            val host = Uri.parse(link).host!!
            val linkView = requireContext().inflate(R.layout.view_link, linksView) as AppCompatTextView
            linkView.text = host
            linkView.setLeftDrawable(linkDrawable)
            linkView.setOnClickListener { BrowserHelper.open(requireContext(), link) }
            linksView.addView(linkView)
        }
    }

    override fun onKeyboardAnimation(offset: Int, progress: Float, isShowing: Boolean) {
        super.onKeyboardAnimation(offset, progress, isShowing)
        button.translationY = -offset.toFloat()
    }

    companion object {
        fun newInstance(
            info: PoolInfoEntity,
            poolAddress: String
        ): StakeDetailsFragment {
            val fragment = StakeDetailsFragment()
            fragment.setArgs(StakeDetailsArgs(info, poolAddress))
            return fragment
        }
    }
}