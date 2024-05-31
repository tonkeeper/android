package com.tonapps.tonkeeper.ui.screen.settings.language

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.ui.screen.settings.language.list.Adapter
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.base.BaseListFragment
import uikit.extensions.collectFlow

class LanguageScreen: BaseListFragment(), BaseFragment.SwipeBack {

    private val languageViewModel: LanguageViewModel by viewModel()

    private val adapter = Adapter {
        languageViewModel.setLanguage(it.code)
        finish()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(getString(Localization.language))
        setAdapter(adapter)
        collectFlow(languageViewModel.uiItemsFlow, adapter::submitList)
    }

    companion object {
        fun newInstance() = LanguageScreen()
    }
}