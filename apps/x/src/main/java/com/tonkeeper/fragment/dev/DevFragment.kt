package com.tonkeeper.fragment.dev

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeperx.R
import com.tonkeeper.App
import com.tonkeeper.settings.ExperimentalSettings
import uikit.base.BaseFragment
import uikit.list.ListCell
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.item.ItemSwitchView

class DevFragment: BaseFragment(R.layout.fragment_dev), BaseFragment.SwipeBack {

    companion object {
        fun newInstance() = DevFragment()
    }

    private val experimentalSettings: ExperimentalSettings
        get() = App.settings.experimental

    private lateinit var blur31Switch: ItemSwitchView
    private lateinit var blurAllSwitch: ItemSwitchView
    private lateinit var bottomBgOverSwitch: ItemSwitchView
    private lateinit var lightThemeSwitch: ItemSwitchView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        blur31Switch = view.findViewById(R.id.blur_31)
        blur31Switch.position = ListCell.Position.FIRST
        blur31Switch.checked = experimentalSettings.hasBlur31
        blur31Switch.doOnCheckedChanged = {
            experimentalSettings.hasBlur31 = it
        }

        blurAllSwitch = view.findViewById(R.id.blur_all)
        blurAllSwitch.position = ListCell.Position.MIDDLE
        blurAllSwitch.checked = experimentalSettings.hasBlurLegacy
        blurAllSwitch.doOnCheckedChanged = {
            experimentalSettings.hasBlurLegacy = it
        }

        bottomBgOverSwitch = view.findViewById(R.id.bottom_bg)
        bottomBgOverSwitch.position = ListCell.Position.MIDDLE
        bottomBgOverSwitch.checked = experimentalSettings.bottomBgOver
        bottomBgOverSwitch.doOnCheckedChanged = {
            experimentalSettings.bottomBgOver = it
        }

        lightThemeSwitch = view.findViewById(R.id.light_theme)
        lightThemeSwitch.position = ListCell.Position.LAST
        lightThemeSwitch.checked = experimentalSettings.lightTheme
        lightThemeSwitch.doOnCheckedChanged = {
            experimentalSettings.lightTheme = it
        }
    }
}