package com.tonapps.tonkeeper.ui.screen.theme

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import uikit.base.BaseFragment
import uikit.widget.item.ItemIconView

class ThemeScreen: BaseFragment(R.layout.fragment_theme), BaseFragment.SwipeBack {

    companion object {
        fun newInstance() = ThemeScreen()
    }


    private lateinit var themeBlueView: ItemIconView
    private lateinit var themeDarkView: ItemIconView
    private lateinit var themeLightView: ItemIconView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        themeBlueView = view.findViewById(R.id.theme_blue)
        bindClick(themeBlueView, uikit.R.style.Theme_App_Blue)

        themeDarkView = view.findViewById(R.id.theme_dark)
        bindClick(themeDarkView, uikit.R.style.Theme_App_Dark)

        themeLightView = view.findViewById(R.id.theme_light)
        bindClick(themeLightView, uikit.R.style.Theme_App_Light)

        checkCurrentTheme()
    }

    private fun checkCurrentTheme() {
        themeBlueView.iconRes = 0
        themeDarkView.iconRes = 0
        themeLightView.iconRes = 0

        val currentTheme = App.instance.getThemeRes()
        if (currentTheme == uikit.R.style.Theme_App_Blue) {
            themeBlueView.iconRes = UIKitIcon.ic_done_16
        } else if (currentTheme == uikit.R.style.Theme_App_Dark) {
            themeDarkView.iconRes = UIKitIcon.ic_done_16
        } else if (currentTheme == uikit.R.style.Theme_App_Light) {
            themeLightView.iconRes = UIKitIcon.ic_done_16
        }
    }

    private fun setTheme(id: Int) {
        if (App.instance.setThemeRes(id)) {
            requireActivity().recreate()
        }
    }

    private fun bindClick(view: View, id: Int) {
        view.setOnClickListener {
            setTheme(id)
        }
    }

}