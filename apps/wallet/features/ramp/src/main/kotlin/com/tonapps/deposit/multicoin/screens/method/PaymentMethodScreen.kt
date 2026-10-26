package com.tonapps.deposit.multicoin.screens.method

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.deposit.multicoin.data.RampAssetDetail
import com.tonapps.deposit.multicoin.data.RampMethod
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import io.exchangeapi.models.ExchangePaymentMethodType
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonTopAppBar
import ui.components.moon.MoonTopAppBarSubtitle
import ui.components.moon.MoonTopAppBarTitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.TextCell
import ui.components.moon.container.MoonScaffold
import ui.components.moon.screen.MoonEmptyScreen
import ui.components.moon.screen.MoonLoadingScreen
import ui.painterResource
import ui.theme.UIKit
import ui.workaround.hideKeyboardOnScrollConnection
import uikit.navigation.Navigation.Companion.navigation

@Composable
fun PaymentMethodScreen(
    feature: PaymentMethodFeature,
    currencySelectionResult: WalletCurrency?,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onSelectCurrency: (selectedCode: String?, availableCodes: List<String>) -> Unit,
    onPaymentMethodSelected: (asset: RampAssetDetail, method: RampMethod, fiatCode: String) -> Unit,
    onOpenP2P: (url: String) -> Unit,
) {
    val state by feature.state.collectAsState()
    val dataState = state as? PaymentMethodState.Data
    val fiatCode = dataState?.fiatCode
    val context = LocalContext.current

    LaunchedEffect(currencySelectionResult) {
        if (currencySelectionResult != null) {
            feature.selectCurrency(currencySelectionResult)
        }
    }

    val p2pErrorText = stringResource(Localization.p2p_error)
    val toastColor = UIKit.colorScheme.background.contentTint.toArgb()

    LaunchedEffect(feature) {
        feature.events.collect { event ->
            when (event) {
                is PaymentMethodEvent.OpenP2P -> onOpenP2P(event.url)
                is PaymentMethodEvent.ShowP2PError -> context.navigation?.toast(
                    message = p2pErrorText,
                    loading = false,
                    color = toastColor,
                )
            }
        }
    }

    MoonScaffold(
        topBar = {
            MoonTopAppBar(
                title = { MoonTopAppBarTitle(text = stringResource(Localization.payment_method)) },
                onTitleClick = dataState?.let { data ->
                    { onSelectCurrency(data.fiatCode, data.supportedFiatCodes) }
                },
                subtitle = fiatCode?.let { code ->
                    { PaymentMethodSubtitle(fiatCode = code) }
                },
                actionIconRes = UIKitIcon.ic_close_16,
                onActionClick = onClose,
                navigationIconRes = UIKitIcon.ic_chevron_left_16,
                onNavigationClick = onBack,
                ignoreSystemOffset = true,
                showDivider = false,
                backgroundColor = Color.Transparent
            )
        },
    ) {
        when (val current = state) {
            is PaymentMethodState.Loading -> MoonLoadingScreen()
            is PaymentMethodState.Empty -> MoonEmptyScreen(
                text = stringResource(Localization.cant_find_anything),
                buttonText = stringResource(Localization.retry),
                onButtonClick = feature::retry,
            )
            is PaymentMethodState.Data -> {
                if (current.methods.isEmpty()) {
                    MoonEmptyScreen(
                        text = stringResource(Localization.cant_find_anything),
                        buttonText = stringResource(Localization.retry),
                        onButtonClick = feature::retry,
                    )
                } else {
                    Methods(
                        asset = current.asset,
                        methods = current.methods,
                        onPaymentMethodSelected = { asset, method ->
                            feature.trackPaymentMethodClick(method, current.fiatCode)
                            onPaymentMethodSelected(asset, method, current.fiatCode)
                        },
                        onP2PMethodClick = {
                            feature.openP2PMethod()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentMethodSubtitle(fiatCode: String) {
    val accentColor = UIKit.colorScheme.accent.blue
    val prefix = stringResource(Localization.methods_of_purchase_for)

    MoonTopAppBarSubtitle(
        text = remember(fiatCode, prefix, accentColor) {
            buildAnnotatedString {
                append(prefix)
                append(" ")
                withStyle(SpanStyle(color = accentColor)) {
                    append(fiatCode)
                }
            }
        },
    )
    Spacer(modifier = Modifier.width(4.dp))

    MoonItemIcon(
        painter = painterResource(UIKitIcon.ic_switch_16),
        size = 12.dp,
        color = accentColor,
    )
}

@Composable
private fun Methods(
    asset: RampAssetDetail,
    methods: List<RampMethod>,
    onPaymentMethodSelected: (asset: RampAssetDetail, method: RampMethod) -> Unit,
    onP2PMethodClick: (method: RampMethod) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .nestedScroll(hideKeyboardOnScrollConnection())
            .nestedScroll(rememberNestedScrollInteropConnection()),
        contentPadding = PaddingValues(
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
        ),
    ) {
        itemsIndexed(
            items = methods,
            key = { _, method -> method.type.value },
            contentType = { _, _ -> "method" },
        ) { index, method ->
            MoonBundleCell(position = MoonBundlePosition.default(methods.size, index)) {
                TextCell(
                    title = method.name,
                    image = { MoonItemImage(image = method.image, size = 44.dp) },
                    onClick = {
                        if (method.type == ExchangePaymentMethodType.p2p) {
                            onP2PMethodClick(method)
                        } else {
                            onPaymentMethodSelected(asset, method)
                        }
                    },
                    minHeight = 76.dp,
                )
            }
        }
    }
}
