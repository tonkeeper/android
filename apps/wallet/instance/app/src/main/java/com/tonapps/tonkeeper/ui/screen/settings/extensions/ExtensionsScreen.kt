package com.tonapps.tonkeeper.ui.screen.settings.extensions

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.send.boc.RemoveExtensionScreen
import com.tonapps.tonkeeper.ui.screen.settings.extensions.list.Adapter
import com.tonapps.tonkeeper.ui.screen.support.SupportScreen
import com.tonapps.uikit.color.iconSecondaryColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import uikit.base.BaseFragment
import uikit.extensions.collectFlow

class ExtensionsScreen(private val wallet: WalletEntity): BaseListWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)), BaseFragment.SwipeBack {

    override val fragmentName: String = "ExtensionsScreen"

    override val viewModel: ExtensionsViewModel by walletViewModel()

    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(viewModel.uiItemsFlow, adapter::submitList)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(getString(Localization.installed_extensions))
        setActionIcon(UIKitIcon.ic_question_message_outline_28) {
            navigation?.add(SupportScreen.newInstance(wallet))
        }
        setActionTint(requireContext().iconSecondaryColor)
        setAdapter(adapter)
    }

    companion object {
        fun newInstance(wallet: WalletEntity) = ExtensionsScreen(wallet)
    }
}



