package com.tonapps.blockchain.model.legacy.errors

import com.tonapps.extensions.ErrorForUserException
import com.tonapps.wallet.localization.Localization

// The wallet's 2FA extension turned off signing by key: the network rejects whatever Keeper signs.
class SignatureDisabledException : ErrorForUserException(stringRes = Localization.sign_error_two_factor) {
    override val message: String get() = "signature_disabled"
}
