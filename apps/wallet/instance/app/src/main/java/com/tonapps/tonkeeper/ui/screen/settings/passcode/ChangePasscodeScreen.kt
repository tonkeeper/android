package com.tonapps.tonkeeper.ui.screen.settings.passcode

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.passcode.ui.PasscodeView
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.widget.HeaderView

class ChangePasscodeScreen: BaseWalletScreen<ScreenContext.None>(R.layout.fragment_change_passcode, ScreenContext.None), BaseFragment.SwipeBack {

    override val fragmentName: String = "ChangePasscodeScreen"

    override val viewModel: ChangePasscodeViewModel by viewModel()

    private lateinit var headerView: HeaderView
    private lateinit var passcodeView: PasscodeView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        passcodeView = view.findViewById(R.id.passcode)
        collectFlow(viewModel.stepFlow, ::setStep)
        collectFlow(viewModel.errorFlow) { applyError() }
    }

    private fun setStep(step: ChangePasscodeViewModel.Step) {
        when (step) {
            ChangePasscodeViewModel.Step.Current -> applyCurrent()
            ChangePasscodeViewModel.Step.New -> applyNew()
            ChangePasscodeViewModel.Step.Confirm -> applyConfirm()
            ChangePasscodeViewModel.Step.Saved -> finish()
        }
    }

    private fun applyCurrent() {
        passcodeView.clear()
        passcodeView.setTitle(Localization.passcode_current)
        passcodeView.doOnCheck = {
            viewModel.checkCurrent(requireContext(), it)
        }
    }

    private fun applyNew() {
        passcodeView.clear()
        passcodeView.setTitle(Localization.passcode_new)
        passcodeView.doOnCheck = {
            viewModel.setNew(it)
        }
    }

    private fun applyConfirm() {
        passcodeView.clear()
        passcodeView.setTitle(Localization.passcode_re_enter)
        passcodeView.doOnCheck = {
            viewModel.save(requireContext(), it)
        }
    }

    private fun applyError() {
        passcodeView.setError()
    }

    companion object {
        fun newInstance() = ChangePasscodeScreen()
    }

}