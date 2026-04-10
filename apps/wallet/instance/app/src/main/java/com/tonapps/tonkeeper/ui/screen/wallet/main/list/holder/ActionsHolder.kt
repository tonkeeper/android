package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.view.ViewGroup
import com.tonapps.blockchain.contract.Blockchain
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.bus.generated.Events
import com.tonapps.deposit.screens.qr.QrAssetFragment
import com.tonapps.tonkeeper.koin.serverFlags
import com.tonapps.tonkeeper.ui.screen.camera.CameraScreen
import com.tonapps.tonkeeper.ui.screen.onramp.main.OnRampScreen
import com.tonapps.tonkeeper.ui.screen.send.main.SendScreen
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingScreen
import com.tonapps.tonkeeper.ui.screen.swap.SwapScreen
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeper.ui.screen.watchonly.WatchInfoScreen
import com.tonapps.tonkeeperx.R
import uikit.widget.IconButtonView

class ActionsHolder(parent: ViewGroup): Holder<Item.Actions>(parent, R.layout.view_wallet_actions) {

    private val sendView = findViewById<IconButtonView>(R.id.send)
    private val receiveView = findViewById<IconButtonView>(R.id.receive)
    private val buyOrSellView = findViewById<IconButtonView>(R.id.buy_or_sell)
    private val swapView = findViewById<IconButtonView>(R.id.swap)
    private val scanView = findViewById<IconButtonView>(R.id.scan)
    private val stakeView = findViewById<IconButtonView>(R.id.stake)

    override fun onBind(item: Item.Actions) {
        val isWatchOnly = item.walletType == WalletType.Watch
        val isSwapEnabled = item.walletType != WalletType.Watch && item.walletType != WalletType.Testnet && !item.isSwapDisabled
        val isSendEnabled = item.walletType != WalletType.Watch
        val isScanEnabled = item.walletType != WalletType.Watch
        val isStakeEnabled = item.walletType != WalletType.Watch && item.walletType != WalletType.Testnet && !item.isStakingDisabled
        val isBuyOrSellEnabled = item.walletType != WalletType.Testnet && !item.isExchangeDisabled

        scanView.setOnClickListener {
            if (isWatchOnly) {
                openWatchInfo(item.wallet)
                return@setOnClickListener
            } else if (!isScanEnabled) {
                return@setOnClickListener
            }

            val chains = mutableListOf(Blockchain.TON)

            if (item.tronEnabled) {
                chains.add(Blockchain.TRON)
            }

            navigation?.add(CameraScreen.newInstance(chains = chains))
        }
        receiveView.setOnClickListener {
            navigation?.add(QrAssetFragment.newInstance())
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
        buyOrSellView.setOnClickListener {
            if (!isBuyOrSellEnabled) {
                return@setOnClickListener
            }

            navigation?.add(OnRampScreen.newInstance(context, item.wallet, "wallet"))
        }
        sendView.setOnClickListener {
            if (isWatchOnly) {
                openWatchInfo(item.wallet)
                return@setOnClickListener
            } else if (!isSendEnabled) {
                return@setOnClickListener
            }

            navigation?.add(
                SendScreen.newInstance(
                    item.wallet,
                    type = SendScreen.Companion.Type.Default,
                    from = Events.SendNative.SendNativeFrom.WalletScreen
                )
            )
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
        scanView.setEnabledAlpha(isScanEnabled)
        stakeView.setEnabledAlpha(isStakeEnabled)
        buyOrSellView.setEnabledAlpha(isBuyOrSellEnabled)

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