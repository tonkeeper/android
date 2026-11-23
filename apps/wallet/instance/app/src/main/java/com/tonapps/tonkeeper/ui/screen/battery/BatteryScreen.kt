package com.tonapps.tonkeeper.ui.screen.battery

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.core.ComposableFragment
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.koin.analytics
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.battery.refill.BatteryRefillRoute
import com.tonapps.tonkeeper.ui.screen.battery.refill.BatteryRefillViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment

class BatteryScreen : ComposableFragment(), BaseFragment.BottomSheet, BaseWalletVM.Holder {

    override val fragmentName: String = "BatteryScreen"

    override val uiContext: Context
        get() = requireContext()

    private val wallet: WalletEntity by lazy {
        requireArguments().getParcelableCompat<WalletEntity>(ARG_WALLET)!!
    }

    private val from: BatteryNativeFrom by lazy {
        BatteryNativeFrom.valueOf(requireArguments().getString(ARG_FROM)!!)
    }

    private val utm: UtmParameters by lazy {
        requireArguments().getParcelableCompat<UtmParameters>(ARG_UTM) ?: UtmParameters.Empty
    }

    private val viewModel: BatteryRefillViewModel by viewModel {
        parametersOf(wallet, from, utm, arguments?.getString(ARG_JETTON) ?: "")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context?.analytics?.eventsTaggedWith(utm)?.batteryNative?.batteryOpen(from)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewModel.attachHolder(this)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.getString(ARG_PROMO)?.let {
            viewModel.applyPromo(it)
        }
        viewModel.setBatteryViewed()
        setContent {
            BatteryRefillRoute(viewModel, onClose = ::finish)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onScreenResumed()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.detachHolder()
    }

    companion object {
        private const val ARG_WALLET = "wallet"
        private const val ARG_PROMO = "promo"
        private const val ARG_FROM = "from"
        private const val ARG_JETTON = "jetton"
        private const val ARG_UTM = "utm"

        fun newInstance(
            wallet: WalletEntity,
            promo: String? = null,
            from: BatteryNativeFrom,
            jetton: String? = null,
            utm: UtmParameters = UtmParameters.Empty,
        ): BatteryScreen {
            val fragment = BatteryScreen()
            fragment.putParcelableArg(ARG_WALLET, wallet)
            fragment.putStringArg(ARG_PROMO, promo)
            fragment.putStringArg(ARG_FROM, from.name)
            fragment.putStringArg(ARG_JETTON, jetton)
            fragment.putParcelableArg(ARG_UTM, utm)
            return fragment
        }
    }
}
