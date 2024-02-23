package com.tonapps.tonkeeper.ui.screen.init

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.PasscodeManager
import com.tonapps.tonkeeper.api.Tonapi
import com.tonapps.tonkeeper.extensions.setRecoveryPhraseBackup
import com.tonapps.tonkeeper.ui.screen.init.pager.ChildPageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.api.pub.PublicKeyEd25519
import org.ton.crypto.base64
import org.ton.crypto.hex
import org.ton.mnemonic.Mnemonic
import ton.wallet.Wallet
import ton.wallet.WalletManager

class InitViewModel(
    private val action: InitAction,
    argsName: String?,
    argsPkBase64: String?,
    private val walletManager: WalletManager,
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
        } else if (action == InitAction.Signer && argsPkBase64 == null) {
            add(ChildPageType.Signer)
        }

        if (!App.passcode.hasPinCode) {
            add(ChildPageType.Passcode)
        }

        if (walletManager.hasWallet()) {
            add(ChildPageType.Name)
        }
    }

    private val _uiTopOffset = MutableStateFlow(0)
    val uiTopOffset = _uiTopOffset.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage = _currentPage.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _savedWalletChannel = Channel<Unit>(Channel.BUFFERED)
    val savedWalletFlow = _savedWalletChannel.receiveAsFlow()

    init {
        args.pkBase64 = argsPkBase64
        args.name = argsName

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
            try {
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

                _savedWalletChannel.trySend(Unit)
            } catch (e: Throwable) {
                Log.e("InitViewModelLog", "error", e)
            }
        }
    }

    private suspend fun signerWallet(): Wallet {
        val pkBase64 = args.pkBase64 ?: throw IllegalStateException("Pk base64 is empty")
        val publicKey = PublicKeyEd25519(base64(pkBase64))
        return walletManager.addWatchWallet(publicKey, args.name, args.emoji, args.color, true)
    }

    private suspend fun watchWallet(): Wallet {
        val watchAccountId = args.watchAccountId ?: throw IllegalStateException("Watch account id is empty")
        val publicKey = resolvePublicKey(watchAccountId)
        return walletManager.addWatchWallet(publicKey, args.name, args.emoji, args.color, false)
    }

    private suspend fun createWallet(): Wallet {
        val words = Mnemonic.generate()
        return walletManager.addWallet(words, args.name, args.emoji, args.color, false)
    }

    private suspend fun importWallet(testnet: Boolean): Wallet {
        val words = args.words ?: throw IllegalStateException("Words can't be null")
        val wallet = walletManager.addWallet(words, args.name, args.emoji, args.color, testnet)
        for (v in WalletVersion.entries) {
            val w = wallet.asVersion(v)
            val address = w.address
            val account = Tonapi.resolveAccount(address, testnet) ?: continue
            if (account.balance > 0) {
                walletManager.setWalletVersion(wallet.id, v)
            }
            return w
        }

        return wallet
    }

    private suspend fun savePasscode() {
        val passcode = args.passcode ?: return
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