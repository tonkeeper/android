package com.tonapps.tonkeeper.ui.screen.settings.language

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.settings.language.list.Adapter
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.base.BaseListFragment
import uikit.extensions.collectFlow

class LanguageScreen: BaseListWalletScreen<ScreenContext.None>(ScreenContext.None), BaseFragment.SwipeBack {

    override val viewModel: LanguageViewModel by viewModel()

    private val adapter = Adapter {
        viewModel.setLanguage(it.code)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(getString(Localization.language))
        setAdapter(adapter)
        collectFlow(viewModel.uiItemsFlow, adapter::submitList)
    }

    companion object {
        fun newInstance() = LanguageScreen()
    }
}