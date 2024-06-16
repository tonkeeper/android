package com.tonapps.wallet.data.rn.expo

import com.tonapps.wallet.data.rn.RNLegacy
import java.io.Serializable

class SecureStoreOptions(
    var authenticationPrompt: String = " ",
    var keychainService: String = RNLegacy.DEFAULT_KEYSTORE_ALIAS,
    var requireAuthentication: Boolean = false
) : Serializable