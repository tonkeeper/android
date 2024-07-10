package com.tonapps.tonkeeper.ui.screen.settings.security

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.ui.screen.settings.passcode.ChangePasscodeScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.passcode.PasscodeBiometric
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView
import uikit.widget.item.ItemIconView
import uikit.widget.item.ItemSwitchView

class SecurityScreen: BaseFragment(R.layout.fragment_security), BaseFragment.SwipeBack {

    private val securityViewModel: SecurityViewModel by viewModel()

    private lateinit var headerView: HeaderView
    private lateinit var biometricView: ItemSwitchView
    private lateinit var lockScreenView: ItemSwitchView
    private lateinit var changePasscodeView: ItemIconView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        biometricView = view.findViewById(R.id.biometric)
        biometricView.setChecked(securityViewModel.biometric, false)
        biometricView.doOnCheckedChanged = { checked, byUser ->
            if (byUser) {
                securityViewModel.biometric = checked
            }
        }

        val biometricDescriptionView = view.findViewById<View>(R.id.biometric_description)
        val biometricVisibility = if (PasscodeBiometric.isAvailableOnDevice(requireContext())) {
            View.VISIBLE
        } else {
            View.GONE
        }
        biometricView.visibility = biometricVisibility
        biometricDescriptionView.visibility = biometricVisibility

        lockScreenView = view.findViewById(R.id.lock_screen)
        lockScreenView.setChecked(securityViewModel.lockScreen, false)
        lockScreenView.doOnCheckedChanged = { checked, byUser ->
            if (byUser) {
                securityViewModel.lockScreen = checked
            }
        }

        changePasscodeView = view.findViewById(R.id.change_passcode)
        changePasscodeView.setOnClickListener { navigation?.add(ChangePasscodeScreen.newInstance()) }
    }

    companion object {
        fun newInstance() = SecurityScreen()
    }
}