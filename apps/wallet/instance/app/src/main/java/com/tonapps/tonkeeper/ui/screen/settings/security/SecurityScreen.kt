package com.tonapps.tonkeeper.ui.screen.settings.security

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.settings.passcode.ChangePasscodeScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.passcode.PasscodeBiometric
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView
import uikit.widget.item.ItemIconView
import uikit.widget.item.ItemSwitchView

class SecurityScreen: BaseWalletScreen<ScreenContext.None>(R.layout.fragment_security, ScreenContext.None), BaseFragment.SwipeBack {

    override val viewModel: SecurityViewModel by viewModel()

    private lateinit var headerView: HeaderView
    private lateinit var biometricView: ItemSwitchView
    private lateinit var lockScreenView: ItemSwitchView
    private lateinit var changePasscodeView: ItemIconView
    private lateinit var verifiedNFTsView: ItemSwitchView
    private lateinit var verifiedTokenView: ItemSwitchView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        biometricView = view.findViewById(R.id.biometric)
        biometricView.setChecked(viewModel.biometric, false)
        biometricView.doOnCheckedChanged = { checked, byUser ->
            if (byUser) {
                enableBiometric(checked)
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
        lockScreenView.setChecked(viewModel.lockScreen, false)
        lockScreenView.doOnCheckedChanged = { checked, byUser ->
            if (byUser) {
                viewModel.lockScreen = checked
            }
        }

        changePasscodeView = view.findViewById(R.id.change_passcode)
        changePasscodeView.setOnClickListener { navigation?.add(ChangePasscodeScreen.newInstance()) }

        verifiedNFTsView = view.findViewById(R.id.verified_nfts)
        verifiedNFTsView.setChecked(viewModel.onlyVerifyNFTs, false)
        verifiedNFTsView.doOnCheckedChanged = { checked, byUser ->
            if (byUser) {
                viewModel.onlyVerifyNFTs = checked
            }
        }

        verifiedTokenView = view.findViewById(R.id.verified_token)
        verifiedTokenView.setChecked(viewModel.onlyVerifyTokens, false)
        verifiedTokenView.doOnCheckedChanged = { checked, byUser ->
            if (byUser) {
                viewModel.onlyVerifyTokens = checked
            }
        }
    }

    private fun enableBiometric(value: Boolean) {
        viewModel.enableBiometric(requireContext(), value).catch {
            biometricView.setChecked(newChecked = false, byUser = true)
        }.launchIn(lifecycleScope)
    }

    companion object {
        fun newInstance() = SecurityScreen()
    }
}