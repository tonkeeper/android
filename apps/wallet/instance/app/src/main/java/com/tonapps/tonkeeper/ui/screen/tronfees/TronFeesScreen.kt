package com.tonapps.tonkeeper.ui.screen.tronfees

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.tronfees.list.Adapter
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.deposit.usecase.emulation.TronFeesEmulation
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow

class TronFeesScreen(
    wallet: WalletEntity,
    private val type: TronFeesScreenType,
    private val emulation: TronFeesEmulation?,
) : BaseListWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)), BaseFragment.Modal {

    override val fragmentName: String = "TronFeesScreen"

    override val viewModel: TronFeesViewModel by walletViewModel() {
        parametersOf(type)
    }

    private val adapter = Adapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.setEmulation(emulation)
        setAdapter(adapter)
        collectFlow(viewModel.uiItemsFlow, adapter::submitList)
    }

    companion object {

        fun newInstance(
            wallet: WalletEntity,
            type: TronFeesScreenType = TronFeesScreenType.DefaultFees,
            emulation: TronFeesEmulation? = null
        ): TronFeesScreen {
            return TronFeesScreen(wallet, type, emulation)
        }
    }
}


