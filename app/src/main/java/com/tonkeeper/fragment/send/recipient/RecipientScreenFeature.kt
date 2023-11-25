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
import uikit.mvi.UiFeature

class RecipientScreenFeature: UiFeature<RecipientScreenState, RecipientScreenEffect>(RecipientScreenState()) {

    private val account = Tonapi.accounts

    private var checkAddressJob: Job? = null

    val displayAddress: String
        get() {
            val state = uiState.value
            return state.name ?: state.address.shortAddress
        }

    fun setComment(comment: String) {
        updateUiState { it.copy(comment = comment) }
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
                name = account.name,
            ) }
        }
    }

    private suspend fun resolveAccount(
        value: String
    ): Account? = withContext(Dispatchers.IO) {
        try {
            if (!TonAddress.isValid(value)) {
                return@withContext resolveDomain(value)
            }
            return@withContext account.getAccount(value)
        } catch (ignored: Throwable) {}
        return@withContext null
    }

    private fun resolveDomain(domain: String): Account? {
        var account: Account? = null
        try {
            account = Tonapi.accounts.getAccount(domain)
        } catch (ignored: Throwable) {}

        if (account == null && !domain.endsWith(".ton")) {
            try {
                account = Tonapi.accounts.getAccount("$domain.ton")
            } catch (ignored: Throwable) {}
        }

        if (account == null && !domain.endsWith(".t.me")) {
            try {
                account = Tonapi.accounts.getAccount("$domain.t.me")
            } catch (ignored: Throwable) {}
        }
        return account
    }

}