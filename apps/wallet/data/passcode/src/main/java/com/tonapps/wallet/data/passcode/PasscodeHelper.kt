package com.tonapps.wallet.data.passcode

import android.content.Context
import android.graphics.Color
import com.tonapps.extensions.bestMessage
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.passcode.source.PasscodeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uikit.navigation.Navigation.Companion.navigation

class PasscodeHelper(
    private val store: PasscodeStore,
    private val accountRepository: AccountRepository,
) {

    val hasPinCode: Boolean
        get() = store.hasPinCode

    suspend fun change(context: Context, old: String, new: String): Boolean {
        if (!store.hasPinCode && !isValidLegacy(context, old)) {
            return false
        }
        return store.change(old, new)
    }

    suspend fun save(code: String) {
        store.setPinCode(code)
    }

    suspend fun reset() {
        store.clearPinCode()
    }

    suspend fun isValid(context: Context, code: String): Boolean {
        return if (store.hasPinCode && store.compare(code)) {
            true
        } else {
            isValidLegacy(context, code)
        }
    }

    private suspend fun isValidLegacy(context: Context, code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            accountRepository.importPrivateKeysFromRNLegacy(code)
            store.setPinCode(code)
            true
        } catch (e: Throwable) {
            context.navigation?.toast(e.bestMessage, false, Color.RED)
            false
        }
    }

}