package com.tonapps.tonkeeper.ui.screen.init

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.PasscodeManager
import com.tonapps.tonkeeper.api.Tonapi
import com.tonapps.tonkeeper.extensions.setRecoveryPhraseBackup
import com.tonapps.tonkeeper.ui.screen.init.pager.ChildPageType
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

class InitViewModel(
    private val action: InitAction,
    private val argsName: String?,
    private val argsPkBase64: String?,
    savedStateHandle: SavedStateHandle
): ViewModel() {

    private val args = InitArgs(savedStateHandle)

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

        if (App.walletManager.hasWallet()) {
            add(ChildPageType.Name)
        }
    }

    private val _uiTopOffset = MutableStateFlow(0)
    val uiTopOffset = _uiTopOffset.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage = _currentPage.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val words: List<String>
        get() = args.words

    private val name: String?
        get() = args.name ?: argsName

    private val passcode: String
        get() = args.passcode

    private val watchAccountId: String
        get() = args.watchAccountId

    private val pkBase64: String?
        get() = args.pkBase64 ?: argsPkBase64

    init {
        args.pkBase64 = pkBase64

        if (pages.isEmpty()) {
            initWallet()
        }
    }

    fun setUiTopOffset(offset: Int) {
        _uiTopOffset.value = offset
    }

    fun setWatchAccountId(accountId: String) {
        args.watchAccountId = accountId
        next()
    }

    fun setWords(words: List<String>) {
        args.words = words
        next()
    }

    fun setData(name: String, emoji: CharSequence, color: Int) {
        args.name = name
        args.emoji = emoji
        args.color = color
        next()
    }

    fun setPasscode(passcode: String) {
        args.passcode = passcode
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

    fun prev(): Boolean {
        val prevPage = _currentPage.value - 1
        if (prevPage >= 0) {
            _currentPage.value = prevPage
            return true
        }
        return false
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

        return App.walletManager.addWatchWallet(publicKey, name, args.emoji, args.color, true)
    }

    private suspend fun watchWallet(): Wallet {
        if (watchAccountId.isEmpty()) {
            throw IllegalStateException("Watch account id is empty")
        }

        val publicKey = resolvePublicKey(watchAccountId)
        return App.walletManager.addWatchWallet(publicKey, name, args.emoji, args.color, false)
    }

    private suspend fun createWallet(): Wallet {
        val words = Mnemonic.generate()
        return App.walletManager.addWallet(words, name, args.emoji, args.color, false)
    }

    private suspend fun importWallet(testnet: Boolean): Wallet {
        if (words.isEmpty()) {
            throw IllegalStateException("Words is empty")
        }

        val wallet = App.walletManager.addWallet(words, name, args.emoji, args.color, testnet)
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