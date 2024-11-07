package com.tonapps.wallet.data.passcode

import android.content.Context
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.extensions.logError
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.passcode.source.PasscodeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PasscodeHelper(
    private val context: Context,
    private val accountRepository: AccountRepository
) {

    
    private val store: PasscodeStore by lazy { PasscodeStore(context) }

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
        return store.hasPinCode && store.compare(code)
        /*return if (store.hasPinCode && store.compare(code)) {
            true
        } else {
            isValidLegacy(context, code)
        }*/
    }

    private suspend fun isValidLegacy(context: Context, code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            accountRepository.importPrivateKeysFromRNLegacy(code)
            store.setPinCode(code)
            true
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            context.logError(e)
            false
        }
    }

}