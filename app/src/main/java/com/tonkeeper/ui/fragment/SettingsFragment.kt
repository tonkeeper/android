package com.tonkeeper.ui.fragment

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.SwitchCompat
import com.tonkeeper.AppSettings
import com.tonkeeper.R

class SettingsFragment: BaseFragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val switchRussianLanguage = view.findViewById<SwitchCompat>(R.id.russian_language)
        switchRussianLanguage.isChecked = AppSettings.russianLanguage
        switchRussianLanguage.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.russianLanguage = isChecked
        }

        val switchAppsTabs = view.findViewById<SwitchCompat>(R.id.apps_tabs)
        switchAppsTabs.isChecked = AppSettings.appsTabs
        switchAppsTabs.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.appsTabs = isChecked
        }

        val switchSingleColumn = view.findViewById<SwitchCompat>(R.id.single_column)
        switchSingleColumn.isChecked = AppSettings.singleColumn
        switchSingleColumn.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.singleColumn = isChecked
        }

    }
}