package com.tonapps.tonkeeper.ui.screen.send.contacts.add

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.isTestnetAddress
import com.tonapps.blockchain.tron.isValidTronAddress
import com.tonapps.extensions.bestMessage
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.contacts.ContactsRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import io.tonapi.models.Account
import io.tonapi.models.AccountStatus
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AddContactViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val api: API,
    private val contactsRepository: ContactsRepository,
    private val settingsRepository: SettingsRepository,
) : BaseWalletVM(app) {

    private val tronUsdtEnabled: Boolean
        get() = settingsRepository.getTronUsdtEnabled(wallet.id)

    data class UserInput(
        val name: String,
        val address: String,
    )

    sealed class AddressAccount {
        data object Empty : AddressAccount()
        data object Loading : AddressAccount()
        data object Error : AddressAccount()
        data class Success(val account: Account) : AddressAccount()
        data class TronAccount(val address: String) : AddressAccount()
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
        (account is AddressAccount.Success || account is AddressAccount.TronAccount) && name.isNotBlank()
    }

    init {
        userInputAddressFlow.collectFlow { address ->
            if (address.isBlank()) {
                _accountFlow.value = AddressAccount.Empty
            } else if (address.contains(":")) {
                _accountFlow.value = AddressAccount.Error
            } else if (address.isTestnetAddress() && !wallet.testnet) {
                _accountFlow.value = AddressAccount.Error
            } else if (address.isValidTronAddress() && tronUsdtEnabled) {
                _accountFlow.value = AddressAccount.TronAccount(address)
            } else {
                _accountFlow.value = AddressAccount.Loading

                val account = api.resolveAccount(address, wallet.testnet)
                if (account == null || !account.isWallet || account.status == AccountStatus.nonexist) {
                    _accountFlow.value = AddressAccount.Error
                } else {
                    _accountFlow.value = AddressAccount.Success(account)
                }
            }
        }
    }

    fun save() {
        viewModelScope.launch {
            try {
                val userInput = _userInputFlow.value
                contactsRepository.add(userInput.name, userInput.address, wallet.testnet)
            } catch (e: Throwable) {
                toast(e.bestMessage)
            } finally {
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