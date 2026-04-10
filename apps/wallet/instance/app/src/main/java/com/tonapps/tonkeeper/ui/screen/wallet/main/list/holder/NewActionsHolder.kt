package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.view.ViewGroup
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowFrom
import com.tonapps.core.flags.TooltipManager
import com.tonapps.core.flags.TooltipState
import com.tonapps.core.flags.WalletTooltip
import com.tonapps.deposit.DepositFragment
import com.tonapps.deposit.WithdrawFragment
import com.tonapps.tonkeeper.core.DevSettings
import com.tonapps.tonkeeper.helper.DateHelper
import com.tonapps.tonkeeper.koin.serverFlags
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingScreen
import com.tonapps.tonkeeper.ui.screen.swap.SwapScreen
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeper.ui.screen.watchonly.WatchInfoScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import uikit.widget.ActionIconButtonView
import uikit.widget.BalloonTooltip

class NewActionsHolder(parent: ViewGroup): Holder<Item.Actions>(parent, R.layout.view_new_wallet_actions) {

    private val sendView = findViewById<ActionIconButtonView>(R.id.send)
    private val receiveView = findViewById<ActionIconButtonView>(R.id.receive)
    private val swapView = findViewById<ActionIconButtonView>(R.id.swap)
    private val stakeView = findViewById<ActionIconButtonView>(R.id.stake)

    private var tooltip: BalloonTooltip? = null

    override fun onBind(item: Item.Actions) {
        val isWatchOnly = item.walletType == WalletType.Watch
        val isSwapEnabled = item.walletType != WalletType.Watch && item.walletType != WalletType.Testnet && !item.isSwapDisabled
        val isSendEnabled = item.walletType != WalletType.Watch
        val isStakeEnabled = item.walletType != WalletType.Watch && item.walletType != WalletType.Testnet && !item.isStakingDisabled

        receiveView.setOnClickListener {
            AnalyticsHelper.Default.events.depositFlow.depositOpen(from = DepositFlowFrom.WalletScreen)
            navigation?.add(DepositFragment())
        }

        swapView.setOnClickListener {
            if (isWatchOnly) {
                openWatchInfo(item.wallet)
                return@setOnClickListener
            } else if (!isSwapEnabled) {
                return@setOnClickListener
            }

            navigation?.add(SwapScreen.newInstance(
                wallet = item.wallet,
                nativeSwap = context.serverFlags?.disableNativeSwap != true,
                uri = item.swapUri
            ))
        }

        sendView.setOnClickListener {
            AnalyticsHelper.Default.events.withdrawFlow.withdrawOpen(from = WithdrawFlowFrom.WalletScreen)
            markTooltipSeen()
            if (isWatchOnly) {
                openWatchInfo(item.wallet)
                return@setOnClickListener
            } else if (!isSendEnabled) {
                return@setOnClickListener
            }

            navigation?.add(WithdrawFragment.create())
        }
        stakeView.setOnClickListener {
            if (isWatchOnly) {
                openWatchInfo(item.wallet)
                return@setOnClickListener
            } else if (!isStakeEnabled) {
                return@setOnClickListener
            }

            navigation?.add(StakingScreen.newInstance(wallet = item.wallet, from = "wallet"))
        }

        swapView.setEnabledAlpha(isSwapEnabled)
        sendView.setEnabledAlpha(isSendEnabled)
        stakeView.setEnabledAlpha(isStakeEnabled)

        if (item.isSwapDisabled) {
            swapView.alpha = 0f
        }
        if (item.isStakingDisabled) {
            stakeView.alpha = 0f
        }

        showSendTooltipIfNeeded(isSendEnabled)
    }

    override fun onUnbind() {
        super.onUnbind()
        dismissTooltip()
    }

    private fun showSendTooltipIfNeeded(isSendEnabled: Boolean) {
        val key = WalletTooltip.Ramp.key
        if (!isSendEnabled) {
            return
        }

        if (!WalletTooltip.Ramp.shouldShow) {
            return
        }

        if (isNewUser() && WalletTooltip.Ramp.state != TooltipState.ALWAYS) { // TODO move
            markTooltipShown()
            return
        }

        sendView.post {
            if (!sendView.isAttachedToWindow) {
                return@post
            }

            tooltip = BalloonTooltip.show(
                anchorView = sendView,
                badgeText = getString(Localization.tooltip_new),
                messageText = getString(Localization.tooltip_send_from_here),
                autoDismissMs = BalloonTooltip.NO_AUTO_DISMISS,
                onClickListener = { markTooltipSeen() },
            )

            TooltipManager.markShownInSession(key)
            TooltipManager.incrementShowCount(key)
        }
    }

    private fun markTooltipSeen() {
        dismissTooltip()
        markTooltipShown()
    }

    private fun markTooltipShown() {
        if (WalletTooltip.Ramp.state != TooltipState.ALWAYS) {
            TooltipManager.setState(WalletTooltip.Ramp.key, TooltipState.SHOWN)
        }
    }

    private fun isNewUser(): Boolean {
        val firstLaunch = DevSettings.firstLaunchDate
        return firstLaunch <= 0 || DateHelper.isToday(firstLaunch)
    }

    private fun dismissTooltip() {
        tooltip?.dismiss()
        tooltip = null
    }

    private fun openWatchInfo(wallet: WalletEntity) {
        navigation?.add(WatchInfoScreen.newInstance(wallet))
    }
}
