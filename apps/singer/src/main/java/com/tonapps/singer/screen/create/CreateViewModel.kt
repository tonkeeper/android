package com.tonapps.singer.screen.create

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.singer.core.account.AccountRepository
import com.tonapps.singer.screen.create.pager.PageType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.ton.mnemonic.Mnemonic
import ton.MnemonicHelper

class CreateViewModel(
    private val accountRepository: AccountRepository
): ViewModel() {

    private val requestPasswordCreate: Boolean
        get() = !accountRepository.hasPassword()

    val pages = mutableListOf<PageType>().apply {
        if (requestPasswordCreate) {
            add(PageType.Password)
            add(PageType.RepeatPassword)
        }
        add(PageType.Name)
    }

    private val _currentPage = MutableStateFlow(pages.first())
    val currentPage = _currentPage.asStateFlow()

    private val _indexPage = MutableStateFlow(0)
    val indexPage = _indexPage.asStateFlow()

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _name = MutableStateFlow("")
    val name = _name.asStateFlow()

    init {
        currentPage.onEach {
            _indexPage.value = pageIndex(it)
        }.launchIn(viewModelScope)
    }

    fun setName(name: String) {
        _name.value = name
        addKey()
    }

    fun setPassword(password: String) {
        _password.value = password
        _currentPage.value = PageType.RepeatPassword
    }

    fun checkPassword(value: String): Boolean {
        if (value != password.value) {
            return false
        }
        _currentPage.value = PageType.Name
        return true
    }

    private fun addKey() {
        viewModelScope.launch {
            if (requestPasswordCreate) {
                accountRepository.setPassword(password.value)
            }
            createNewKey(name.value)
        }
    }

    private suspend fun createNewKey(name: String) {
        val mnemonic = MnemonicHelper.generate()
        accountRepository.addKey(name, mnemonic)
    }

    fun prev(): Boolean {
        val currentPage = currentPage.value
        val index = pageIndex(currentPage)
        if (index == 0) {
            return false
        }
        _currentPage.value = pages[index - 1]
        return true
    }

    private fun pageIndex(page: PageType): Int {
        return pages.indexOf(page)
    }
}