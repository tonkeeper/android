package com.tonapps.singer.screen.qr

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.singer.core.account.AccountRepository
import core.qr.QRBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class QRViewModel(
    private val id: Long,
    private val body: String,
    private val accountRepository: AccountRepository
): ViewModel() {

    val keyEntity = accountRepository.getKey(id).filterNotNull()

    private val _qrCode = MutableStateFlow<Bitmap?>(null)
    val qrCode = _qrCode.asStateFlow().filterNotNull()

    fun requestQR(width: Int, height: Int) {
        viewModelScope.launch {
            val builder = QRBuilder(body, width, height)
            _qrCode.value = builder.build()
        }
    }
}