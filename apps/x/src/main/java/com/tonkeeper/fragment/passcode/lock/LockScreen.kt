package com.tonkeeper.fragment.passcode.lock

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeperx.R
import com.tonkeeper.App
import com.tonkeeper.PasscodeManager
import kotlinx.coroutines.delay
import uikit.widget.NumPadView
import kotlinx.coroutines.launch
import uikit.base.BaseFragment
import uikit.extensions.startSnakeAnimation
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView
import uikit.widget.PinInputView

class LockScreen: BaseFragment(R.layout.fragment_passcode) {

    companion object {

        private const val REQUEST_KEY = "request_key"

        fun newInstance(requestKey: String? = null): LockScreen {
            val screen = LockScreen()
            screen.arguments = Bundle().apply {
                putString(REQUEST_KEY, requestKey)
            }
            return screen
        }
    }

    private val authenticationCallback = object : BiometricPrompt.AuthenticationCallback() {

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            success()
        }
    }

    private val requestKey: String? by lazy { arguments?.getString(REQUEST_KEY) }

    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var headerView: HeaderView
    private lateinit var titleView: TextView
    private lateinit var passcodeView: PinInputView
    private lateinit var numPadView: NumPadView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        biometricPrompt = BiometricPrompt(this, mainExecutor, authenticationCallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }
        if (requestKey == null) {
            headerView.visibility = View.GONE
        }

        titleView = view.findViewById(R.id.title)
        titleView.setText(R.string.passcode_enter)

        passcodeView = view.findViewById(R.id.passcode)
        passcodeView.doOnCodeUpdated = {
            val size = it.length
            val fullCode = size == PasscodeManager.CODE_LENGTH
            numPadView.backspace = size > 0
            if (fullCode) {
                checkCode(it)
            } else {
                numPadView.isEnabled = true
            }
        }

        numPadView = view.findViewById(R.id.num_pad)
        numPadView.doOnNumberClick = { number ->
            passcodeView.appendNumber(number)
        }
        numPadView.doOnBackspaceClick = {
            passcodeView.removeLastNumber()
        }
    }

    private fun rejectCode() {
        passcodeView.setError()
        passcodeView.startSnakeAnimation(
            duration = PinInputView.animationDuration * PasscodeManager.CODE_LENGTH
        )

        numPadView.isEnabled = true
    }

    private fun checkCode(code: String) {
        numPadView.isEnabled = false

        lifecycleScope.launch {
            if (App.passcode.compare(code)) {
                passcodeView.setSuccess()
                delay(320)
                success()
            } else {
                rejectCode()
            }
        }
    }

    private fun success() {
        if (requestKey == null) {
            navigation?.remove(this)
        } else {
            successWithResult(requestKey!!)
        }
    }

    private fun successWithResult(key: String) {
        navigation?.let {
            it.setFragmentResult(key)
            it.remove(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (App.settings.biometric) {
            requestBiometric()
        }
    }

    override fun onBackPressed(): Boolean {
        if (requestKey == null) {
            activity?.finish()
            return false
        }
        return true
    }

    private fun requestBiometric() {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_name))
            .setNegativeButtonText(getString(R.string.cancel))
            .setConfirmationRequired(false)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}