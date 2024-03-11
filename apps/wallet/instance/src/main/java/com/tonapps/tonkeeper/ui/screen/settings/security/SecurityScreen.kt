package com.tonapps.tonkeeper.ui.screen.settings.security

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeperx.R
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.widget.HeaderView
import uikit.widget.item.ItemIconView
import uikit.widget.item.ItemSwitchView

class SecurityScreen: BaseFragment(R.layout.fragment_security), BaseFragment.SwipeBack {

    private val securityViewModel: SecurityViewModel by viewModel()

    private lateinit var headerView: HeaderView
    private lateinit var biometricView: ItemSwitchView
    private lateinit var lockScreenView: ItemSwitchView
    private lateinit var recoveryPhraseView: ItemIconView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        biometricView = view.findViewById(R.id.biometric)
        biometricView.checked = com.tonapps.tonkeeper.App.settings.biometric
        biometricView.doOnCheckedChanged = { com.tonapps.tonkeeper.App.settings.biometric = it }

        lockScreenView = view.findViewById(R.id.lock_screen)
        lockScreenView.checked = com.tonapps.tonkeeper.App.settings.lockScreen
        lockScreenView.doOnCheckedChanged = { securityViewModel.enableLockScreen(it) }

        recoveryPhraseView = view.findViewById(R.id.recovery_phrase)
        recoveryPhraseView.setOnClickListener {

        }
    }

    companion object {
        fun newInstance() = SecurityScreen()
    }
}