package com.tonapps.singer.screen.key

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.singer.core.KeyEntity
import com.tonapps.singer.core.account.AccountRepository
import core.QRBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.api.pub.PublicKeyEd25519

class KeyViewModel(
    id: Long,
    accountRepository: AccountRepository
): ViewModel() {

    private val _keyEntity = MutableStateFlow<KeyEntity?>(null)
    val keyEntity = _keyEntity.asStateFlow()

    init {
        accountRepository.getKey(id).onEach {
            _keyEntity.value = it
        }.launchIn(viewModelScope)
    }

    fun requestQrBitmap(
        width: Int,
        height: Int
    ): Flow<Bitmap> = keyEntity.filterNotNull().map {
        createQRBitmap(it.exportUri, width, height)
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