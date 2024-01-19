package com.tonapps.singer.screen.sign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.singer.core.KeyEntity
import com.tonapps.singer.core.account.AccountRepository
import com.tonapps.singer.screen.root.action.RootAction
import com.tonapps.singer.screen.sign.list.SignItem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.ton.block.Message
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.crypto.base64
import org.ton.tlb.loadTlb
import ton.extensions.base64
import uikit.list.ListCell

class SignViewModel(
    private val id: Long,
    private val boc: String,
    private val accountRepository: AccountRepository
): ViewModel() {

    val keyEntity = accountRepository.getKey(id).filterNotNull()

    private val _actionsFlow = MutableStateFlow<List<SignItem>?>(null)
    val actionsFlow = _actionsFlow.asStateFlow().filterNotNull()

    private val _signedBody = Channel<String>(Channel.BUFFERED)
    val signedBody = _signedBody.receiveAsFlow()

    private val unsignedBody: Cell by lazy {
        try {
            BagOfCells(base64(boc)).first()
        } catch (e: Throwable) {
            Cell.empty()
        }
    }

    init {
        viewModelScope.launch {
            val items = parseBoc()
            _actionsFlow.value = items
        }
    }

    fun sign() {
        viewModelScope.launch {
            val signedBody = accountRepository.sign(id, unsignedBody).base64()
            _signedBody.trySend(signedBody)
        }
    }

    private fun parseBoc(): List<SignItem> {
        try {
            val message = unsignedBody.parse { loadTlb(Message.Any) }
            return listOf(SignItem.Unknown(ListCell.Position.SINGLE))
        } catch (e: Throwable) {
            return listOf(SignItem.Unknown(ListCell.Position.SINGLE))
        }
    }
}