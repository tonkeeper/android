package com.tonapps.tonkeeper.ui.screen.battery.settings

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.koin.parentFragmentViewModel
import com.tonapps.tonkeeper.ui.base.BaseHolderWalletScreen
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.battery.BatteryScreen
import com.tonapps.tonkeeper.ui.screen.battery.BatteryViewModel
import com.tonapps.tonkeeper.ui.screen.battery.settings.list.Adapter
import com.tonapps.tonkeeper.ui.screen.battery.settings.list.Item
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.R
import uikit.extensions.collectFlow
import uikit.extensions.dp
import uikit.extensions.getDimensionPixelSize

class BatterySettingsScreen(wallet: WalletEntity): BaseHolderWalletScreen.ChildListScreen<ScreenContext.Wallet, BatteryScreen, BatteryViewModel>(ScreenContext.Wallet(wallet)) {

    override val viewModel: BatterySettingsViewModel by viewModel {
        parametersOf(screenContext.wallet)
    }

    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(viewModel.uiItemsFlow, adapter::submitList)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setCloseIcon(UIKitIcon.ic_chevron_left_16) { popBackStack() }
        setActionIcon(UIKitIcon.ic_close_16) { finish() }
        setAdapter(adapter)
        updateListPadding(top = requireContext().getDimensionPixelSize(R.dimen.barHeight))
    }

    companion object {

        fun newInstance(wallet: WalletEntity) = BatterySettingsScreen(wallet)
    }
}