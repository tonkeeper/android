package com.tonapps.tonkeeper.fragment.send.recipient

import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.api.Tonapi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ton.TonAddress
import ton.extensions.toUserFriendly
import uikit.mvi.UiFeature

class RecipientScreenFeature: UiFeature<RecipientScreenState, RecipientScreenEffect>(RecipientScreenState()) {

    private var checkAddressJob: Job? = null

    val isRequireComment: Boolean
        get() = uiState.value.requireComment

    val isValidateAddress: Boolean
        get() = uiState.value.addressState == RecipientScreenState.AddressState.VALID

    fun requestAddressCheck(value: String) {
        updateUiState { it.copy(address = value) }

        if (value.isEmpty()) {
            updateUiState { it.copy(
                addressState = RecipientScreenState.AddressState.EMPTY,
                requireComment = false,
                name = null,
                address = "",
                bounce = false,
            ) }
            return
        }

        updateUiState { it.copy(
            addressState = RecipientScreenState.AddressState.LOADING,
            requireComment = false,
            name = null,
            address = "",
            bounce = false,
        ) }

        checkAddressJob?.cancel()
        checkAddressJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300)

            val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo() ?: return@launch

            val account = Tonapi.resolveAccount(value, wallet.testnet)
            if (account == null) {
                updateUiState { it.copy(
                    addressState = RecipientScreenState.AddressState.INVALID,
                    requireComment = false,
                    name = null,
                    address = "",
                    bounce = false,
                ) }
                return@launch
            }

            var address = value
            if (!account.name.isNullOrBlank()) {
                address = account.address.toUserFriendly(
                    wallet = account.isWallet,
                    testnet = wallet.testnet
                )
            }

            var bounce = value.startsWith("EQ") || !value.startsWith("UQ")

            if (!TonAddress.isValid(value)) {
                bounce = !account.isWallet
            }

            updateUiState { it.copy(
                addressState = RecipientScreenState.AddressState.VALID,
                address = address,
                name = account.name,
                requireComment = account.memoRequired == true,
                bounce = bounce,
            ) }
        }
    }

}