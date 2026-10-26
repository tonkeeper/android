package com.tonapps.deposit.multicoin.screens.confirm

import com.tonapps.blockchain.model.ConfirmContext

/**
 * Where the confirmation sheet reports back. The request carries the transport it arrived on, so
 * this screen does not need to know which one answered it.
 */
interface ConfirmResultListener {

    suspend fun approved(context: ConfirmContext.Dapp, result: String)

    suspend fun rejected(context: ConfirmContext.Dapp)

    // The user confirmed, but signing or sending did not go through.
    suspend fun failed(context: ConfirmContext.Dapp)
}
