package com.tonapps.tonkeeper.ui.screen.send.transaction

import android.os.Bundle
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.settings.BatteryTransaction
import uikit.base.BaseArgs

data class SendTransactionArgs(
    val request: SignRequestEntity,
    val batteryTransactionType: BatteryTransaction,
    val forceRelayer: Boolean
): BaseArgs() {

    companion object {
        private const val ARG_REQUEST = "request"
        private const val ARG_BATTERY_TRANSACTION_TYPE = "battery_transaction_type"
        private const val ARG_FORCE_RELAYER = "force_relayer"
    }

    constructor(bundle: Bundle) : this(
        request = bundle.getParcelableCompat(ARG_REQUEST)!!,
        batteryTransactionType = BatteryTransaction.of(bundle.getInt(ARG_BATTERY_TRANSACTION_TYPE, -1)),
        forceRelayer = bundle.getBoolean(ARG_FORCE_RELAYER)
    )

    override fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putParcelable(ARG_REQUEST, request)
        bundle.putInt(ARG_BATTERY_TRANSACTION_TYPE, batteryTransactionType.code)
        bundle.putBoolean(ARG_FORCE_RELAYER, forceRelayer)
        return bundle
    }
}
