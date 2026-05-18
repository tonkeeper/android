package com.tonapps.deposit.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.tonapps.blockchain.contract.Blockchain
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.deposit.screens.send.state.SendDestination

sealed interface AddressError {
    data object NotFound : AddressError
    data object Scam : AddressError
    data class TokenMismatch(
        val addressBlockchain: Blockchain,
        val selectedToken: TokenEntity,
    ) : AddressError
}

@Stable
class SendAddressState internal constructor() {

    var text by mutableStateOf("")
        private set

    var error: AddressError? by mutableStateOf(null)
        private set

    var isResolving by mutableStateOf(false)
        private set

    val hasAddress: Boolean
        get() = text.isNotBlank()

    val isError: Boolean
        get() = error != null

    fun onTextChange(newText: String) {
        text = newText
        if (newText.isEmpty()) {
            error = null
            isResolving = false
        }
    }

    internal fun updateDestination(destination: SendDestination, isResolving: Boolean) {
        this.isResolving = isResolving
        error = when (destination) {
            is SendDestination.NotFound -> AddressError.NotFound
            is SendDestination.Scam -> AddressError.Scam
            is SendDestination.TokenError -> AddressError.TokenMismatch(
                addressBlockchain = destination.addressBlockchain,
                selectedToken = destination.selectedToken,
            )
            else -> null
        }
    }

    companion object {
        val Saver: Saver<SendAddressState, String> = Saver(
            save = { it.text },
            restore = { saved -> SendAddressState().also { it.onTextChange(saved) } },
        )
    }
}

@Composable
fun rememberSendAddressState(
    destination: SendDestination,
    isResolving: Boolean,
): SendAddressState {
    val state = rememberSaveable(saver = SendAddressState.Saver) { SendAddressState() }
    LaunchedEffect(destination, isResolving) {
        state.updateDestination(destination, isResolving)
    }
    return state
}
