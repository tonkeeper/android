package com.tonapps.signer.password

import android.content.Context
import android.util.Log
import com.tonapps.signer.core.entities.PrivateKeyEntity
import com.tonapps.signer.password.ui.PasswordDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import security.vault.safeArea
import javax.crypto.SecretKey

object Password {

    private const val MIN_LENGTH = 4
    private const val UNLOCK_TIMEOUT = 60000L

    private var unlockTime = -1L

    fun isValid(value: CharArray): Boolean {
        return value.size >= MIN_LENGTH
    }

    fun setUnlock() {
        unlockTime = System.currentTimeMillis()
    }

    fun isUnlocked(): Boolean {
        if (0 >= unlockTime) {
            return false
        }
        return unlockTime + UNLOCK_TIMEOUT > System.currentTimeMillis()
    }

    fun authenticate(context: Context): Flow<SecretKey> = callbackFlow {
        val dialog = PasswordDialog(context, ::trySend)
        dialog.setOnDismissListener {
            if (isActive) {
                close(Throwable("User canceled"))
            } else {
                close()
            }
        }
        dialog.show()
        awaitClose { dialog.destroy() }
    }.flowOn(Dispatchers.Main).take(1)
}