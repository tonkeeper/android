package com.tonkeeper.fragment.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.R
import com.tonkeeper.fragment.settings.list.SettingsAdapter
import com.tonkeeper.fragment.settings.list.SettingsItemDecoration
import com.tonkeeper.uikit.mvi.UiScreen

class SettingsScreen: UiScreen<SettingsScreenState, SettingsScreenFeature>(R.layout.fragment_settings) {

    companion object {
        fun newInstance() = SettingsScreen()
    }

    override val viewModel: SettingsScreenFeature by viewModels()

    private lateinit var listView: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView = view.findViewById(R.id.list)
        listView.addItemDecoration(SettingsItemDecoration(view.context))
    }

    override fun newUiState(state: SettingsScreenState) {
        listView.adapter = SettingsAdapter(state.items)
    }
}