package com.tonapps.portfolio

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.tonapps.bus.generated.Events.AssetScreen.AssetScreenFrom
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowFrom
import com.tonapps.core.ComposableFragment
import com.tonapps.core.navigation.NavigationDelegate
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import uikit.base.TabScreen
import uikit.extensions.activity

val LocalPortfolioIsTop = staticCompositionLocalOf { true }

class PortfolioFragment : ComposableFragment(), TabScreen {
    override val fragmentName: String = "PortfolioFragment"

    private var isTop by mutableStateOf(true)

    private var topCallbacksHost: FragmentManager? = null

    private val scrollToTopEvents = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val topCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentAttached(fm: FragmentManager, f: Fragment, context: Context) {
            updateTop()
        }

        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
            updateTop()
        }

        override fun onFragmentPaused(fm: FragmentManager, f: Fragment) {
            updateTop()
        }

        override fun onFragmentDetached(fm: FragmentManager, f: Fragment) {
            updateTop()
        }
    }

    override fun smoothScrollToTop() {
        scrollToTopEvents.tryEmit(Unit)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val fm = requireActivity().supportFragmentManager
        fm.registerFragmentLifecycleCallbacks(topCallbacks, true)
        topCallbacksHost = fm
        updateTop()

        val delegate = context?.activity as? NavigationDelegate
        setContent {
            CompositionLocalProvider(LocalPortfolioIsTop provides isTop) {
                PortfolioRouter(
                    scrollToTop = scrollToTopEvents,
                    onOpenDeposit = { delegate?.onOpenDeposit(DepositFlowFrom.WalletScreen) },
                    onOpenWithdraw = { delegate?.onOpenWithdraw(WithdrawFlowFrom.WalletScreen) },
                    onOpenAsset = { asset ->
                        delegate?.onOpenAssetDetails(
                            assetId = asset.id,
                            previewName = asset.name,
                            previewImageUrl = asset.imageUrl,
                            from = AssetScreenFrom.WalletScreen
                        )
                    },
                    onOpenAccountsManage = { delegate?.onOpenAccountsManage() },
                    onOpenPortfolioSearch = { delegate?.onOpenPortfolioSearch() },
                    onOpenWalletsList = { delegate?.onOpenWalletsList() },
                    onOpenSwaps = { delegate?.onOpenSwap() },
                    onOpenStake = { delegate?.onOpenStake() },
                    onOpenStakeViewer = { poolAddress, poolName ->
                        delegate?.onOpenStakeViewer(poolAddress, poolName)
                    },
                    onOpenStakeWithdraw = { poolAddress ->
                        delegate?.onOpenStakeWithdraw(poolAddress)
                    },
                    onOpenScanner = { delegate?.onOpenScanner() },
                    onSettingsClick = { delegate?.onOpenSettings() },
                    onHistoryClick = { delegate?.onOpenHistory() },
                    onBackupClick = { delegate?.onOpenBackup() },
                    onEnablePushClick = { delegate?.onEnablePush() },
                    onMigrationClick = { delegate?.onOpenMigration() },
                    onEnableBiometryClick = { delegate?.onEnableBiometry() },
                    onOpenCollectibles = { delegate?.onOpenCollectibles() },
                    onOpenNft = { nft -> delegate?.onOpenNft(nft) },
                    onOpenLink = { url -> delegate?.onOpenLink(url) },
                    onOpenRaffle = { walletId, raffleId -> delegate?.onOpenRaffle(walletId, raffleId) },
                    onOpenBattery = { delegate?.onOpenBattery(from = BatteryNativeFrom.Wallet) },
                    onOpenReceivingAddress = { delegate?.onOpenReceivingAddress() },
                    onOpenPerps = { entryPoint -> delegate?.onOpenPerps(entryPoint) },
                    onOpenPerpMarket = { marketIndex, symbol, entryPoint ->
                        delegate?.onOpenPerpMarket(marketIndex, symbol, entryPoint)
                    },
                    onOpenEarn = { from -> delegate?.onOpenEarn(from) },
                    onOpenEarnVault = { network, vaultId, from ->
                        delegate?.onOpenEarnVault(network, vaultId, from)
                    },
                )
            }
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        updateTop()
    }

    override fun onDestroyView() {
        topCallbacksHost?.unregisterFragmentLifecycleCallbacks(topCallbacks)
        topCallbacksHost = null
        super.onDestroyView()
    }

    private fun updateTop() {
        if (!isAdded) {
            return
        }
        isTop = generateSequence<Fragment>(this) { it.parentFragment }.all { fragment ->
            !fragment.isHidden && fragment.parentFragmentManager.fragments.lastOrNull { !it.isHidden } == fragment
        }
    }

    companion object {
        fun newInstance(): PortfolioFragment = PortfolioFragment()
    }
}
