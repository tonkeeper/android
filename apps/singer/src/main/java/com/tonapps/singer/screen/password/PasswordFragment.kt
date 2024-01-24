package com.tonapps.singer.screen.password

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import com.tonapps.singer.R
import com.tonapps.singer.core.password.Password
import com.tonapps.singer.core.SimpleState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.pinToBottomInsets
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView
import uikit.widget.LoaderView
import uikit.widget.password.PasswordInputView

class PasswordFragment: BaseFragment(R.layout.fragment_password) {

    companion object {

        private const val REQUEST_ID_KEY = "request_id"

        fun newInstance(requestId: String): PasswordFragment {
            val fragment = PasswordFragment()
            fragment.arguments = Bundle().apply {
                putString(REQUEST_ID_KEY, requestId)
            }
            return fragment
        }
    }

    private val requestId: String by lazy { requireArguments().getString(REQUEST_ID_KEY)!! }
    private val passwordViewModel: PasswordViewModel by viewModel()

    private lateinit var headerView: HeaderView
    private lateinit var passwordInput: PasswordInputView
    private lateinit var actionView: View
    private lateinit var doneButton: Button
    private lateinit var loaderView: LoaderView
    private lateinit var successView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.security_header)
        headerView.doOnCloseClick = { finish() }

        passwordInput = view.findViewById(R.id.security_password)

        actionView = view.findViewById(R.id.security_action)
        actionView.pinToBottomInsets()

        doneButton = view.findViewById(R.id.security_button)
        doneButton.setOnClickListener { sendPassword() }

        loaderView = view.findViewById(R.id.security_loader)
        successView = view.findViewById(R.id.security_success)

        passwordInput.doAfterTextChanged {
            if (it.isNullOrBlank()) {
                return@doAfterTextChanged
            }
            passwordInput.error = false
            doneButton.isEnabled = Password.isValid(it.toString())
        }

        passwordViewModel.uiState.onEach(::setState).launchIn(lifecycleScope)
    }

    private fun sendPassword() {
        val password = passwordInput.text.toString()
        if (!Password.isValid(password)) {
            applyErrorState()
            return
        }

        passwordViewModel.checkPassword(password)
    }

    private fun setState(state: SimpleState) {
        when (state) {
            SimpleState.Default -> applyDefaultState()
            SimpleState.Error -> applyErrorState()
            SimpleState.Success -> applySuccessState()
            SimpleState.Loading -> applyLoadingState()
        }
    }

    private fun applyErrorState() {
        passwordInput.failedPassword()
        applyDefaultState()
    }

    private fun applyLoadingState() {
        passwordInput.hideKeyboard()
        passwordInput.isEnabled = false

        doneButton.visibility = View.GONE
        loaderView.visibility = View.VISIBLE
    }

    private fun applySuccessState() {
        successView.visibility = View.VISIBLE
        loaderView.visibility = View.GONE
        doneButton.visibility = View.GONE

        navigation?.setFragmentResult(requestId)
        finish()
    }

    private fun applyDefaultState() {
        doneButton.visibility = View.VISIBLE
        loaderView.visibility = View.GONE
        successView.visibility = View.GONE

        passwordInput.focusWithKeyboard()
        passwordInput.isEnabled = true
    }

    override fun onResume() {
        super.onResume()
        passwordInput.focusWithKeyboard()
    }
}