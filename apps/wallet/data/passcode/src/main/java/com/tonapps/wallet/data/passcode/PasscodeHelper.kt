package com.tonapps.wallet.data.passcode

import android.content.Context
import android.util.Log
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.passcode.source.PasscodeStore

class PasscodeHelper(
    private val context: Context,
    private val accountRepository: AccountRepository
) {

    private val store: PasscodeStore by lazy { PasscodeStore(context) }

    val hasPinCode: Boolean
        get() = store.hasPinCode

    suspend fun change(context: Context, old: String, new: String): Boolean {
        if (!isValid(context, old)) {
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
        if (store.hasPinCode) {
            return store.compare(code)
        }
        if (accountRepository.importPrivateKeysFromRNLegacy(code)) {
            store.setPinCode(code)
            return true
        }
        return false
    }

}