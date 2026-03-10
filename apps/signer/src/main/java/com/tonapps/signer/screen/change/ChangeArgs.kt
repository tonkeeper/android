package com.tonapps.signer.screen.change

import androidx.lifecycle.SavedStateHandle

internal class ChangeArgs(
    private val savedStateHandle: SavedStateHandle
) {

    private companion object {
        private const val CURRENT_PASSWORD_KEY = "current_password"
        private const val NEW_PASSWORD_KEY = "new_password"
        private const val CONFIRM_PASSWORD_KEY = "confirm_password"
    }

    var currentPassword: CharArray?
        get() = savedStateHandle[CURRENT_PASSWORD_KEY]
        set(value) {
            savedStateHandle[CURRENT_PASSWORD_KEY] = value
        }

    var newPassword: CharArray?
        get() = savedStateHandle[NEW_PASSWORD_KEY]
        set(value) {
            savedStateHandle[NEW_PASSWORD_KEY] = value
        }

    var confirmPassword: CharArray?
        get() = savedStateHandle[CONFIRM_PASSWORD_KEY]
        set(value) {
            savedStateHandle[CONFIRM_PASSWORD_KEY] = value
        }
}