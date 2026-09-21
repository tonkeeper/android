package com.tonapps.deposit.multicoin.screens.confirm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.num.FiatCurrency
import com.tonapps.chainkit.core.chain.model.num.FiatRate
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.deposit.multicoin.screens.confirm.engine.FeeAccount
import com.tonapps.deposit.multicoin.screens.confirm.engine.TxFee
import com.tonapps.wallet.data.multichain.account.AccountBalanceEntity
import com.tonapps.wallet.data.multichain.account.AccountEntity
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.asset.AssetRateEntity
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemDivider
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.cell.MoonPropertyTitle
import ui.components.moon.cell.MoonPropertyValue
import ui.components.moon.cell.MoonSlideConfirmation
import ui.components.moon.cell.MoonSlideConfirmationState
import ui.components.moon.cell.MoonTextCheckboxCell
import ui.preview.ThemedPreview
import ui.theme.UIKit

private const val PREVIEW_TON_ASSET = "ton/mainnet/coin"
private const val PREVIEW_USDT_ASSET =
    "ton/mainnet/jetton/0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe"

private fun previewChainFee(
    assetId: String,
    name: String,
    symbol: String,
    decimals: Int,
    balance: String,
    price: String,
    amount: String,
    viaRelayer: Boolean,
): TxFee {
    val account = AccountWithDetails(
        data = AccountEntity(
            walletId = "preview",
            network = "ton",
            mode = "mainnet",
            displayAddress = "UQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAJKZ",
            publicKey = "",
            segwitPublicKey = "",
            addressType = Address.Type.TonV5R1,
        ),
        balance = AccountBalanceEntity(available = balance),
        asset = AssetEntity(
            id = assetId,
            name = name,
            symbol = symbol,
            decimals = decimals,
            imageUrl = "",
        ),
        rate = AssetRateEntity(price = price, percentChange24h = "0", currencyCode = "USD"),
    )
    return TxFee(
        account = FeeAccount.Chain(account),
        fee = Fee.Value(BigInteger.parseString(amount)),
        viaRelayer = viaRelayer,
    )
}

private fun previewBatteryFee(charges: Int, balance: Int): TxFee {
    return TxFee(
        account = FeeAccount.Keeper(
            balance = BigInteger.fromInt(balance),
            rate = FiatRate(
                value = BigDecimal.parseString("0.0026"),
                currency = FiatCurrency("$"),
            ),
        ),
        fee = Fee.Value(BigInteger.fromInt(charges)),
        viaRelayer = true,
    )
}

@Preview
@Composable
private fun FeeMethodPickerPreview() {
    val native = previewChainFee(
        assetId = PREVIEW_TON_ASSET,
        name = "Toncoin",
        symbol = "TON",
        decimals = 9,
        balance = "12400000000",
        price = "5.42",
        amount = "5500000",
        viaRelayer = false,
    )
    val token = previewChainFee(
        assetId = PREVIEW_USDT_ASSET,
        name = "Tether USD",
        symbol = "USDT",
        decimals = 6,
        balance = "42000000",
        price = "1.0",
        amount = "120000",
        viaRelayer = true,
    )
    val battery = previewBatteryFee(charges = 42, balance = 1200)
    val insufficientBattery = previewBatteryFee(charges = 180, balance = 12)
        .toPickerOption(enabled = false)
        .copy(id = "battery_insufficient", actionTitle = "Deposit")

    ThemedPreview {
        Column {
            FeeMethodPickerContent(
                options = listOf(battery, native, token).map { it.toPickerOption() } + insufficientBattery,
                selectedId = battery.id,
                onSelect = {},
                onClose = {},
            )
        }
    }
}

@Preview
@Composable
private fun PriceImpactWarningDangerPreview() {
    ThemedPreview {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PriceImpactWarningContent(
                severity = PriceImpactSeverity.Danger,
                impact = "-6.5%",
                onConfirm = {},
                onClose = {},
            )
        }
    }
}

@Preview
@Composable
private fun PriceImpactWarningWarningPreview() {
    ThemedPreview {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PriceImpactWarningContent(
                severity = PriceImpactSeverity.Warning,
                impact = "-3.2%",
                onConfirm = {},
                onClose = {},
            )
        }
    }
}

@Preview
@Composable
private fun ExpandendScreenPreview() {
    ThemedPreview {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                MoonBundleCell {
                    Column {
                        MoonPropertyBigCell(
                            title = {
                                MoonPropertyTitle(title = stringResource(Localization.rate))
                            },
                            content = {
                                MoonPropertyValue(
                                    title = "1 TON ≈ 5.42 USDT",
                                    content = {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            progress = { 0.6f },
                                            trackColor = UIKit.colorScheme.icon.secondary,
                                            color = UIKit.colorScheme.icon.primary,
                                            gapSize = 0.dp,
                                        )
                                    },
                                )
                            },
                        )

                        MoonItemDivider()
                        MoonPropertyBigCell(
                            title = stringResource(Localization.min_received),
                            value = "≈ 5.30 USDT",
                            valueDescription = "≈ $5.28",
                        )

                        MoonItemDivider()
                        MoonPropertyBigCell(
                            title = {
                                MoonPropertyTitle(
                                    title = stringResource(Localization.slippage),
                                    infoTooltip = stringResource(Localization.swap_slippage_info),
                                )
                            },
                            content = { MoonPropertyValue(title = "1%") },
                        )

                        MoonItemDivider()
                        PriceImpactCell(
                            PriceImpact(text = "-3.2%", severity = PriceImpactSeverity.Warning)
                        )

                        MoonItemDivider()
                        MoonPropertyBigCell(
                            title = {
                                MoonPropertyTitle(title = stringResource(Localization.network_fee))
                            },
                            content = {
                                MoonPropertyValue(
                                    title = "≈ 0.05 TON",
                                    subtitle = "≈ $0.27",
                                )
                            },
                        )
                    }
                }
            }


            MoonTextCheckboxCell(
                text = "Avoid extra fees on future swaps",
                contentPadding = remember { PaddingValues(16.dp, 10.dp) },
                isChecked = true,
                onCheckedChanged = { },
                onInfo = { },
            )

            MoonSlideConfirmation(
                state = MoonSlideConfirmationState.Slider,
                title = stringResource(Localization.confirm),
                subtitle = stringResource(Localization.swipe_right),
                buttonTitle = stringResource(Localization.try_again),
                enabled = false,
                onConfirm = {},
                onDone = {},
            )
        }
    }
}
