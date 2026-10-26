package com.tonapps.tonkeeper.ui.screen.settings.security

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.tonapps.uikit.list.ListCell
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.settings.passcode.ChangePasscodeScreen
import com.tonapps.tonkeeper.ui.screen.stories.safemode.SafeModeStoriesScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.passcode.PasscodeBiometric
import com.tonapps.wallet.data.settings.SafeModeState
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.getSpannable
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView
import uikit.widget.item.ItemIconView
import uikit.widget.item.ItemSwitchView
import uikit.extensions.setMoonOnClickListener

class SecurityScreen(wallet: WalletEntity): BaseWalletScreen<ScreenContext.Wallet>(R.layout.fragment_security, ScreenContext.Wallet(wallet)), BaseFragment.SwipeBack {

    override val fragmentName: String = "SecurityScreen"

    override val viewModel: SecurityViewModel by viewModel()

    private val wallet: WalletEntity
        get() = screenContext.wallet

    private lateinit var headerView: HeaderView
    private lateinit var biometricView: ItemSwitchView
    private lateinit var biometricDescriptionView: View
    private lateinit var lockScreenView: ItemSwitchView
    private lateinit var lockScreenDescriptionView: View
    private var turnOffLockScreenDialog: TurnOffLockScreenDialog? = null
    private lateinit var changePasscodeView: ItemIconView
    private lateinit var setPasscodeView: ItemIconView
    private lateinit var setPasscodeDescriptionView: View
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

        biometricDescriptionView = view.findViewById(R.id.biometric_description)

        lockScreenView = view.findViewById(R.id.lock_screen)
        lockScreenView.setChecked(viewModel.lockScreenEnabled, false)
        lockScreenView.doOnCheckedChanged = { checked, byUser ->
            if (byUser) {
                setLockScreenEnabled(checked)
            }
        }

        lockScreenDescriptionView = view.findViewById(R.id.lock_screen_description)

        changePasscodeView = view.findViewById(R.id.change_passcode)
        changePasscodeView.setMoonOnClickListener { navigation?.add(ChangePasscodeScreen.newInstance()) }

        setPasscodeView = view.findViewById(R.id.set_passcode)
        setPasscodeView.setMoonOnClickListener {
            navigation?.addForResult(ChangePasscodeScreen.newInstance(create = true)) {
                viewModel.refreshPasscodeState()
            }
        }

        setPasscodeDescriptionView = view.findViewById(R.id.set_passcode_description)

        safeModeView = view.findViewById(R.id.safe_mode)
        safeModeView.setChecked(viewModel.isSafeModeEnabled(wallet), false)
        safeModeView.doOnCheckedChanged = { checked, byUser ->
            if (byUser) {
                viewModel.setSafeModeState(wallet, if (checked) {
                    SafeModeState.Enabled
                } else {
                    SafeModeState.Disabled
                })
            }
        }

        safeModeDisabledView = view.findViewById(R.id.safe_mode_disabled)
        safeModeDisabledView.text = requireContext().getSpannable(Localization.safe_mode_disabled)
        safeModeDisabledView.setMoonOnClickListener {
            viewModel.setSafeModeState(wallet, SafeModeState.DisabledPermanently)
        }

        val safeModeDescriptionView = view.findViewById<AppCompatTextView>(R.id.safe_mode_description)
        safeModeDescriptionView.text = requireContext().getSpannable(Localization.safe_mode_description)
        safeModeDescriptionView.setMoonOnClickListener {
            navigation?.add(SafeModeStoriesScreen.newInstance())
        }

        val biometricAvailable = PasscodeBiometric.isAvailableOnDevice(requireContext())
        changePasscodeView.position = if (biometricAvailable) {
            ListCell.Position.FIRST
        } else {
            ListCell.Position.SINGLE
        }
        changePasscodeView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = if (biometricAvailable) {
                0
            } else {
                requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium)
            }
        }

        collectFlow(viewModel.hasPasscodeFlow) { hasPasscode ->
            if (hasPasscode && biometricAvailable) {
                biometricView.visibility = View.VISIBLE
                biometricDescriptionView.visibility = View.VISIBLE
            } else {
                biometricView.visibility = View.GONE
                biometricDescriptionView.visibility = View.GONE
            }

            if (hasPasscode && viewModel.isLockScreenOptional) {
                lockScreenView.visibility = View.VISIBLE
                lockScreenDescriptionView.visibility = View.VISIBLE
            } else {
                lockScreenView.visibility = View.GONE
                lockScreenDescriptionView.visibility = View.GONE
            }

            if (hasPasscode) {
                changePasscodeView.visibility = View.VISIBLE
                setPasscodeView.visibility = View.GONE
                setPasscodeDescriptionView.visibility = View.GONE
            } else {
                changePasscodeView.visibility = View.GONE
                setPasscodeView.visibility = View.VISIBLE
                setPasscodeDescriptionView.visibility = View.VISIBLE
            }
        }

        collectFlow(viewModel.safeModeFlow(wallet)) { state ->
            if (state != SafeModeState.Disabled) {
                safeModeDisabledView.visibility = View.GONE
            } else {
                safeModeDisabledView.visibility = View.VISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPasscodeState()
    }

    private fun setLockScreenEnabled(enabled: Boolean) {
        if (enabled) {
            viewModel.lockScreenEnabled = true
            return
        }

        lockScreenView.setChecked(newChecked = true, byUser = false)
        if (turnOffLockScreenDialog?.isShowing == true) {
            return
        }

        val dialog = TurnOffLockScreenDialog(requireContext()) {
            viewModel.lockScreenEnabled = false
            lockScreenView.setChecked(newChecked = false, byUser = false)
        }
        turnOffLockScreenDialog = dialog
        dialog.show()
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
