package com.tonapps.tonkeeper.ui.screen.battery.refill

import android.os.Bundle
import android.view.View
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseHolderWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.battery.BatteryScreen
import com.tonapps.tonkeeper.ui.screen.battery.BatteryViewModel
import com.tonapps.tonkeeper.ui.screen.battery.refill.list.Adapter
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.blockchain.model.legacy.WalletEntity
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.R
import uikit.extensions.collectFlow

class BatteryRefillScreen(wallet: WalletEntity) : BaseHolderWalletScreen.ChildListScreen<ScreenContext.Wallet, BatteryScreen, BatteryViewModel>(ScreenContext.Wallet(wallet)) {

    override val fragmentName: String = "BatteryRefillScreen"

    private val from: BatteryNativeFrom by lazy {
        BatteryNativeFrom.valueOf(requireArguments().getString(ARG_FROM)!!)
    }

    private val utm: UtmParameters by lazy {
        requireArguments().getParcelableCompat<UtmParameters>(ARG_UTM) ?: UtmParameters.Empty
    }

    override val viewModel: BatteryRefillViewModel by walletViewModel {
        parametersOf(from, utm)
    }

    private val adapter = Adapter(
        openSettings = { primaryViewModel.routeToSettings() },
        onSubmitPromo = { viewModel.submitPromo(it) },
        onPackSelect = { viewModel.makePurchase(it, requireActivity()) },
        onRestorePurchases = { viewModel.restorePurchases() }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(viewModel.uiItemsFlow, adapter::submitList)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHeaderBackground(R.drawable.bg_page_gradient)
        setActionIcon(UIKitIcon.ic_close_16) { finish() }
        setAdapter(adapter)
        arguments?.getString(ARG_PROMO)?.let {
            viewModel.applyPromo(it)
        }
    }

    companion object {
        private const val ARG_PROMO = "promo"
        private const val ARG_FROM = "from"
        private const val ARG_UTM = "utm"

        fun newInstance(
            wallet: WalletEntity,
            promo: String?,
            from: BatteryNativeFrom,
            utm: UtmParameters = UtmParameters.Empty,
        ): BatteryRefillScreen {
            val fragment = BatteryRefillScreen(wallet)
            fragment.putStringArg(ARG_PROMO, promo)
            fragment.putStringArg(ARG_FROM, from.name)
            fragment.putParcelableArg(ARG_UTM, utm)
            return fragment
        }
    }
}