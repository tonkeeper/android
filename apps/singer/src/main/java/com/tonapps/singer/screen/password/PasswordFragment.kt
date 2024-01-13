package com.tonapps.singer.screen.password

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.doAfterTextChanged
import com.tonapps.singer.R
import uikit.base.BaseFragment
import uikit.extensions.focusWidthKeyboard
import uikit.widget.PasswordInputView

open class PasswordFragment: BaseFragment(R.layout.fragment_password) {

    companion object {

        private const val MIN_PASSWORD_LENGTH = 6
        private const val MAX_PASSWORD_LENGTH = 24

        fun newInstance() = PasswordFragment()

        private fun isValidPassword(value: String): Boolean {
            return value.length in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH
        }
    }

    private lateinit var passwordInput: PasswordInputView
    private lateinit var titleView: AppCompatTextView
    private lateinit var doneButton: Button

    var title: String?
        get() = titleView.text.toString()
        set(value) {
            titleView.text = value
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        passwordInput = view.findViewById(R.id.password)

        titleView = view.findViewById(R.id.title)

        doneButton = view.findViewById(R.id.done)
        doneButton.setOnClickListener { sendPassword() }

        passwordInput.doAfterTextChanged {
            passwordInput.error = false
            doneButton.isEnabled = isValidPassword(it.toString())
        }
    }

    fun focus() {
        passwordInput.focusWidthKeyboard()
    }

    private fun sendPassword() {
        val password = passwordInput.text.toString()
        if (!isValidPassword(password)) {
            passwordInput.error = true
            return
        }
        passwordInput.error = !onPasswordSent(password)
    }

    open fun onPasswordSent(password: String): Boolean {
        return true
    }
}