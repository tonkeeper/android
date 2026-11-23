package com.tonapps.tonkeeper.extensions

import com.tonapps.blockchain.model.legacy.errors.SignatureDisabledException
import com.tonapps.wallet.localization.Localization
import uikit.widget.ProcessTaskView

// Only the 2FA refusal gets its own text: other failures keep "Error", whatever the node said.
fun ProcessTaskView.setFailedLabel(error: Throwable) {
    val label = when (error) {
        is SignatureDisabledException -> error.getMessage(context)
        else -> context.getString(Localization.error)
    }
    setFailedLabel(label)
}
