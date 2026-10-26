package com.tonapps.tonkeeper.ui.screen.send.transaction

import android.os.Bundle
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.settings.BatteryTransaction
import uikit.base.BaseArgs

/**
 * Which endpoint broadcasts the signed message, independent of who pays the fee. [Battery] routes
 * it through the battery service so the request carries the wallet auth headers — it only accepts
 * relay-wrapped messages, so callers must not ask for it with an externally-signed transaction.
 */
enum class BroadcastVia {
    Default,
    Battery,
}

/**
 * What the sheet does once the user confirms. [SignMessage] (TON Connect `signMessage`) signs the
 * request as an internal message for a relayer named [SignMessage.appName] and never broadcasts it.
 */
sealed class SendTransactionMode {
    data object SendTransaction : SendTransactionMode()
    data class SignMessage(val appName: String) : SendTransactionMode()
}

data class SendTransactionAnalyticsContext(
    val sendNativeFrom: Events.SendNative.SendNativeFrom?,
    val transactionSentDetail: Events.TransactionSent.TransactionSentCategoryDetail?,
    val utm: UtmParameters,
)

data class SendTransactionArgs(
    val request: SignRequestEntity,
    val batteryTransactionType: BatteryTransaction,
    val forceRelayer: Boolean,
    val broadcastVia: BroadcastVia = BroadcastVia.Default,
    val sendNativeFrom: Events.SendNative.SendNativeFrom? = null,
    val transactionSentDetail: Events.TransactionSent.TransactionSentCategoryDetail? = null,
    val utm: UtmParameters = UtmParameters.Empty,
    val mode: SendTransactionMode = SendTransactionMode.SendTransaction,
): BaseArgs() {

    constructor(bundle: Bundle) : this(
        request = bundle.getParcelableCompat(ARG_REQUEST)!!,
        batteryTransactionType = BatteryTransaction.of(bundle.getInt(ARG_BATTERY_TRANSACTION_TYPE, -1)),
        forceRelayer = bundle.getBoolean(ARG_FORCE_RELAYER),
        broadcastVia = bundle.getString(ARG_BROADCAST_VIA)?.let { name ->
            BroadcastVia.entries.find { it.name == name }
        } ?: BroadcastVia.Default,
        sendNativeFrom = bundle.getString(ARG_SEND_NATIVE_FROM)?.let { key ->
            Events.SendNative.SendNativeFrom.entries.find { it.key == key }
        },
        transactionSentDetail = bundle.getString(ARG_TRANSACTION_SENT_DETAIL)?.let { key ->
            Events.TransactionSent.TransactionSentCategoryDetail.entries.find { it.key == key }
        },
        utm = bundle.getParcelableCompat<UtmParameters>(ARG_UTM) ?: UtmParameters.Empty,
        mode = if (bundle.getString(ARG_MODE) == MODE_SIGN_MESSAGE) {
            SendTransactionMode.SignMessage(bundle.getString(ARG_MODE_APP_NAME) ?: "")
        } else {
            SendTransactionMode.SendTransaction
        },
    )

    override fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putParcelable(ARG_REQUEST, request)
        bundle.putInt(ARG_BATTERY_TRANSACTION_TYPE, batteryTransactionType.code)
        bundle.putBoolean(ARG_FORCE_RELAYER, forceRelayer)
        bundle.putString(ARG_BROADCAST_VIA, broadcastVia.name)
        sendNativeFrom?.let { bundle.putString(ARG_SEND_NATIVE_FROM, it.key) }
        transactionSentDetail?.let { bundle.putString(ARG_TRANSACTION_SENT_DETAIL, it.key) }
        bundle.putParcelable(ARG_UTM, utm)
        when (mode) {
            is SendTransactionMode.SendTransaction -> bundle.putString(ARG_MODE, MODE_SEND_TRANSACTION)
            is SendTransactionMode.SignMessage -> {
                bundle.putString(ARG_MODE, MODE_SIGN_MESSAGE)
                bundle.putString(ARG_MODE_APP_NAME, mode.appName)
            }
        }
        return bundle
    }

    companion object {
        private const val ARG_REQUEST = "request"
        private const val ARG_BATTERY_TRANSACTION_TYPE = "battery_transaction_type"
        private const val ARG_FORCE_RELAYER = "force_relayer"
        private const val ARG_BROADCAST_VIA = "broadcast_via"
        private const val ARG_SEND_NATIVE_FROM = "send_native_from"
        private const val ARG_TRANSACTION_SENT_DETAIL = "transaction_sent_detail"
        private const val ARG_UTM = "utm"
        private const val ARG_MODE = "mode"
        private const val ARG_MODE_APP_NAME = "mode_app_name"
        private const val MODE_SEND_TRANSACTION = "send_transaction"
        private const val MODE_SIGN_MESSAGE = "sign_message"
    }
}
