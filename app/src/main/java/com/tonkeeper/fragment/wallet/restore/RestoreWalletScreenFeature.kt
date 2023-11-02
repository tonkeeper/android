package com.tonkeeper.fragment.wallet.restore

import androidx.lifecycle.viewModelScope
import com.tonkeeper.App
import com.tonkeeper.core.currency.CurrencyUpdateWorker
import ton.MnemonicHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uikit.mvi.UiFeature

class RestoreWalletScreenFeature: UiFeature<RestoreWalletScreenState, RestoreWalletEffect>(RestoreWalletScreenState()) {

    fun start(words: List<String>) {
        updateUiState { state ->
            state.copy(loading = true)
        }
        viewModelScope.launch {
            App.walletManager.restoreWallet(words)
            updateUiState { state ->
                state.copy(done = true)
            }
        }
    }

    fun checkValidWords(words: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val isValid = MnemonicHelper.isValidWords(words)
            updateUiState { state ->
                state.copy(canNext = isValid)
            }
        }
    }

    fun requestHint(word: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val words = findHint(word)

            updateUiState{ state ->
                state.copy(hintWords = words)
            }
        }
    }

    private fun findHint(word: String): List<String> {
        if (word.isEmpty()) {
            return emptyList()
        }
        val result = MnemonicHelper.search(word)
        if (result.size == 1 && result.first() == word) {
            return emptyList()
        }
        return result
    }
}