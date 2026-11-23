package com.tonapps.tonkeeper.ui.screen.battery.recharge

import android.os.Bundle
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import uikit.base.BaseArgs

data class RechargeArgs(
    val token: AccountTokenEntity?,
    val isGift: Boolean,
    val from: BatteryNativeFrom,
    val utm: UtmParameters,
    val selectsCustomAmount: Boolean = false,
) : BaseArgs() {

    constructor(bundle: Bundle) : this(
        token = bundle.getParcelableCompat(ARG_TOKEN),
        isGift = bundle.getBoolean(ARG_IS_GIFT),
        from = BatteryNativeFrom.valueOf(bundle.getString(ARG_FROM)!!),
        utm = bundle.getParcelableCompat<UtmParameters>(ARG_UTM) ?: UtmParameters.Empty,
        selectsCustomAmount = bundle.getBoolean(ARG_CUSTOM_AMOUNT),
    )

    override fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putParcelable(ARG_TOKEN, token)
        bundle.putBoolean(ARG_IS_GIFT, isGift)
        bundle.putString(ARG_FROM, from.name)
        bundle.putParcelable(ARG_UTM, utm)
        bundle.putBoolean(ARG_CUSTOM_AMOUNT, selectsCustomAmount)
        return bundle
    }

    private companion object {
        private const val ARG_TOKEN = "token"
        private const val ARG_IS_GIFT = "is_gift"
        private const val ARG_FROM = "from"
        private const val ARG_UTM = "utm"
        private const val ARG_CUSTOM_AMOUNT = "custom_amount"
    }
}
