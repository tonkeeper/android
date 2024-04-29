package com.tonapps.signer.password.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import androidx.core.view.doOnLayout
import com.tonapps.signer.R
import com.tonapps.signer.password.Password
import uikit.extensions.pinToBottomInsets
import uikit.widget.LoaderView
import uikit.widget.password.PasswordInputView

class PasswordView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    var doOnPassword: ((password: CharArray) -> Unit)? = null

    private val passwordInput: PasswordInputView
    private val actionView: View
    private val doneButton: Button
    private val loaderView: LoaderView
    private val successView: View

    init {
        inflate(context, R.layout.view_password, this)

        passwordInput = findViewById(R.id.password_input)
        passwordInput.doOnDone = { sendPassword() }

        actionView = findViewById(R.id.password_action)
        actionView.pinToBottomInsets()

        doneButton = findViewById(R.id.password_button)
        doneButton.setOnClickListener { sendPassword() }

        loaderView = findViewById(R.id.password_loader)
        successView = findViewById(R.id.password_success)

        passwordInput.doAfterValueChanged {
            if (it.isEmpty()) {
                doneButton.isEnabled = false
                return@doAfterValueChanged
            }
            passwordInput.error = false
            doneButton.isEnabled = Password.isValid(it)
        }
    }

    private fun sendPassword() {
        val value = passwordInput.value
        if (!Password.isValid(value)) {
            applyErrorState()
            return
        }

        doOnPassword?.invoke(value)
    }

    fun focus() {
        doOnLayout {
            passwordInput.focusWithKeyboard()
        }
    }

    fun hideKeyboard() {
        passwordInput.hideKeyboard()
    }

    fun reset() {
        applyDefaultState()
        doOnLayout {
            passwordInput.clear()
        }
    }

    fun applyErrorState() {
        doneButton.isEnabled = false
        passwordInput.failedPassword()
        applyDefaultState()
    }

    fun applyLoadingState() {
        passwordInput.hideKeyboard()
        passwordInput.isEnabled = false

        doneButton.visibility = View.GONE
        loaderView.visibility = View.VISIBLE
    }

    fun applySuccessState() {
        successView.visibility = View.VISIBLE
        loaderView.visibility = View.GONE
        doneButton.visibility = View.GONE
    }

    fun applyDefaultState() {
        doneButton.visibility = View.VISIBLE
        loaderView.visibility = View.GONE
        successView.visibility = View.GONE

        passwordInput.focusWithKeyboard()
        passwordInput.isEnabled = true
    }
}