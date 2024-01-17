package com.tonkeeper.fragment.wallet.init

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tonkeeper.App
import com.tonkeeper.PasscodeManager
import com.tonkeeper.api.Tonapi
import com.tonkeeper.extensions.hasPushPermission
import com.tonkeeper.extensions.setRecoveryPhraseBackup
import com.tonkeeper.fragment.wallet.init.pager.ChildPageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.api.pub.PublicKeyEd25519
import org.ton.crypto.hex
import org.ton.mnemonic.Mnemonic
import ton.MnemonicHelper
import ton.wallet.Wallet

// TODO refactor this class
internal class InitModel(
    private val action: InitAction
): ViewModel() {

    class Factory(val action: InitAction): ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InitModel(action) as T
        }
    }

    val pages = mutableListOf<ChildPageType>().apply {
        if (action == InitAction.Import) {
            add(ChildPageType.Import)
        } else if (action == InitAction.Testnet) {
            add(ChildPageType.ImportTestnet)
        } else if (action == InitAction.Watch) {
            add(ChildPageType.Watch)
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
    private var watchAccountId: String = ""

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

    fun setWatchAccountId(accountId: String) {
        watchAccountId = accountId
        next()
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
            initWallet()
        } else {
            _currentPage.value = nextPage
        }
    }

    private fun initWallet() {
        _loading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            savePasscode()

            val wallet = when(action) {
                InitAction.Watch -> watchWallet(false)
                InitAction.Create -> createWallet()
                InitAction.Import -> importWallet(false)
                InitAction.Testnet -> importWallet(true)
                InitAction.Signer -> watchWallet(true)
            }

            if (action == InitAction.Create) {
                wallet.setRecoveryPhraseBackup(true)
            }

            _ready.value = true
        }
    }

    private suspend fun watchWallet(singer: Boolean): Wallet {
        if (watchAccountId.isEmpty()) {
            throw IllegalStateException("Watch account id is empty")
        }

        val publicKey = resolvePublicKey(watchAccountId)
        return App.walletManager.addWatchWallet(publicKey, name, singer)
    }

    private suspend fun createWallet(): Wallet {
        val words = Mnemonic.generate()
        return App.walletManager.addWallet(words, name, false)
    }

    private suspend fun importWallet(testnet: Boolean): Wallet {
        if (words.isEmpty()) {
            throw IllegalStateException("Words is empty")
        }

        return App.walletManager.addWallet(words, name, testnet)
    }

    private suspend fun savePasscode() {
        if (passcode.length == PasscodeManager.CODE_LENGTH) {
            App.passcode.setPinCode(passcode)
        }
    }

    private suspend fun resolvePublicKey(
        accountId: String,
    ): PublicKeyEd25519 = withContext(Dispatchers.IO) {
        val hex = Tonapi.accounts.get(false).getAccountPublicKey(accountId).publicKey
        PublicKeyEd25519(hex(hex))
    }
}