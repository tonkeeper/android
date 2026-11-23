package com.tonapps.tonkeeper.ui.screen.battery.recharge.asset

import android.os.Bundle
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.extensions.getParcelableCompat
import uikit.base.BaseArgs

data class BatteryAssetRechargeArgs(
    /** The asset paid with, as the multichain API names it. */
    val assetId: String,
    val from: BatteryNativeFrom,
    val utm: UtmParameters,
) : BaseArgs() {

    constructor(bundle: Bundle) : this(
        assetId = bundle.getString(ARG_ASSET_ID)!!,
        from = BatteryNativeFrom.valueOf(bundle.getString(ARG_FROM)!!),
        utm = bundle.getParcelableCompat<UtmParameters>(ARG_UTM) ?: UtmParameters.Empty,
    )

    override fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putString(ARG_ASSET_ID, assetId)
        bundle.putString(ARG_FROM, from.name)
        bundle.putParcelable(ARG_UTM, utm)
        return bundle
    }

    private companion object {
        private const val ARG_ASSET_ID = "asset_id"
        private const val ARG_FROM = "from"
        private const val ARG_UTM = "utm"
    }
}
