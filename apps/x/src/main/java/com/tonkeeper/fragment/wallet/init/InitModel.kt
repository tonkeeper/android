package com.tonkeeper.fragment.wallet.init

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
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
import org.ton.crypto.base64
import org.ton.crypto.hex
import org.ton.mnemonic.Mnemonic
import ton.MnemonicHelper
import ton.contract.WalletVersion
import ton.wallet.Wallet

// TODO refactor this class
internal class InitModel(
    private val action: InitAction,
    private val argsName: String?,
    private val argsPkBase64: String?,
    private val savedStateHandle: SavedStateHandle
): ViewModel() {

    private companion object {
        private const val WORDS_KEY = "words"
        private const val NAME_KEY = "name"
        private const val PASSCODE_KEY = "passcode"
        private const val WATCH_ACCOUNT_ID_KEY = "watch_account_id"
        private const val PK_BASE64_KEY = "pk_base64"
    }

    class Factory(
        val action: InitAction,
        val name: String?,
        val pkBase64: String?
    ): ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return InitModel(action, name, pkBase64, extras.createSavedStateHandle()) as T
        }
    }

    val pages = mutableListOf<ChildPageType>().apply {
        if (action == InitAction.Import) {
            add(ChildPageType.Import)
        } else if (action == InitAction.Testnet) {
            add(ChildPageType.ImportTestnet)
        } else if (action == InitAction.Watch) {
            add(ChildPageType.Watch)
        } else if (action == InitAction.Signer && pkBase64 == null) {
            add(ChildPageType.Signer)
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

    private val words: List<String>
        get() = savedStateHandle[WORDS_KEY] ?: emptyList()

    private val name: String?
        get() = savedStateHandle[NAME_KEY] ?: argsName

    private val passcode: String
        get() = savedStateHandle[PASSCODE_KEY] ?: ""

    private val watchAccountId: String
        get() = savedStateHandle[WATCH_ACCOUNT_ID_KEY] ?: ""

    private val pkBase64: String?
        get() = savedStateHandle[PK_BASE64_KEY] ?: argsPkBase64

    init {
        savedStateHandle[PK_BASE64_KEY] = pkBase64

        if (pages.isEmpty()) {
            initWallet()
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

    fun setWatchAccountId(accountId: String) {
        savedStateHandle[WATCH_ACCOUNT_ID_KEY] = accountId
        next()
    }

    fun setWords(words: List<String>) {
        savedStateHandle[WORDS_KEY] = words
        next()
    }

    fun setName(name: String) {
        savedStateHandle[NAME_KEY] = name
        next()
    }

    fun setPasscode(passcode: String) {
        savedStateHandle[PASSCODE_KEY] = passcode
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
                InitAction.Watch -> watchWallet()
                InitAction.Create -> createWallet()
                InitAction.Import -> importWallet(false)
                InitAction.Testnet -> importWallet(true)
                InitAction.Signer -> signerWallet()
            }

            if (action == InitAction.Create) {
                wallet.setRecoveryPhraseBackup(true)
            }

            _ready.value = true
        }
    }

    private suspend fun signerWallet(): Wallet {
        val pkBase64 = pkBase64 ?: throw IllegalStateException("Pk base64 is empty")
        val publicKey = PublicKeyEd25519(base64(pkBase64))

        var name = name ?: ""
        if (!name.endsWith("(Signer)")) {
            name += " (Signer)"
        }

        return App.walletManager.addWatchWallet(publicKey, name, true)
    }

    private suspend fun watchWallet(): Wallet {
        if (watchAccountId.isEmpty()) {
            throw IllegalStateException("Watch account id is empty")
        }

        val publicKey = resolvePublicKey(watchAccountId)
        return App.walletManager.addWatchWallet(publicKey, name, false)
    }

    private suspend fun createWallet(): Wallet {
        val words = Mnemonic.generate()
        return App.walletManager.addWallet(words, name, false)
    }

    private suspend fun importWallet(testnet: Boolean): Wallet {
        if (words.isEmpty()) {
            throw IllegalStateException("Words is empty")
        }

        val wallet = App.walletManager.addWallet(words, name, testnet)
        for (v in WalletVersion.entries) {
            val w = wallet.asVersion(v)
            val address = w.address
            val account = Tonapi.resolveAccount(address, testnet) ?: continue
            if (account.balance > 0) {
                App.walletManager.setWalletVersion(wallet.id, v)
            }
            return w
        }

        return wallet
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