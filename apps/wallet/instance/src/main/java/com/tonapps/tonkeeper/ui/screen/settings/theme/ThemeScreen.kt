package com.tonapps.tonkeeper.ui.screen.settings.theme

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.widget.HeaderView
import uikit.widget.item.ItemIconView

class ThemeScreen: BaseFragment(R.layout.fragment_theme), BaseFragment.SwipeBack {

    private val themeViewModel: ThemeViewModel by viewModel()

    private lateinit var headerView: HeaderView
    private lateinit var themeBlueView: ItemIconView
    private lateinit var themeDarkView: ItemIconView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        themeBlueView = view.findViewById(R.id.theme_blue)
        bindClick(themeBlueView, uikit.R.style.Theme_App_Blue)

        themeDarkView = view.findViewById(R.id.theme_dark)
        bindClick(themeDarkView, uikit.R.style.Theme_App_Dark)

        collectFlow(themeViewModel.currentThemeFlow, ::setCurrentTheme)
    }

    private fun setCurrentTheme(currentTheme: Int) {
        themeBlueView.isEnabled = true
        themeDarkView.isEnabled = true
        themeBlueView.iconRes = 0
        themeDarkView.iconRes = 0

        if (currentTheme == uikit.R.style.Theme_App_Blue) {
            themeBlueView.iconRes = UIKitIcon.ic_done_16
            themeBlueView.isEnabled = false
        } else if (currentTheme == uikit.R.style.Theme_App_Dark) {
            themeDarkView.iconRes = UIKitIcon.ic_done_16
            themeDarkView.isEnabled = false
        }
    }

    private fun setTheme(id: Int) {
        themeViewModel.setTheme(id)
    }

    private fun bindClick(view: View, id: Int) {
        view.setOnClickListener {
            setTheme(id)
        }
    }

    companion object {
        fun newInstance() = ThemeScreen()
    }
}