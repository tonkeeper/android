package com.tonapps.deposit.screens.method

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.core.extensions.iconExternalUrl
import com.tonapps.deposit.components.VerticalAssetCell
import com.tonapps.deposit.screens.provider.ProviderConfirmDialog
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.mvi.props.observeSafeState
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.theme.UIKit
import ui.utils.toRichSpanStyle
import io.exchangeapi.models.ExchangeMerchantInfo
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonSmallItemTitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundleTitleCell
import ui.components.moon.cell.MoonInfoCell
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.container.MoonCutRow
import ui.components.moon.container.MoonScaffold
import ui.components.moon.container.OverlapDirection
import ui.components.moon.screen.MoonEmptyScreen
import ui.painterResource

internal const val KEY_CURRENCY_SELECTION_RESULT = "currency_selection_result"


@Composable
fun PaymentMethodScreen(
    feature: PaymentMethodFeature,
    fallbackAsset: RampAsset?,
    currencySelectionResult: WalletCurrency?,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onPaymentMethodClick: (asset: RampAsset, paymentMethodType: String, fiatCurrency: WalletCurrency) -> Unit,
    onBuyWithCrypto: (asset: RampAsset, currency: WalletCurrency) -> Unit,
    onSelectCurrency: (selectedCode: String?) -> Unit,
    onSelectNetwork: (asset: RampAsset, stablecoinCode: String, fiatCurrency: WalletCurrency?) -> Unit,
    onOpenP2P: (url: String) -> Unit,
    onAutoNavigateCrypto: ((asset: RampAsset, targetCurrency: WalletCurrency) -> Unit)? = null,
    onAutoNavigateCash: ((asset: RampAsset, paymentMethodType: String, fiatCurrency: WalletCurrency) -> Unit)? = null,
) {
    val state by feature.state.global.observeSafeState()
    val lifecycle = LocalLifecycleOwner.current
    var confirmationMerchant by remember { mutableStateOf<ExchangeMerchantInfo?>(null) }

    LaunchedEffect(currencySelectionResult) {
        if (currencySelectionResult != null) {
            feature.selectCurrency(currencySelectionResult)
        }
    }

    LaunchedEffect(Unit) {
        feature.events.flowWithLifecycle(lifecycle.lifecycle).collect { event ->
            when (event) {
                is PaymentMethodEvent.ShowP2PConfirmation -> confirmationMerchant = event.merchant
                is PaymentMethodEvent.OpenP2P -> onOpenP2P(event.url)
                is PaymentMethodEvent.AutoNavigateCrypto -> onAutoNavigateCrypto?.invoke(event.resolvedAsset, event.targetCurrency)
                is PaymentMethodEvent.AutoNavigateCash -> onAutoNavigateCash?.invoke(event.resolvedAsset, event.paymentMethodType, event.fiatCurrency)
            }
        }
    }

    confirmationMerchant?.let { merchant ->
        val style = UIKit.typography.body1
        val color = UIKit.colorScheme.text.accent
        val buttons = merchant.buttons
        val description = remember(color, buttons) {
            if (buttons.isEmpty()) return@remember null
            buildAnnotatedString {
                val linkStyle = style.toRichSpanStyle(color = color)
                buttons
                    .filter { it.url.isNotEmpty() }
                    .forEachIndexed { index, button ->
                        if (index > 0) append(" · ")
                        withLink(
                            LinkAnnotation.Url(
                                url = button.url,
                                styles = TextLinkStyles(linkStyle),
                            )
                        ) { append(button.title) }
                    }
            }
        }

        ProviderConfirmDialog(
            title = merchant.title,
            icon = merchant.image,
            description = description,
            onConfirm = { feature.sendAction(PaymentMethodAction.AllowP2P(it)) },
            onClose = { confirmationMerchant = null },
        )
    }

    val resolveAsset = { feature.resolvedAsset ?: fallbackAsset }

    PaymentMethodContent(
        rampType = feature.rampType,
        state = state,
        onBack = onBack,
        onClose = onClose,
        onPaymentMethodClick = { type, currency ->
            resolveAsset()?.let { onPaymentMethodClick(it, type, currency) }
        },
        onP2PMethodClick = { feature.sendAction(PaymentMethodAction.CheckP2PMethod) },
        onBuyWithCrypto = { currency ->
            resolveAsset()?.let { onBuyWithCrypto(it, currency) }
        },
        onSelectCurrency = onSelectCurrency,
        onSelectNetwork = { code, currency ->
            resolveAsset()?.let { onSelectNetwork(it, code, currency) }
        },
        onRetry = { feature.sendAction(PaymentMethodAction.Init) },
    )
}

@Composable
private fun PaymentMethodContent(
    rampType: RampType,
    state: PaymentMethodState,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onPaymentMethodClick: (paymentMethodType: String, fiatCurrency: WalletCurrency) -> Unit,
    onP2PMethodClick: () -> Unit,
    onBuyWithCrypto: (WalletCurrency) -> Unit,
    onSelectCurrency: (selectedCode: String?) -> Unit,
    onSelectNetwork: (stablecoinCode: String, fiatCurrency: WalletCurrency?) -> Unit,
) {
    MoonScaffold(
        title = stringResource(Localization.payment_method),
        onClose = onClose,
        onBack = onBack,
    ) {
        when (state) {
            is PaymentMethodState.Loading -> MoonLoaderCell()
            is PaymentMethodState.Empty -> MoonEmptyScreen(
                text = stringResource(Localization.cant_find_anything),
                buttonText = stringResource(Localization.retry),
                onButtonClick = onRetry,
            )

            is PaymentMethodState.Data -> {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding(),
                ) {
                    if (state.showCashSection) {
                        val currencyUri = state.selectedCurrency?.iconUri
                        val currencyCode = state.selectedCurrency?.code
                        val showCurrencySwitch = state.currencies.size > 1

                        MoonBundleTitleCell(
                            title = when (rampType) {
                                RampType.RampOn -> stringResource(Localization.deposit_buy_with_cash)
                                RampType.RampOff -> stringResource(Localization.ramp_sell_with_cash)
                            },
                            onClick = if (showCurrencySwitch) {
                                { onSelectCurrency(state.selectedCurrency?.code) }
                            } else {
                                null
                            }
                        ) {
                            if (currencyCode != null) {
                                MoonItemImage(currencyUri?.toString(), size = 16.dp)
                                MoonSmallItemTitle(text = currencyCode)
                                if (showCurrencySwitch) {
                                    MoonItemIcon(painter = painterResource(UIKitIcon.ic_switch_16))
                                }
                            }
                        }

                        MoonBundleCell {
                            Column {
                                state.paymentMethods.fastForEachIndexed { index, method ->
                                    if (index > 0) MoonItemDivider()
                                    VerticalAssetCell(
                                        name = method.title,
                                        assetImageUrl = method.iconUrl,
                                        extendedName = method.subtitle,
                                        onClick = {
                                            val currency = state.selectedCurrency
                                                ?: state.currencies.firstOrNull()
                                                ?: return@VerticalAssetCell
                                            if (method.isP2P) {
                                                onP2PMethodClick()
                                            } else {
                                                onPaymentMethodClick(method.type, currency)
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (state.showCryptoSection) {
                        val cryptoAssets = state.cryptoAssets

                        Spacer(modifier = Modifier.height(16.dp))
                        MoonInfoCell(
                            text = stringResource(Localization.ramp_crypto_swap_info)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        MoonBundleCell {
                            Column {
                                cryptoAssets.fastForEachIndexed { index, currency ->
                                    key(currency.key) {
                                        if (index > 0) MoonItemDivider()
                                        VerticalAssetCell(currency) { onBuyWithCrypto(currency) }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (state.showStablecoinSection) {
                        Spacer(modifier = Modifier.height(16.dp))
                        MoonInfoCell(
                            text = when (rampType) {
                                RampType.RampOn -> stringResource(
                                    Localization.ramp_stablecoin_buy_info,
                                    state.asset.currencyCode,
                                    state.asset.toCurrency.chain.name
                                )
                                RampType.RampOff -> stringResource(Localization.ramp_stablecoin_sell_info)
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        MoonBundleCell {
                            Column {
                                val context = LocalContext.current
                                state.stablecoinAssets.fastForEachIndexed { index, stablecoin ->
                                    if (index > 0) MoonItemDivider()
                                    val networkIcons = remember(stablecoin.code, state.stablecoinNetworks) {
                                        state.stablecoinNetworks[stablecoin.code.uppercase()]
                                            ?.mapNotNull { it.networkImage ?: it.currency.chain.iconExternalUrl(context) }
                                            ?.distinct()
                                            ?: emptyList()
                                    }
                                    VerticalAssetCell(
                                        currency = stablecoin,
                                        isAbstract = true,
                                        titleContent = {
                                            if (networkIcons.isNotEmpty()) {
                                                MoonCutRow(
                                                    direction = OverlapDirection.StartOnTop,
                                                ) {
                                                    networkIcons.forEach { icon ->
                                                        MoonItemImage(image = icon, size = 18.dp)
                                                    }
                                                }
                                            }
                                        },
                                        onClick = { onSelectNetwork(stablecoin.code, state.selectedCurrency) },
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

