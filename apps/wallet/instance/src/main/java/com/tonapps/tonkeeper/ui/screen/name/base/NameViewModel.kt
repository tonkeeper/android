package com.tonapps.tonkeeper.ui.screen.name.base

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tonapps.emoji.EmojiPack
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.ui.screen.name.adapter.EmojiAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import ton.wallet.WalletManager

class NameViewModel(
    mode: NameMode,
    application: Application,
    savedStateHandle: SavedStateHandle
): AndroidViewModel(application) {

    private val walletManager: WalletManager = App.walletManager
    private val savedState = NameSavedState(savedStateHandle)

    private val _emojisFlow = MutableStateFlow<Array<CharSequence>?>(null)
    val emojiFlow = _emojisFlow.asSharedFlow().filterNotNull()

    private val _uiStateFlow = MutableStateFlow<UiState?>(null)
    val uiStateFlow = _uiStateFlow.asSharedFlow().filterNotNull()

    init {
        if (mode == NameModeEdit) {
            loadCurrentWallet()
        }
    }

    fun setEmoji(emoji: CharSequence) {
        savedState.emoji = emoji
        notifyUiState()
    }

    fun setColor(color: Int) {
        savedState.color = color
        notifyUiState()
    }

    private fun notifyUiState() {
        _uiStateFlow.value = UiState(
            name = savedState.name,
            emoji = savedState.emoji,
            color = savedState.color,
        )
    }

    fun loadEmojiPack() {
        if (_emojisFlow.value.isNullOrEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                val emojis = EmojiPack.get(getApplication())
                _emojisFlow.value = emojis.map { emoji -> emoji.value }.toTypedArray()

                if (savedState.emoji.isEmpty()) {
                    setEmoji(emojis.random().value)
                }
            }
        }
    }

    private fun loadCurrentWallet() {
        viewModelScope.launch {
            val wallet = walletManager.getWalletInfo() ?: return@launch
            savedState.name = wallet.name
            savedState.emoji = wallet.emoji
            savedState.color = wallet.color
            notifyUiState()
        }
    }
}