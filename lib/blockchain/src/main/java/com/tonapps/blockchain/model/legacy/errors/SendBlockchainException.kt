package com.tonapps.blockchain.model.legacy.errors

import androidx.annotation.StringRes
import com.tonapps.extensions.ErrorForUserException
import com.tonapps.wallet.localization.Localization

sealed class SendBlockchainException(
    @StringRes stringRes: Int,
    text: String? = null,
    cause: Throwable? = null
): ErrorForUserException(stringRes = stringRes, text = text, cause = cause) {

    data object SendBlockchainStatusException: SendBlockchainException(Localization.sending_error) {
        private fun readResolve(): Any = SendBlockchainStatusException
    }

    data class SendBlockchainErrorException(override val cause: Throwable, override val text: String? = null): SendBlockchainException(Localization.error, text, cause) {
        private fun readResolve(): Any = SendBlockchainStatusException
    }

    data object SendBlockchainUnknownException : SendBlockchainException(Localization.error) {
        private fun readResolve(): Any = SendBlockchainUnknownException
    }

}