package com.tonapps.singer.screen.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.singer.core.account.AccountRepository
import com.tonapps.singer.screen.create.pager.PageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ton.MnemonicHelper

class CreateViewModel(
    private val import: Boolean,
    private val accountRepository: AccountRepository,
    private val savedStateHandle: SavedStateHandle
): ViewModel() {

    private companion object {
        private const val NAME_KEY = "name"
        private const val PASSWORD_KEY = "password"
        private const val MNEMONIC_KEY = "mnemonic"
    }

    private val requestPasswordCreate: Boolean
        get() = !accountRepository.hasPassword()

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

    private val name: String
        get() = savedStateHandle[NAME_KEY] ?: ""

    private val password: String
        get() = savedStateHandle[PASSWORD_KEY] ?: ""

    private val mnemonic: List<String>
        get() = savedStateHandle[MNEMONIC_KEY] ?: emptyList()

    fun pageIndex() = currentPage.map { pageIndex(it) }

    fun page(pageType: PageType) = currentPage.filter { it == pageType }

    fun setUiTopOffset(offset: Int) {
        _uiTopOffset.value = offset
    }

    fun setMnemonic(mnemonic: List<String>) {
        savedStateHandle[MNEMONIC_KEY] = mnemonic

        if (requestPasswordCreate) {
            _currentPage.tryEmit(PageType.Password)
        } else {
            _currentPage.tryEmit(PageType.Name)
        }
    }

    fun setName(name: String) {
        savedStateHandle[NAME_KEY] = name
        addKey()
    }

    fun setPassword(password: String) {
        savedStateHandle[PASSWORD_KEY] = password
        _currentPage.tryEmit(PageType.RepeatPassword)
    }

    fun checkPassword(value: String): Boolean {
        if (value != password) {
            return false
        }
        _currentPage.tryEmit(PageType.Name)
        return true
    }

    private fun addKey() {
        viewModelScope.launch(Dispatchers.IO) {
            if (requestPasswordCreate) {
                accountRepository.setPassword(password)
            }

            if (import) {
                addNewKey(name, mnemonic)
            } else {
                createNewKey(name)
            }

            _onReady.trySend(Unit)
        }
    }

    private suspend fun createNewKey(name: String) {
        val mnemonic = MnemonicHelper.generate()
        addNewKey(name, mnemonic)
    }

    private suspend fun addNewKey(name: String, mnemonic: List<String>) {
        accountRepository.addKey(name, mnemonic)
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