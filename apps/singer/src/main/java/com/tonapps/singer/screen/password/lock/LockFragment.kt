package com.tonapps.singer.screen.password.lock

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.tonapps.singer.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.doOnOnApplyWindowInsets
import uikit.extensions.focusWidthKeyboard
import uikit.extensions.hideKeyboard
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView
import uikit.widget.LoaderView
import uikit.widget.PasswordInputView

class LockFragment: BaseFragment(R.layout.fragment_lock) {

    companion object {
        private const val REQUEST_KEY = "request"

        fun newInstance(requestKey: String): LockFragment {
            val fragment = LockFragment()
            fragment.arguments = Bundle().apply {
                putString(REQUEST_KEY, requestKey)
            }
            return fragment
        }
    }

    private val requestKey: String by lazy { requireArguments().getString(REQUEST_KEY)!! }
    private val lockViewModel: LockViewModel by viewModel()

    private lateinit var headerView: HeaderView
    private lateinit var passwordView: PasswordInputView
    private lateinit var actionView: View
    private lateinit var continueButton: Button
    private lateinit var loaderView: LoaderView
    private lateinit var successView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        passwordView = view.findViewById(R.id.password)

        actionView = view.findViewById(R.id.action)
        continueButton = view.findViewById(R.id.ok)
        loaderView = view.findViewById(R.id.loader)
        successView = view.findViewById(R.id.success)

        passwordView.doOnTextChanged { _, _, _, _ ->
            continueButton.isEnabled = !passwordView.isEmpty
            passwordView.error = false
        }

        continueButton.setOnClickListener { checkPassword() }

        view.doOnOnApplyWindowInsets {
            val insetsIme = it.getInsets(WindowInsetsCompat.Type.ime())
            actionView.translationY = -insetsIme.bottom.toFloat()
            it
        }
    }

    private fun checkPassword() {
        val text = passwordView.text.toString()
        lockViewModel.checkPassword(text).onEach {
            when (it) {
                PasswordState.Checking -> applyCheckingState()
                PasswordState.Error -> applyErrorState()
                PasswordState.Success -> {
                    applySuccessState()
                    delay(500)
                    closeSuccess()
                }
                else -> applyDefaultState()
            }
        }.launchIn(lifecycleScope)
    }

    private fun closeSuccess() {
        navigation?.setFragmentResult(requestKey)
        finish()
    }

    private fun applyCheckingState() {
        passwordView.hideKeyboard()
        applyLoading()
    }

    private fun applyErrorState() {
        passwordView.error = true
        passwordView.focusWidthKeyboard()
        applyDefaultState()
    }

    private fun applySuccessState() {
        successView.visibility = View.VISIBLE
        loaderView.visibility = View.GONE
        continueButton.visibility = View.GONE
    }

    private fun applyLoading() {
        continueButton.visibility = View.GONE
        loaderView.visibility = View.VISIBLE
    }

    private fun applyDefaultState() {
        continueButton.visibility = View.VISIBLE
        loaderView.visibility = View.GONE
        successView.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        passwordView.focusWidthKeyboard()
    }
}