package com.tonapps.tonkeeper.ui.screen.migration

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeperx.R
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow

class MigrationScreen: BaseWalletScreen<ScreenContext.None>(R.layout.fragment_migration, ScreenContext.None), BaseFragment.SwipeBack {

    override val fragmentName: String = "MigrationScreen"

    override val viewModel: MigrationViewModel by viewModel()

    private lateinit var legacyStateView: AppCompatTextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        legacyStateView = view.findViewById(R.id.legacy_state)
        collectFlow(viewModel.legacyStateFlow, ::applyLegacyState)
    }

    private fun applyLegacyState(state: MigrationViewModel.LegacyState) {
        val lines = mutableListOf<String>()
        lines.add("WalletsCount: ${state.walletsCount}")
        lines.add("Lockscreen: ${state.lockScreenEnabled}")
        lines.add("Biometrics: ${state.biometryEnabled}")
        lines.add("SelectedId: ${state.selectedIdentifier}")
        legacyStateView.text = lines.joinToString("\n")
    }

    companion object {

        fun newInstance() = MigrationScreen()
    }
}