package com.tonapps.tonkeeper.ui.screen.init.step

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.getDimensionPixelSize
import uikit.widget.NumPadView
import uikit.widget.PinInputView

class PasscodeScreen: BaseFragment(R.layout.fragment_init_passcode)  {

    enum class Mode {
        Create,
        ReEnter,
        Enter,
    }

    override val fragmentName: String = "PasscodeScreen"

    private val initViewModel: InitViewModel by viewModel(ownerProducer = { requireParentFragment() })
    private val mode: Mode by lazy { Mode.valueOf(requireArguments().getString(ARG_MODE, Mode.Create.name)) }

    private lateinit var pinInputView: PinInputView
    private lateinit var numPadView: NumPadView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val titleView = view.findViewById<AppCompatTextView>(R.id.title)
        titleView.setText(when (mode) {
            Mode.Create -> Localization.passcode_create
            Mode.ReEnter -> Localization.passcode_re_enter
            Mode.Enter -> Localization.passcode_enter
        })

        pinInputView = view.findViewById(R.id.passcode)

        numPadView = view.findViewById(R.id.num_pad)
        numPadView.doOnBackspaceClick = {
            pinInputView.removeLastNumber()
            numPadView.backspace = pinInputView.count != 0
        }
        numPadView.doOnNumberClick = {
            pinInputView.appendNumber(it)
            numPadView.backspace = true
            if (pinInputView.count == 4) {
                setPasscode(pinInputView.code)
            }
        }

        if (mode == Mode.Enter || mode == Mode.ReEnter) {
            collectFlow(initViewModel.wrongPasscodeFlow) {
                numPadView.backspace = false
                pinInputView.setError()
                if (mode == Mode.ReEnter) {
                    numPadView.isEnabled = false
                }
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val insetsNav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val bottom = insetsNav + requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium)
            numPadView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = bottom
            }
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        numPadView.isEnabled = true
    }

    private fun setPasscode(code: String) {
        when (mode) {
            Mode.Create -> initViewModel.setPasscode(code)
            Mode.ReEnter -> initViewModel.reEnterPasscode(code)
            Mode.Enter -> initViewModel.enterExistingPasscode(code)
        }
    }

    companion object {
        private const val ARG_MODE = "mode"

        fun newInstance(mode: Mode): PasscodeScreen {
            val fragment = PasscodeScreen()
            fragment.arguments = Bundle().apply {
                putString(ARG_MODE, mode.name)
            }
            return fragment
        }
    }
}