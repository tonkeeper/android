package com.tonapps.wallet.data.passcode

import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.passcode.source.PasscodeStore

class PasscodeHelper(
    private val store: PasscodeStore,
    private val accountRepository: AccountRepository,
) {

    val hasPinCode: Boolean
        get() = store.hasPinCode

    suspend fun change(old: String, new: String): Boolean {
        if (!store.hasPinCode && !isValidLegacy(old)) {
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

    suspend fun isValid(code: String): Boolean {
        return if (store.hasPinCode && store.compare(code)) {
            true
        } else {
            isValidLegacy(code)
        }
    }

    private suspend fun isValidLegacy(code: String): Boolean {
        try {
            accountRepository.importPrivateKeysFromRNLegacy(code)
            store.setPinCode(code)
            return true
        } catch (e: Throwable) {
            return false
        }
    }

}