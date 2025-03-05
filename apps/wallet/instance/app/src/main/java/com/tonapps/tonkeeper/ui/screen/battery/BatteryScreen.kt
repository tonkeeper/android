package com.tonapps.tonkeeper.ui.screen.battery

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseHolderWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.battery.refill.BatteryRefillScreen
import com.tonapps.tonkeeper.ui.screen.battery.settings.BatterySettingsScreen
import com.tonapps.wallet.data.account.entities.WalletEntity
import kotlinx.coroutines.flow.map
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow

class BatteryScreen(wallet: WalletEntity): BaseHolderWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)), BaseFragment.BottomSheet {

    override val fragmentName: String = "BatteryScreen"

    private val from: String by lazy { requireArguments().getString(ARG_FROM)!! }

    private val initialPromo: String? by lazy { requireArguments().getString(ARG_PROMO) }

    override val viewModel: BatteryViewModel by walletViewModel {
        parametersOf(arguments?.getString(ARG_JETTON))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnalyticsHelper.simpleTrackScreenEvent("battery_open", viewModel.installId, from)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectFlow(viewModel.routeFlow.map { route ->
            when(route) {
                BatteryRoute.Refill -> BatteryRefillScreen.newInstance(screenContext.wallet, initialPromo)
                BatteryRoute.Settings -> BatterySettingsScreen.newInstance(screenContext.wallet)
            }
        }) { setFragment(it) }
    }

    companion object {
        private const val ARG_PROMO = "promo"
        private const val ARG_FROM = "from"
        private const val ARG_JETTON = "jetton"

        fun newInstance(
            wallet: WalletEntity,
            promo: String? = null,
            from: String,
            jetton: String? = null,
        ): BatteryScreen {
            val fragment = BatteryScreen(wallet)
            fragment.putStringArg(ARG_PROMO, promo)
            fragment.putStringArg(ARG_FROM, from)
            fragment.putStringArg(ARG_JETTON, jetton)
            return fragment
        }
    }
}