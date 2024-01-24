package com.tonapps.singer.screen.key

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.singer.core.KeyEntity
import com.tonapps.singer.core.TonkeeperApp
import com.tonapps.singer.core.account.AccountRepository
import core.qr.QRBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KeyViewModel(
    private val id: Long,
    private val accountRepository: AccountRepository
): ViewModel() {

    val keyEntity = accountRepository.getKey(id)

    fun delete() {
        viewModelScope.launch {
            accountRepository.deleteKey(id)
        }
    }

    fun requestQrBitmap(
        width: Int,
        height: Int
    ): Flow<Bitmap> = keyEntity.filterNotNull().map {
        val uri = TonkeeperApp.buildExportUri(it.publicKey, it.name)
        createQRBitmap(uri, width, height)
    }

    private suspend fun createQRBitmap(
        uri: Uri,
        width: Int,
        height: Int
    ): Bitmap = withContext(Dispatchers.IO) {
        val content = uri.toString()
        val builder = QRBuilder(content, width, height)
        builder.build()
    }
}