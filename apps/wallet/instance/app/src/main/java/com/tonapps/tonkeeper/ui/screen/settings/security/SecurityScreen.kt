package com.tonapps.tonkeeper.ui.screen.settings.security

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.settings.passcode.ChangePasscodeScreen
import com.tonapps.tonkeeper.ui.screen.stories.safemode.SafeModeStoriesScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.passcode.PasscodeBiometric
import com.tonapps.wallet.data.settings.SafeModeState
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.getSpannable
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView
import uikit.widget.item.ItemIconView
import uikit.widget.item.ItemSwitchView

class SecurityScreen(wallet: WalletEntity): BaseWalletScreen<ScreenContext.Wallet>(R.layout.fragment_security, ScreenContext.Wallet(wallet)), BaseFragment.SwipeBack {

    override val viewModel: SecurityViewModel by viewModel()

    private val wallet: WalletEntity
        get() = screenContext.wallet

    private lateinit var headerView: HeaderView
    private lateinit var biometricView: ItemSwitchView
    private lateinit var lockScreenView: ItemSwitchView
    private lateinit var changePasscodeView: ItemIconView
    private lateinit var safeModeView: ItemSwitchView
    private lateinit var safeModeDisabledView: AppCompatTextView

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
        if (!wallet.hasPrivateKey) {
            biometricView.visibility = View.GONE
            view.findViewById<View>(R.id.lock_screen_description).visibility = View.GONE
        }


        val biometricDescriptionView = view.findViewById<View>(R.id.biometric_description)
        val biometricVisibility = if (wallet.hasPrivateKey && PasscodeBiometric.isAvailableOnDevice(requireContext())) {
            View.VISIBLE
        } else {
            View.GONE
        }
        biometricDescriptionView.visibility = biometricVisibility
        if (!wallet.hasPrivateKey) {
            biometricDescriptionView.visibility = View.GONE
        } else {
            biometricView.visibility = biometricVisibility
        }

        lockScreenView = view.findViewById(R.id.lock_screen)
        lockScreenView.setChecked(viewModel.lockScreen, false)
        lockScreenView.doOnCheckedChanged = { checked, byUser ->
            if (byUser) {
                viewModel.lockScreen = checked
            }
        }
        if (!wallet.hasPrivateKey) {
            lockScreenView.visibility = View.GONE
        }

        changePasscodeView = view.findViewById(R.id.change_passcode)
        changePasscodeView.setOnClickListener { navigation?.add(ChangePasscodeScreen.newInstance()) }
        if (!wallet.hasPrivateKey) {
            changePasscodeView.visibility = View.GONE
        }

        safeModeView = view.findViewById(R.id.safe_mode)
        safeModeView.setChecked(viewModel.isSafeModeEnabled(), false)
        safeModeView.doOnCheckedChanged = { checked, byUser ->
            if (byUser) {
                viewModel.setSafeModeState(if (checked) SafeModeState.Enabled else SafeModeState.Disabled)
            }
        }

        safeModeDisabledView = view.findViewById(R.id.safe_mode_disabled)
        safeModeDisabledView.text = requireContext().getSpannable(Localization.safe_mode_disabled)
        safeModeDisabledView.setOnClickListener {
            viewModel.setSafeModeState(SafeModeState.DisabledPermanently)
        }

        val safeModeDescriptionView = view.findViewById<AppCompatTextView>(R.id.safe_mode_description)
        safeModeDescriptionView.text = requireContext().getSpannable(Localization.safe_mode_description)
        safeModeDescriptionView.setOnClickListener {
            navigation?.add(SafeModeStoriesScreen.newInstance())
        }

        collectFlow(viewModel.safeModeFlow) { state ->
            if (state != SafeModeState.Disabled) {
                safeModeDisabledView.visibility = View.GONE
            } else {
                safeModeDisabledView.visibility = View.VISIBLE
            }
        }
    }

    private fun enableBiometric(value: Boolean) {
        viewModel.enableBiometric(requireContext(), value).catch {
            biometricView.setChecked(newChecked = false, byUser = true)
        }.launchIn(lifecycleScope)
    }

    companion object {
        fun newInstance(wallet: WalletEntity) = SecurityScreen(wallet)
    }
}