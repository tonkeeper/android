package com.tonkeeper.fragment.send.recipient

import androidx.lifecycle.viewModelScope
import com.tonkeeper.App
import com.tonkeeper.api.Tonapi
import com.tonkeeper.api.shortAddress
import io.tonapi.models.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ton.TonAddress
import ton.extensions.toUserFriendly
import uikit.mvi.UiFeature

class RecipientScreenFeature: UiFeature<RecipientScreenState, RecipientScreenEffect>(RecipientScreenState()) {

    private val accounts = Tonapi.accounts

    private var checkAddressJob: Job? = null

    val displayAddress: String
        get() {
            val state = uiState.value
            return state.name ?: state.address.shortAddress
        }

    fun requestAddressCheck(value: String) {
        updateUiState { it.copy(address = value) }

        if (value.isEmpty()) {
            updateUiState { it.copy(addressState = RecipientScreenState.AddressState.EMPTY) }
            return
        }

        updateUiState { it.copy(addressState = RecipientScreenState.AddressState.LOADING) }

        checkAddressJob?.cancel()
        checkAddressJob = viewModelScope.launch {
            delay(300)

            val account = resolveAccount(value)
            if (account == null) {
                updateUiState { it.copy(addressState = RecipientScreenState.AddressState.INVALID) }
                return@launch
            }

            val wallet = App.walletManager.getWalletInfo()
            if (wallet?.isMyAddress(account.address) == true) {
                updateUiState { it.copy(addressState = RecipientScreenState.AddressState.INVALID) }
                return@launch
            }

            updateUiState { it.copy(
                addressState = RecipientScreenState.AddressState.VALID,
                address = account.address.toUserFriendly(),
                name = account.name,
            ) }
        }
    }

    private suspend fun resolveAccount(
        value: String
    ): Account? = withContext(Dispatchers.IO) {
        try {
            if (!TonAddress.isValid(value)) {
                return@withContext resolveDomain(value.lowercase().trim())
            }
            return@withContext accounts.getAccount(value)
        } catch (ignored: Throwable) {}
        return@withContext null
    }

    private fun resolveDomain(
        domain: String,
        suffixList: Array<String> = arrayOf(".ton", ".t.me")
    ): Account? {
        var account: Account? = null
        try {
            account = accounts.getAccount(domain)
        } catch (ignored: Throwable) {}

        for (suffix in suffixList) {
            if (account == null && !domain.endsWith(suffix)) {
                try {
                    account = accounts.getAccount("$domain$suffix")
                } catch (ignored: Throwable) {}
            }
        }
        if (account?.name == null) {
            account = account?.copy(name = domain)
        }
        return account
    }

}