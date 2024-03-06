package com.tonapps.tonkeeper.ui.screen.init

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.PasscodeManager
import com.tonapps.tonkeeper.api.ApiHelper
import com.tonapps.wallet.api.Tonapi
import com.tonapps.tonkeeper.extensions.setRecoveryPhraseBackup
import com.tonapps.tonkeeper.ui.screen.init.pager.ChildPageType
import com.tonapps.wallet.data.account.WalletRepository
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
import com.tonapps.wallet.data.account.legacy.WalletLegacy
import com.tonapps.wallet.data.account.legacy.WalletManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

class InitViewModel(
    private val action: InitAction,
    private val walletRepository: WalletRepository,
    savedStateHandle: SavedStateHandle
): ViewModel() {

    data class UiState(
        val topOffset: Int,
        val loading: Boolean
    )

    private val args = InitArgs(savedStateHandle)

    val pages = mutableListOf<ChildPageType>().apply {
        if (action == InitAction.Import) {
            add(ChildPageType.Import)
        } else if (action == InitAction.Testnet) {
            add(ChildPageType.ImportTestnet)
        } else if (action == InitAction.Watch) {
            add(ChildPageType.Watch)
        } else if (action == InitAction.Signer) {
            add(ChildPageType.Signer)
        }

        if (!App.passcode.hasPinCode) {
            add(ChildPageType.Passcode)
        }

        /*if (walletManager.hasWallet()) {
            add(ChildPageType.Name)
        }*/
    }

    private val _uiState = MutableStateFlow<UiState?>(null)
    val uiState = _uiState.stateIn(viewModelScope, SharingStarted.Eagerly, null).filterNotNull()

    init {
        if (pages.isEmpty()) {
            initWallet()
        }
    }

    fun setUiTopOffset(offset: Int) {
        // _uiTopOffset.value = offset
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
        setName(name)
        args.emoji = emoji
        args.color = color
        next()
    }

    fun setPublicKey(pkBase64: String) {
        args.pkBase64 = pkBase64
    }

    fun setName(name: String) {
        args.name = name
    }

    fun setPasscode(passcode: String) {
        args.passcode = passcode
        next()
    }

    fun next() {
        /*val nextPage = _currentPage.value + 1
        if (nextPage >= pages.size) {
            initWallet()
        } else {
            _currentPage.value = nextPage
        }*/
    }

    fun prev(): Boolean {
        /*val prevPage = _currentPage.value - 1
        if (prevPage >= 0) {
            _currentPage.value = prevPage
            return true
        }*/
        return false
    }

    private fun initWallet() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                savePasscode()

                if (action == InitAction.Create) {
                    walletRepository.createNewWallet()
                }

                //

                /*val wallet = when(action) {
                    InitAction.Watch -> watchWallet()
                    InitAction.Create -> createWallet()
                    InitAction.Import -> importWallet(false)
                    InitAction.Testnet -> importWallet(true)
                    InitAction.Signer -> signerWallet()
                }

                if (action == InitAction.Create) {
                    wallet.setRecoveryPhraseBackup(true)
                }*/
            } catch (e: Throwable) {
                Log.e("InitViewModelLog", "error", e)
            }
        }
    }

    /*private suspend fun signerWallet(): WalletLegacy {
        val pkBase64 = args.pkBase64 ?: throw IllegalStateException("Pk base64 is empty")
        val publicKey = PublicKeyEd25519(base64(pkBase64))
        return walletManager.addWatchWallet(publicKey, args.name, args.emoji, args.color, true)
    }

    private suspend fun watchWallet(): WalletLegacy {
        val watchAccountId = args.watchAccountId ?: throw IllegalStateException("Watch account id is empty")
        val publicKey = resolvePublicKey(watchAccountId)
        return walletManager.addWatchWallet(publicKey, args.name, args.emoji, args.color, false)
    }

    private suspend fun createWallet(): WalletLegacy {
        val words = Mnemonic.generate()
        return walletManager.addWallet(words, args.name, args.emoji, args.color, false)
    }

    private suspend fun importWallet(testnet: Boolean): WalletLegacy {
        val words = args.words ?: throw IllegalStateException("Words can't be null")
        val wallet = walletManager.addWallet(words, args.name, args.emoji, args.color, testnet)
        for (v in WalletVersion.entries) {
            val w = wallet.asVersion(v)
            val address = w.address
            val account = ApiHelper.resolveAccount(address, testnet) ?: continue
            if (account.balance > 0) {
                walletManager.setWalletVersion(wallet.id, v)
            }
            return w
        }

        return wallet
    }*/

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