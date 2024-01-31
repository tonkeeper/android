package com.tonapps.signer.screen.create

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.signer.core.repository.KeyRepository
import com.tonapps.signer.password.Password
import com.tonapps.signer.screen.create.pager.PageType
import com.tonapps.signer.vault.SignerVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.mnemonic.Mnemonic
import javax.crypto.SecretKey

class CreateViewModel(
    private val import: Boolean,
    private val keyRepository: KeyRepository,
    private val vault: SignerVault,
    savedStateHandle: SavedStateHandle,
): ViewModel() {

    private val args = CreateArgs(savedStateHandle)

    private val requestPasswordCreate: Boolean
        get() = !vault.hasPassword()

    val pages = mutableListOf<PageType>().apply {
        if (import) {
            add(PageType.Phrase)
        }
        if (requestPasswordCreate) {
            add(PageType.Password)
            add(PageType.RepeatPassword)
        }
        add(PageType.Name)
    }

    private val _onReady = Channel<Unit>(Channel.BUFFERED)
    val onReady: Flow<Unit> = _onReady.receiveAsFlow()

    private val _currentPage = MutableStateFlow(pages.first())
    val currentPage = _currentPage.asSharedFlow()

    private val _uiTopOffset = MutableStateFlow(0)
    val uiTopOffset = _uiTopOffset.asStateFlow()

    fun pageIndex() = currentPage.map { pageIndex(it) }

    fun page(pageType: PageType) = currentPage.filter { it == pageType }

    fun setUiTopOffset(offset: Int) {
        _uiTopOffset.value = offset
    }

    fun setMnemonic(mnemonic: List<String>) {
        args.mnemonic = mnemonic

        if (requestPasswordCreate) {
            _currentPage.tryEmit(PageType.Password)
        } else {
            _currentPage.tryEmit(PageType.Name)
        }
    }

    fun setName(name: String) {
        args.name = name
    }

    fun setPassword(password: CharArray) {
        args.password = password
        _currentPage.tryEmit(PageType.RepeatPassword)
    }

    fun checkPassword(value: CharArray): Boolean {
        if (!value.contentEquals(args.password)) {
            return false
        }
        _currentPage.tryEmit(PageType.Name)
        return true
    }

    fun addKey(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val name = args.name ?: throw IllegalStateException("Name is null")
                val secret = masterSecret(context).single()

                if (import) {
                    val mnemonic = args.mnemonic ?: throw IllegalStateException("Mnemonic is null")
                    addNewKey(secret, name, mnemonic)
                } else {
                    createNewKey(secret, name)
                }

                Password.setUnlock()
                _onReady.trySend(Unit)
            } catch (e: Throwable) {
                _currentPage.tryEmit(pages.first())
            }
        }
    }

    private fun masterSecret(context: Context): Flow<SecretKey> {
        return if (requestPasswordCreate) {
            val password = args.password ?: throw IllegalStateException("password is required")
            createMasterSecret(password)
        } else {
            Password.authenticate(context)
        }
    }

    private fun createMasterSecret(password: CharArray) = flow {
        emit(vault.createMasterSecret(password))
    }.take(1)

    private suspend fun createNewKey(secret: SecretKey, name: String) {
        val mnemonic = Mnemonic.generate()
        addNewKey(secret, name, mnemonic)
    }

    private suspend fun addNewKey(secret: SecretKey, name: String, mnemonic: List<String>) {
        val seed = Mnemonic.toSeed(mnemonic)
        val publicKey = PrivateKeyEd25519(seed).publicKey()

        val entity = keyRepository.addKey(name, publicKey)

        vault.setMnemonic(secret, entity.id, mnemonic)
    }

    fun prev(): Boolean {
        val currentPage = currentPage.replayCache.last()
        val index = pageIndex(currentPage)
        if (index == 0) {
            return false
        }
        _currentPage.tryEmit(pages[index - 1])
        return true
    }

    private fun pageIndex(page: PageType): Int {
        return pages.indexOf(page)
    }
}