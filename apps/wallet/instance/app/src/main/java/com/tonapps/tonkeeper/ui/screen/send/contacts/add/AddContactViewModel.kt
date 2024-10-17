package com.tonapps.tonkeeper.ui.screen.send.contacts.add

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.extensions.bestMessage
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.contacts.ContactsRepository
import io.tonapi.models.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class AddContactViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val api: API,
    private val contactsRepository: ContactsRepository
): BaseWalletVM(app) {

    data class UserInput(
        val name: String,
        val address: String,
    )

    sealed class AddressAccount {
        data object Empty: AddressAccount()
        data object Loading: AddressAccount()
        data object Error: AddressAccount()
        data class Success(val account: Account): AddressAccount()
    }

    private val _userInputFlow = MutableStateFlow(UserInput("", ""))
    private val userInputFlow = _userInputFlow.asStateFlow()

    @OptIn(FlowPreview::class)
    private val userInputAddressFlow = userInputFlow
        .map { it.address }.distinctUntilChanged()
        .debounce { if (it.isEmpty()) 0 else 600 }

    private val _accountFlow = MutableStateFlow<AddressAccount>(AddressAccount.Empty)
    val accountFlow = _accountFlow.asStateFlow()

    val isEnabledButtonFlow = combine(
        accountFlow,
        userInputFlow.map { it.name }.distinctUntilChanged()
    ) { account, name ->
        account is AddressAccount.Success && name.isNotBlank()
    }

    init {
        userInputAddressFlow.collectFlow { address ->
            if (address.isBlank()) {
                _accountFlow.value = AddressAccount.Empty
            } else {
                _accountFlow.value = AddressAccount.Loading

                val account = api.resolveAccount(address, wallet.testnet)
                if (account == null) {
                    _accountFlow.value = AddressAccount.Error
                } else {
                    _accountFlow.value = AddressAccount.Success(account)
                }
            }
        }
    }

    fun save() {
        viewModelScope.launch {
            loading(true)
            try {
                val userInput = _userInputFlow.value
                contactsRepository.add(userInput.name, userInput.address)
            } catch (e: Throwable) {
                toast(e.bestMessage)
            } finally {
                loading(false)
                finish()
            }
        }
    }

    fun setName(name: String) {
        _userInputFlow.value = _userInputFlow.value.copy(name = name)
    }

    fun setAddress(address: String) {
        _userInputFlow.value = _userInputFlow.value.copy(address = address)
    }
}