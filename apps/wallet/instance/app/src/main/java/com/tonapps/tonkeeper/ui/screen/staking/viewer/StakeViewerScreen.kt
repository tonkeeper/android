package com.tonapps.tonkeeper.ui.screen.staking.viewer

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.extensions.uri
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingScreen
import com.tonapps.tonkeeper.ui.screen.staking.unstake.UnStakeScreen
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Adapter
import com.tonapps.uikit.color.backgroundPageColor
import com.tonapps.wallet.api.entity.EthenaEntity
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.android.inject
import org.koin.core.parameter.parametersOf
import ui.theme.Dimens
import ui.theme.MoonTheme
import uikit.base.BaseFragment
import uikit.extensions.applyNavBottomPadding
import uikit.extensions.collectFlow
import uikit.extensions.dp
import kotlin.math.roundToInt

class StakeViewerScreen(wallet: WalletEntity): BaseListWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)), BaseFragment.SwipeBack {

    override val fragmentName: String = "StakeViewerScreen"

    override val viewModel: StakeViewerViewModel by walletViewModel { parametersOf(args.address, args.ethenaType, args.utm) }

    private val args: StakeViewerArgs by lazy { StakeViewerArgs(requireArguments()) }

    private val environment: Environment by inject()

    private val adapter = Adapter()

    private val actionsState = mutableStateOf<StakeViewerActions?>(null)
    private var actionsBarView: ComposeView? = null
    private var listBottomPadding = 0
    private var appliedBarVisible: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.uiStateFlow.catch {
            navigation?.toast(Localization.unknown_error)
            finish()
        }.onEach(::applyState).launchIn(lifecycleScope)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val title = when {
            args.ethenaType.isNotEmpty() -> getString(Localization.staked_usde)
            else -> args.name
        }
        setTitle(title)
        setAdapter(adapter)
        collectFlow(viewModel.poolNameFlow, ::setTitle)

        listBottomPadding = listView.paddingBottom
        // The bar sits on the legacy page, whose theme can differ from environment.theme.
        val pageColor = Color(requireContext().backgroundPageColor)
        val actionsBar = ComposeView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val actions = actionsState.value
                if (actions != null) {
                    MoonTheme(colorScheme = environment.theme) {
                        StakeViewerActionsBar(
                            unstakeEnabled = !actions.unstakeDisabled,
                            stakeEnabled = !actions.stakeDisabled,
                            onUnstakeClick = { openUnstake(actions) },
                            onStakeClick = { openStake(actions) },
                            backgroundColor = pageColor,
                        )
                    }
                }
            }
        }
        view.findViewById<FrameLayout>(uikit.R.id.list_container).addView(actionsBar)
        actionsBarView = actionsBar
        applyActionsBarVisibility()
    }

    override fun onDestroyView() {
        actionsBarView = null
        appliedBarVisible = null
        super.onDestroyView()
    }

    private fun applyState(state: StakeViewerUiState) {
        adapter.submitList(state.items)
        actionsState.value = state.actions
        applyActionsBarVisibility()
    }

    private fun applyActionsBarVisibility() {
        val actionsBar = actionsBarView ?: return
        val visible = actionsState.value != null
        if (visible == appliedBarVisible) {
            return
        }
        appliedBarVisible = visible
        actionsBar.isVisible = visible
        val barHeight = if (visible) {
            Dimens.heightActionBar.value.roundToInt().dp
        } else {
            0
        }
        listView.applyNavBottomPadding(listBottomPadding + barHeight)
        ViewCompat.requestApplyInsets(listView)
    }

    private fun openStake(actions: StakeViewerActions) {
        when {
            actions.poolAddress != null -> navigation?.add(
                StakingScreen.newInstance(
                    actions.wallet,
                    from = "staking_viewer",
                    poolAddress = actions.poolAddress,
                    utm = actions.utm,
                )
            )
            actions.ethenaMethod != null -> openEthenaDApp(actions, actions.ethenaMethod.depositUrl)
        }
    }

    private fun openUnstake(actions: StakeViewerActions) {
        when {
            actions.poolAddress != null -> navigation?.add(
                UnStakeScreen.newInstance(actions.wallet, actions.poolAddress, actions.utm)
            )
            actions.ethenaMethod != null -> openEthenaDApp(actions, actions.ethenaMethod.withdrawalUrl)
        }
    }

    private fun openEthenaDApp(actions: StakeViewerActions, url: String) {
        val method = actions.ethenaMethod ?: return
        navigation?.add(
            DAppScreen.newInstance(
                wallet = actions.wallet,
                title = method.name,
                url = url.toUri(),
                iconUrl = actions.iconRes?.uri().toString(),
                source = "staking_viewer",
                forceConnect = true,
            )
        )
    }

    companion object {

        fun newInstance(
            wallet: WalletEntity,
            address: String,
            name: String,
            utm: UtmParameters = UtmParameters.Empty,
        ): StakeViewerScreen {
            val fragment = StakeViewerScreen(wallet)
            fragment.setArgs(StakeViewerArgs(address, name, "", utm))
            return fragment
        }

        fun newInstance(wallet: WalletEntity, ethenaType: EthenaEntity.Method.Type): StakeViewerScreen {
            val fragment = StakeViewerScreen(wallet)
            fragment.setArgs(StakeViewerArgs(ethenaType = ethenaType.id, address = "", name = ""))
            return fragment
        }
    }
}