@file:Suppress("ForbiddenImport")

package com.tonapps.wallet

import com.trustwallet.core.ethereum.SigningOutput

internal fun evmSignedTransactionHex(signingOutput: ByteArray): String =
    "0x" + SigningOutput.ADAPTER.decode(signingOutput).encoded.hex()
