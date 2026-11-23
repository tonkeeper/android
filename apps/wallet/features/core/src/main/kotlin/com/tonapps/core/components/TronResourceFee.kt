package com.tonapps.core.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.wallet.data.multichain.activity.TronResourceEntity
import com.tonapps.wallet.localization.Localization

@Composable
fun TronResourceEntity.feeTexts(): Pair<String, String?>? {
    val energyText = energy.takeIf { it > 0 }
        ?.let { stringResource(Localization.tron_resource_energy, it.formatGrouped()) }
    val bandwidthText = bandwidth.takeIf { it > 0 }
        ?.let { stringResource(Localization.tron_resource_bandwidth, it.formatGrouped()) }
    return when {
        energyText != null -> energyText to bandwidthText
        bandwidthText != null -> bandwidthText to null
        else -> null
    }
}

private fun Long.formatGrouped(): String = String.format(CurrencyFormatter.locale, "%,d", this)
