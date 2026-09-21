package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.view.ViewGroup
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.deposit.DepositFragment
import com.tonapps.deposit.WithdrawFragment
import com.tonapps.deposit.multicoin.DepositMulticoinFragment
import com.tonapps.deposit.multicoin.WithdrawMulticoinFragment
import com.tonapps.tonkeeper.koin.serverFlags
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingScreen
import com.tonapps.tonkeeper.ui.screen.swap.SwapScreen
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeper.ui.screen.watchonly.WatchInfoScreen
import com.tonapps.tonkeeperx.R
import uikit.widget.ActionIconButtonView

class NewActionsHolder(parent: ViewGroup): Holder<Item.Actions>(parent, R.layout.view_new_wallet_actions) {

    private val sendView = findViewById<ActionIconButtonView>(R.id.send)
    private val receiveView = findViewById<ActionIconButtonView>(R.id.receive)
    private val swapView = findViewById<ActionIconButtonView>(R.id.swap)
    private val stakeView = findViewById<ActionIconButtonView>(R.id.stake)

    override fun onBind(item: Item.Actions) {
        val isWatchOnly = item.walletType == WalletType.Watch
        val isSwapEnabled = item.walletType != WalletType.Watch && item.walletType != WalletType.Testnet && !item.isSwapDisabled
        val isSendEnabled = item.walletType != WalletType.Watch
        val isStakeEnabled = item.walletType != WalletType.Watch && item.walletType != WalletType.Testnet && !item.isStakingDisabled

        receiveView.setOnClickListener {
            if (item.walletType == WalletType.Multichain) {
                navigation?.add(DepositMulticoinFragment())
            } else {
                navigation?.add(DepositFragment())
            }
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
            if (isWatchOnly) {
                openWatchInfo(item.wallet)
                return@setOnClickListener
            } else if (!isSendEnabled) {
                return@setOnClickListener
            }

            if (item.walletType == WalletType.Multichain) {
                navigation?.add(WithdrawMulticoinFragment())
            } else {
                navigation?.add(WithdrawFragment.create())
            }
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
    }

    private fun openWatchInfo(wallet: WalletEntity) {
        navigation?.add(WatchInfoScreen.newInstance(wallet))
    }
}
