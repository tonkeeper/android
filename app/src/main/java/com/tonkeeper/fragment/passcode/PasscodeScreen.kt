package com.tonkeeper.fragment.passcode

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.tonkeeper.App
import com.tonkeeper.R
import com.tonkeeper.fragment.currency.CurrencyScreenFeature
import com.tonkeeper.fragment.currency.CurrencyScreenState
import uikit.base.fragment.BaseFragment
import com.tonkeeper.widget.NumPadView
import com.tonkeeper.widget.PasscodeView
import uikit.mvi.UiScreen
import uikit.navigation.Navigation.Companion.nav
import java.util.concurrent.Executor

class PasscodeScreen: UiScreen<PasscodeScreenState, PasscodeScreenEffect, PasscodeScreenFeature>(R.layout.fragment_passcode) {

    companion object {

        private const val STATE_KEY = "state"

        const val STATE_DEFAULT = 0
        const val STATE_CREATE = 1
        const val STATE_CHECK = 2

        fun newInstance(state: Int = STATE_DEFAULT): PasscodeScreen {
            if (state == STATE_DEFAULT && !App.passcode.hasPinCode) {
                return newInstance(STATE_CREATE)
            }
            val fragment = PasscodeScreen()
            fragment.arguments = Bundle().apply {
                putInt(STATE_KEY, state)
            }
            return fragment
        }
    }

    private val state: Int by lazy {
        arguments?.getInt(STATE_KEY) ?: STATE_DEFAULT
    }

    override val feature: PasscodeScreenFeature by viewModels(
        factoryProducer = { PasscodeScreenFeature.factory(state) }
    )

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    private lateinit var titleView: TextView
    private lateinit var passcodeView: PasscodeView
    private lateinit var numPadView: NumPadView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        executor = ContextCompat.getMainExecutor(requireContext())
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    nav()?.init(true)
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_name))
            .setNegativeButtonText(getString(R.string.cancel))
            .build()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        titleView = view.findViewById(R.id.title)
        titleView.setText(headerTitle())

        passcodeView = view.findViewById(R.id.passcode)

        numPadView = view.findViewById(R.id.num_pad)
        numPadView.doOnNumberClick = { number ->
            feature.addNumber(number)
        }
        numPadView.doOnBackspaceClick = {
            feature.backspace()
        }
    }

    override fun newUiEffect(effect: PasscodeScreenEffect) {
        when(effect) {
            is PasscodeScreenEffect.ReEnter -> {
                nav()?.replace(newInstance(STATE_CHECK), true)
            }
            is PasscodeScreenEffect.Failure -> {
                passcodeView.setError()
            }
            is PasscodeScreenEffect.Success -> {
                passcodeView.setSuccess()
            }
            is PasscodeScreenEffect.Default -> {
                passcodeView.setDefault()
            }
            is PasscodeScreenEffect.Close -> {
                nav()?.init(true)
            }
        }
    }

    override fun newUiState(state: PasscodeScreenState) {
        passcodeView.setCount(state.numbers.size)
        numPadView.backspace = state.backspace
    }

    override fun onResume() {
        super.onResume()
        if (App.settings.biometric) {
            biometricPrompt.authenticate(promptInfo)
        }
    }

    @StringRes
    private fun headerTitle(): Int {
        return when (state) {
            STATE_CHECK -> R.string.passcode_re_enter
            STATE_CREATE -> R.string.passcode_create
            else -> R.string.passcode_enter
        }
    }

}