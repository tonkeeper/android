package com.tonapps.tonkeeper.ui.screen.battery

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.FragmentManager
import com.tonapps.tonkeeper.ui.base.BaseHolderWalletScreen
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.screen.battery.refill.BatteryRefillScreen
import com.tonapps.tonkeeper.ui.screen.battery.settings.BatterySettingsScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import kotlinx.coroutines.flow.map
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.commitChildAsSlide
import uikit.widget.HeaderView

class BatteryScreen : BaseHolderWalletScreen(), BaseFragment.BottomSheet {

    private val initialPromo: String? by lazy { requireArguments().getString(ARG_PROMO) }

    override val viewModel: BatteryViewModel by viewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectFlow(viewModel.routeFlow.map { route ->
            when(route) {
                BatteryRoute.Refill -> BatteryRefillScreen.newInstance(initialPromo)
                BatteryRoute.Settings -> BatterySettingsScreen.newInstance()
            }
        }) { setFragment(it) }
    }

    companion object {
        private const val ARG_PROMO = "promo"

        fun newInstance(promo: String? = null): BatteryScreen {
            val fragment = BatteryScreen()
            fragment.arguments = Bundle().apply {
                putString(ARG_PROMO, promo)
            }

            return fragment
        }
    }
}