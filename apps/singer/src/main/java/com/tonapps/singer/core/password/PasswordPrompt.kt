package com.tonapps.singer.core.password

import com.tonapps.singer.screen.password.PasswordFragment
import uikit.base.BaseFragment
import uikit.navigation.Navigation
import uikit.navigation.Navigation.Companion.navigation

class PasswordPrompt(
    private val fragment: BaseFragment,
    private val callback: AuthenticationCallback
) {

    private val requestId = "password${System.currentTimeMillis()}"

    private val navigation: Navigation?
        get() = fragment.navigation

    init {
        navigation?.setFragmentResultListener(requestId) { _ ->
            callback.onAuthenticationResult(Password.Result.Success)
        }
    }

    fun authenticate() {
        navigation?.add(PasswordFragment.newInstance(requestId))
    }

    open class AuthenticationCallback {
        open fun onAuthenticationResult(result: Password.Result) {

        }
    }
}