package com.tonapps.signer.screen.storage

import android.content.Context
import androidx.lifecycle.ViewModel
import com.tonapps.security.vault.safeArea
import com.tonapps.signer.core.repository.KeyRepository
import com.tonapps.signer.password.Password
import com.tonapps.signer.vault.SignerVault
import kotlinx.coroutines.flow.take

class StorageViewModel(
    private val keyRepository: KeyRepository,
    private val vault: SignerVault,
): ViewModel() {

    suspend fun hasPassword() = vault.hasPassword()

    fun signOut(context: Context) = Password.authenticate(context).safeArea {
        keyRepository.deleteAll()
        vault.deleteAll()
    }.take(1)
}
