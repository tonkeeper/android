package com.tonapps.deposit.multicoin.screens.confirm.perpetual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemTitle
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.cell.MoonPropertyTitle
import ui.components.moon.cell.MoonPropertyValue
import ui.preview.ThemedPreview
import ui.theme.UIKit
import ui.theme.modifiers.rememberShimmerPhase
import ui.theme.modifiers.shimmer

@Composable
internal fun PerpetualDepositFeesCell(
    fees: PerpetualDepositFees,
    activatesAccount: Boolean,
    symbol: String = fees.symbol,
    quoting: Boolean = false,
    onSwitchMethod: (() -> Unit)? = null,
) {
    PerpetualDepositFeeMethodCell(
        title = stringResource(Localization.perps_deposit_fees),
        value = fees.total,
        symbol = symbol,
        tooltip = perpetualDepositFeeBreakdown(fees.network, fees.provider, activatesAccount),
        quoting = quoting,
        onSwitchMethod = onSwitchMethod,
    )
}

@Composable
internal fun PerpetualDepositFeeMethodCell(
    title: String,
    value: String,
    symbol: String,
    tooltip: String?,
    quoting: Boolean = false,
    onSwitchMethod: (() -> Unit)? = null,
) {
    MoonPropertyBigCell(
        title = {
            MoonPropertyTitle(
                title = title,
                infoTooltip = tooltip,
            )
        },
        content = {
            if (quoting) {
                FeesQuotingValue()
            } else {
                MoonPropertyValue(
                    title = value,
                    content = { FeeMethodLabel(symbol = symbol, switchable = onSwitchMethod != null) },
                )
            }
        },
        onClick = onSwitchMethod.takeIf { !quoting },
    )
}

@Composable
internal fun perpetualDepositFeeBreakdown(network: String, provider: String, activatesAccount: Boolean): String {
    val networkLine = if (activatesAccount) {
        stringResource(Localization.perps_deposit_activation_fee_line, network)
    } else {
        stringResource(Localization.perps_deposit_network_fee_line, network)
    }
    val providerLine = stringResource(Localization.perps_deposit_provider_fee_line, provider)
    return remember(networkLine, providerLine) { "$networkLine\n$providerLine" }
}

@Composable
private fun FeeMethodLabel(symbol: String, switchable: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoonItemTitle(text = "·")
        if (switchable) {
            MoonItemTitle(text = symbol, color = UIKit.colorScheme.text.accent)
            MoonItemIcon(
                painter = painterResource(UIKitIcon.ic_switch_16),
                color = UIKit.colorScheme.text.accent,
            )
        } else {
            MoonItemTitle(text = symbol, color = UIKit.colorScheme.text.secondary)
        }
    }
}

@Composable
private fun FeesQuotingValue() {
    val phase by rememberShimmerPhase()
    Spacer(
        Modifier
            .width(96.dp)
            .height(20.dp)
            .shimmer(phase)
    )
}

@Preview
@Composable
private fun PerpetualDepositFeesCellPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpetualDepositFeesCell(
            fees = PerpetualDepositFees(
                total = "≈ $ 0.54",
                network = "≈ $ 0.52",
                provider = "≈ $ 0.02",
                symbol = "GRAM",
            ),
            activatesAccount = true,
        )
    }
}

@Preview
@Composable
private fun PerpetualDepositBatteryFeeCellPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpetualDepositFeeMethodCell(
            title = "Network fees",
            value = "≈ 20 charges",
            symbol = "Battery",
            tooltip = null,
            onSwitchMethod = {},
        )
    }
}
