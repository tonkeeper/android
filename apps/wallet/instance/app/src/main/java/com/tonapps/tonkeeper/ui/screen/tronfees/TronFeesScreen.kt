package com.tonapps.tonkeeper.ui.screen.tronfees

import android.os.Bundle
import android.view.View
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.tronfees.list.Adapter
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.deposit.multicoin.analytics.depositFlowFromOrNull
import com.tonapps.deposit.usecase.emulation.TronFeesEmulation
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow

class TronFeesScreen(wallet: WalletEntity) : BaseListWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)), BaseFragment.Modal {

    override val fragmentName: String = "TronFeesScreen"

    private val analyticsFrom: DepositFlowFrom by lazy {
        depositFlowFromOrNull(requireArguments().getString(ARG_ANALYTICS_FROM)) ?: DepositFlowFrom.Other
    }

    private val type: TronFeesScreenType by lazy {
        val name = requireArguments().getString(ARG_TYPE)
        TronFeesScreenType.entries.firstOrNull { it.name == name } ?: TronFeesScreenType.DefaultFees
    }

    private val emulation: TronFeesEmulation? by lazy {
        requireArguments().getParcelableCompat(ARG_EMULATION)
    }

    override val viewModel: TronFeesViewModel by walletViewModel() {
        parametersOf(type)
    }

    private val adapter by lazy { Adapter(analyticsFrom) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.setEmulation(emulation)
        setAdapter(adapter)
        collectFlow(viewModel.uiItemsFlow, adapter::submitList)
    }

    companion object {
        private const val ARG_ANALYTICS_FROM = "analytics_from"
        private const val ARG_TYPE = "type"
        private const val ARG_EMULATION = "emulation"

        fun newInstance(
            wallet: WalletEntity,
            analyticsFrom: DepositFlowFrom,
            type: TronFeesScreenType = TronFeesScreenType.DefaultFees,
            emulation: TronFeesEmulation? = null
        ): TronFeesScreen {
            val fragment = TronFeesScreen(wallet)
            fragment.putStringArg(ARG_ANALYTICS_FROM, analyticsFrom.key)
            fragment.putStringArg(ARG_TYPE, type.name)
            emulation?.let { fragment.putParcelableArg(ARG_EMULATION, it) }
            return fragment
        }
    }
}


