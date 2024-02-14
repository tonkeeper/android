package com.tonapps.tonkeeper.ui.screen.init.pager.child.phrase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ton.MnemonicHelper

class PhraseViewModel: ViewModel() {

    private val _hintWords = MutableStateFlow(emptyList<String>())
    val hintWords = _hintWords.asStateFlow()

    fun hint(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = MnemonicHelper.search(text)
            _hintWords.value = if (result.size == 1 && result.first() == text) {
                emptyList()
            } else {
                result
            }
        }
    }
}