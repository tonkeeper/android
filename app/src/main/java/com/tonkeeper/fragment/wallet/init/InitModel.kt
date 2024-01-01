package com.tonkeeper.fragment.wallet.init

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tonkeeper.App
import com.tonkeeper.PasscodeManager
import com.tonkeeper.extensions.hasPushPermission
import com.tonkeeper.extensions.setRecoveryPhraseBackup
import com.tonkeeper.fragment.wallet.init.pager.ChildPageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ton.mnemonic.Mnemonic
import ton.MnemonicHelper

internal class InitModel(
    private val newWallet: Boolean
): ViewModel() {

    class Factory(val create: Boolean): ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InitModel(create) as T
        }
    }

    val pages = mutableListOf<ChildPageType>().apply {
        if (!newWallet) {
            add(ChildPageType.Import)
        }

        if (!App.passcode.hasPinCode) {
            add(ChildPageType.Passcode)
        }

        /*if (!App.instance.hasPushPermission()) {
            add(ChildPageType.Push)
        }*/

        if (App.walletManager.hasWallet()) {
            add(ChildPageType.Name)
        }
    }

    private val _currentPage = MutableStateFlow(0)
    val currentPage = _currentPage.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _hintWords = MutableStateFlow(emptyList<String>())
    val hintWords = _hintWords.asStateFlow()

    private val _validWords = MutableStateFlow(false)
    val validWords = _validWords.asStateFlow()

    private var words: List<String> = emptyList()
    private var name: String = ""
    private var passcode: String = ""

    init {
        if (newWallet) {
            viewModelScope.launch {
                words = Mnemonic.generate()
            }
        }
    }

    fun requestCheckValidWords(words: List<String>) {
        _validWords.value = false

        viewModelScope.launch(Dispatchers.IO) {
            val isValid = MnemonicHelper.isValidWords(words)
            _validWords.value = isValid
        }
    }

    fun requestHint(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = MnemonicHelper.search(text)
            _hintWords.value = if (result.size == 1 && result.first() == text) {
                emptyList()
            } else {
                result
            }
        }
    }

    fun setWords(words: List<String>) {
        this.words = words
        next()
    }

    fun setName(name: String) {
        this.name = name
        next()
    }

    fun setPasscode(passcode: String) {
        this.passcode = passcode
        next()
    }

    fun setPushPermission(isGranted: Boolean) {
        next()
    }

    fun next() {
        val nextPage = _currentPage.value + 1
        if (nextPage >= pages.size) {
            createWallet()
        } else {
            _currentPage.value = nextPage
        }
    }

    private fun createWallet() {
        _loading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            savePasscode()

            if (words.isEmpty()) {
                words = Mnemonic.generate()
            }

            val wallet = App.walletManager.addWallet(words, name)
            if (!newWallet) {
                wallet.setRecoveryPhraseBackup(true)
            }
            _ready.value = true
        }
    }

    private suspend fun savePasscode() {
        if (passcode.length == PasscodeManager.CODE_LENGTH) {
            App.passcode.setPinCode(passcode)
        }
    }
}