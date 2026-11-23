package com.tonapps.deposit.multicoin.screens.buy.method

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import io.exchangeapi.models.ExchangeMerchantSlug
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonTopAppBar
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.MoonTopAppBarSubtitle
import ui.components.moon.MoonTopAppBarTitle
import ui.components.moon.cell.TextCheckCell
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoCard
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoCardIcon
import ui.components.moon.MoonItemTitle
import ui.components.moon.dialog.MoonAlertDialog
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoProviderLine
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoProviderRow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.deposit.multicoin.AccentCircleIcon
import com.tonapps.deposit.multicoin.domain.RampPaymentMethod
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLoader
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.MoonBundleTitleCell
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.cell.TextCell
import ui.components.moon.container.MoonScaffold
import ui.components.moon.screen.MoonEmptyScreen
import ui.components.moon.screen.MoonEmptyScreenType
import ui.preview.ThemedPreview
import ui.theme.UIKit
import uikit.navigation.Navigation.Companion.navigation

@Composable
fun BuyCryptoMethodScreen(
    feature: BuyCryptoMethodFeature,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onPicked: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onReceive: () -> Unit,
) {
    val loading by feature.loading.collectAsState()
    val failed by feature.failed.collectAsState()
    val methods by feature.methods.collectAsState()
    val openingP2P by feature.openingP2P.collectAsState()
    val providerLine by feature.providerLine.collectAsState()
    val providers by feature.providers.collectAsState()
    var providerSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val p2pErrorText = stringResource(Localization.p2p_error)
    val removedText = stringResource(Localization.moonpay_card_removed)
    val errorText = stringResource(Localization.unknown_error)
    val toastColor = UIKit.colorScheme.background.contentTint.toArgb()
    LaunchedEffect(feature) {
        feature.events.collect { event ->
            when (event) {
                is BuyCryptoMethodEvent.Picked -> onPicked()
                is BuyCryptoMethodEvent.ProvidersFailed -> context.navigation?.toast(message = errorText, loading = false, color = toastColor)
                is BuyCryptoMethodEvent.OpenUrl -> onOpenUrl(event.url)
                is BuyCryptoMethodEvent.P2PFailed -> context.navigation?.toast(
                    message = p2pErrorText,
                    loading = false,
                    color = toastColor,
                )
                is BuyCryptoMethodEvent.CardRemoved -> context.navigation?.toast(message = removedText, loading = false, color = toastColor)
                is BuyCryptoMethodEvent.CardRemovalFailed -> context.navigation?.toast(
                    message = errorText,
                    loading = false,
                    color = toastColor,
                )
            }
        }
    }

    // A load of «Buy crypto» decides who sells: a sheet open when it starts goes, as its taps would be dropped.
    val switchable = providerLine?.switchable == true
    LaunchedEffect(switchable) {
        if (!switchable) {
            providerSheet = false
        }
    }
    if (providerSheet) {
        BuyCryptoProviderSheet(
            providers = providers,
            onPick = { feature.sendAction(BuyCryptoMethodAction.PickProvider(it)) },
            onClose = { providerSheet = false },
        )
    }

    BuyCryptoMethodContent(
        loading = loading,
        failed = failed,
        methods = methods,
        openingP2P = openingP2P,
        providerLine = providerLine,
        onProviderLine = {
            if (providerLine?.failed == true) {
                feature.sendAction(BuyCryptoMethodAction.RetryProviders)
            } else {
                providerSheet = true
            }
        },
        onBack = onBack,
        onClose = onClose,
        onPick = { feature.sendAction(BuyCryptoMethodAction.Pick(it)) },
        onReload = { feature.sendAction(BuyCryptoMethodAction.Reload) },
        onReceive = onReceive,
        onRemove = { feature.sendAction(BuyCryptoMethodAction.RemoveCard(it)) },
    )
}

@Composable
private fun BuyCryptoMethodContent(
    loading: Boolean,
    failed: Boolean,
    methods: List<BuyCryptoMethodRow>,
    openingP2P: Boolean,
    providerLine: BuyCryptoProviderLine?,
    onProviderLine: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onPick: (String) -> Unit,
    onReload: () -> Unit,
    onReceive: () -> Unit,
    onRemove: (String) -> Unit,
) {
    MoonScaffold(
        topBar = {
            MoonTopAppBar(
                title = { MoonTopAppBarTitle(text = stringResource(Localization.payment_method)) },
                subtitle = providerLine?.let { line -> { ProviderLine(line, onProviderLine) } },
                actionIconRes = UIKitIcon.ic_close_16,
                onActionClick = onClose,
                navigationIconRes = UIKitIcon.ic_chevron_left_16,
                onNavigationClick = onBack,
                ignoreSystemOffset = true,
                showDivider = false,
                backgroundColor = Color.Transparent,
            )
        },
    ) {
        if (loading) {
            MoonLoaderCell()
            return@MoonScaffold
        }
        Column(
            modifier = Modifier
                .nestedScroll(rememberNestedScrollInteropConnection())
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            if (failed) {
                MoonEmptyScreen(
                    text = stringResource(Localization.buy_crypto_load_failed),
                    type = MoonEmptyScreenType.Error,
                    buttonText = stringResource(Localization.retry),
                    onButtonClick = onReload,
                )
            } else if (methods.isNotEmpty()) {
                val (saved, cash) = methods.partition { it.card != null }
                if (saved.isNotEmpty()) {
                    MoonBundleTitleCell(title = stringResource(Localization.buy_crypto_saved_cards))
                    saved.forEachIndexed { index, method ->
                        // Keyed by the card: a menu or a confirmation stays with its card when another one leaves.
                        key(method.id) {
                            MoonBundleCell(position = MoonBundlePosition.default(saved.size, index)) {
                                SavedCardCell(
                                    method = method,
                                    onClick = if (openingP2P || !method.enabled) {
                                        null
                                    } else {
                                        { onPick(method.id) }
                                    },
                                    onRemove = onRemove,
                                )
                            }
                        }
                    }
                }
                MoonBundleTitleCell(title = stringResource(Localization.buy_crypto_cash))
                cash.forEachIndexed { index, method ->
                    MoonBundleCell(position = MoonBundlePosition.default(cash.size, index)) {
                        MethodCell(
                            method = method,
                            opening = openingP2P && method.isP2P,
                            onClick = if (openingP2P) {
                                null
                            } else {
                                { onPick(method.id) }
                            },
                        )
                    }
                }
            }
            MoonBundleTitleCell(title = stringResource(Localization.crypto))
            MoonBundleCell {
                TextCell(
                    title = stringResource(Localization.buy_crypto_crypto_deposit),
                    subtitle = stringResource(Localization.deposit_from_another_wallet_mc),
                    image = { AccentCircleIcon(painterResource(UIKitIcon.ic_qr_code_28)) },
                    content = { MoonItemIcon(painterResource(UIKitIcon.ic_chevron_right_16)) },
                    onClick = onReceive,
                    minHeight = ROW_HEIGHT,
                )
            }
        }
    }
}

@Composable
private fun MethodCell(method: BuyCryptoMethodRow, opening: Boolean, onClick: (() -> Unit)?) {
    val badge = method.badge
    TextCell(
        title = method.title,
        subtitle = method.subtitle,
        tags = if (badge != null) {
            { MoonLabel(text = badge) }
        } else {
            null
        },
        image = { MoonItemImage(image = method.iconUrl, size = ICON_SIZE) },
        content = {
            if (opening) {
                MoonLoader(modifier = Modifier.size(LOADER_SIZE))
            } else if (method.selected) {
                MoonItemIcon(
                    painter = painterResource(UIKitIcon.ic_donemark_thin_28),
                    size = CHECK_SIZE,
                    color = UIKit.colorScheme.accent.blue,
                )
            }
        },
        onClick = onClick,
        minHeight = ROW_HEIGHT,
    )
}

// The card's menu stays on a card MoonPay no longer takes: only its content is dimmed, as on iOS.
@Composable
private fun SavedCardCell(method: BuyCryptoMethodRow, onClick: (() -> Unit)?, onRemove: (String) -> Unit) {
    val card = requireNotNull(method.card)
    val alpha = DISABLED_ALPHA.takeIf { !method.enabled } ?: 1f
    var menuShown by remember { mutableStateOf(false) }
    var confirmShown by remember { mutableStateOf(false) }
    TextCell(
        title = {
            MoonItemTitle(text = method.title, modifier = Modifier.alpha(alpha))
        },
        subtitle = {
            Text(
                text = cardSubtitle(method.subtitle.orEmpty(), card.expiry),
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.text.secondary,
                maxLines = 1,
                modifier = Modifier.alpha(alpha),
            )
        },
        image = { BuyCryptoCardIcon(card.icon, modifier = Modifier.alpha(alpha)) },
        content = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (method.selected) {
                    MoonItemIcon(
                        painter = painterResource(UIKitIcon.ic_donemark_thin_28),
                        size = CHECK_SIZE,
                        color = UIKit.colorScheme.accent.blue,
                    )
                }
                if (card.removable) {
                    Box {
                        MoonItemIcon(
                            painter = painterResource(UIKitIcon.ic_ellipsis_16),
                            modifier = Modifier.padding(start = 8.dp),
                            onClick = { menuShown = true },
                        )
                        DropdownMenu(
                            containerColor = UIKit.colorScheme.background.contentTint,
                            shape = UIKit.shapes.large,
                            expanded = menuShown,
                            onDismissRequest = { menuShown = false },
                        ) {
                            TextCell(
                                title = stringResource(Localization.moonpay_card_remove),
                                content = { MoonItemIcon(painterResource(UIKitIcon.ic_trash_bin_16)) },
                                onClick = {
                                    menuShown = false
                                    confirmShown = true
                                },
                            )
                        }
                    }
                }
            }
        },
        onClick = onClick,
        minHeight = ROW_HEIGHT,
    )
    if (confirmShown) {
        MoonAlertDialog(
            title = stringResource(Localization.moonpay_card_remove_title),
            message = stringResource(Localization.moonpay_card_remove_message, method.subtitle.orEmpty()),
            positiveButtonText = stringResource(Localization.moonpay_card_remove),
            positiveButtonColor = UIKit.colorScheme.accent.red,
            onPositiveClick = { onRemove(card.cardId) },
            negativeButtonText = stringResource(Localization.cancel),
            onDismiss = { confirmShown = false },
        )
    }
}

@Composable
private fun cardSubtitle(number: String, expiry: BuyCryptoCardExpiry?): AnnotatedString {
    expiry ?: return AnnotatedString(number)
    val date = expiry.date
    val text = when {
        date == null -> stringResource(Localization.moonpay_card_unavailable)
        expiry.state == BuyCryptoCardExpiry.State.Expired -> stringResource(Localization.moonpay_card_expired, date)
        else -> stringResource(Localization.moonpay_card_expires, date)
    }
    val color = when (expiry.state) {
        BuyCryptoCardExpiry.State.Valid -> UIKit.colorScheme.text.secondary
        BuyCryptoCardExpiry.State.Soon -> UIKit.colorScheme.accent.orange
        BuyCryptoCardExpiry.State.Expired -> UIKit.colorScheme.accent.red
    }
    return buildAnnotatedString {
        append(number)
        append(" · ")
        withStyle(SpanStyle(color = color)) { append(text) }
    }
}

private const val DISABLED_ALPHA = 0.5f
private val ICON_SIZE = 44.dp
private val CHECK_SIZE = 28.dp
private val LOADER_SIZE = 20.dp
private val ROW_HEIGHT = 76.dp

@Preview
@Composable
private fun BuyCryptoMethodPreview() {
    ThemedPreview {
        BuyCryptoMethodContent(
            loading = false,
            failed = false,
            methods = listOf(
                BuyCryptoMethodRow(
                    id = "card:visa",
                    title = "Debit card",
                    subtitle = "Visa •••• 4148",
                    badge = null,
                    iconUrl = null,
                    selected = true,
                    card = BuyCryptoSavedCard(
                        cardId = "visa",
                        icon = BuyCryptoCard(BuyCryptoCard.Logo.Visa, "4148"),
                        expiry = BuyCryptoCardExpiry("10/28", BuyCryptoCardExpiry.State.Valid),
                        removable = true,
                    ),
                ),
                BuyCryptoMethodRow(
                    id = "card:mc",
                    title = "Debit card",
                    subtitle = "Mastercard •••• 4771",
                    badge = null,
                    iconUrl = null,
                    selected = false,
                    card = BuyCryptoSavedCard(
                        cardId = "mc",
                        icon = BuyCryptoCard(BuyCryptoCard.Logo.Mastercard, "4771"),
                        expiry = BuyCryptoCardExpiry("11/26", BuyCryptoCardExpiry.State.Soon),
                        removable = true,
                    ),
                ),
                BuyCryptoMethodRow(
                    id = "card:old",
                    title = "Credit card",
                    subtitle = "Amex •••• 7482",
                    badge = null,
                    iconUrl = null,
                    selected = false,
                    enabled = false,
                    card = BuyCryptoSavedCard(
                        cardId = "old",
                        icon = BuyCryptoCard(BuyCryptoCard.Logo.Name("AMEX"), "7482"),
                        expiry = BuyCryptoCardExpiry("11/24", BuyCryptoCardExpiry.State.Expired),
                        removable = true,
                    ),
                ),
                BuyCryptoMethodRow("card", "Debit Card", "Instant", "Recommended", null, selected = false),
                BuyCryptoMethodRow("revolut", "Revolut Pay", "Instant", null, null, selected = false),
                BuyCryptoMethodRow(RampPaymentMethod.P2P_ID, "P2P Express", "Buy from other users", "For beginners", null, selected = false),
            ),
            openingP2P = true,
            providerLine = BuyCryptoProviderLine(name = "MoonPay", switchable = true, failed = false),
            onProviderLine = {},
            onBack = {},
            onClose = {},
            onPick = {},
            onReload = {},
            onReceive = {},
            onRemove = {},
        )
    }
}

@Preview
@Composable
private fun BuyCryptoMethodFailedPreview() {
    ThemedPreview {
        BuyCryptoMethodContent(
            loading = false,
            failed = true,
            methods = emptyList(),
            openingP2P = false,
            providerLine = BuyCryptoProviderLine(name = "MoonPay", switchable = true, failed = false),
            onProviderLine = {},
            onBack = {},
            onClose = {},
            onPick = {},
            onReload = {},
            onReceive = {},
            onRemove = {},
        )
    }
}

@Composable
private fun RowScope.ProviderLine(line: BuyCryptoProviderLine, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(enabled = line.switchable, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val name = line.name.orEmpty()
        val text = if (line.name == null) {
            stringResource(Localization.provider)
        } else {
            stringResource(Localization.buy_crypto_via, name)
        }
        val accent = UIKit.colorScheme.accent.blue
        MoonTopAppBarSubtitle(
            text = remember(text, name, accent, line.switchable) {
                buildAnnotatedString {
                    // Blue only where it opens the provider sheet, as on iOS.
                    val start = text.lastIndexOf(name).takeIf { name.isNotEmpty() && line.switchable } ?: -1
                    if (start < 0) {
                        append(text)
                    } else {
                        append(text.substring(0, start))
                        withStyle(SpanStyle(color = accent)) { append(name) }
                        append(text.substring(start + name.length))
                    }
                }
            },
        )
        if (line.switchable) {
            Spacer(modifier = Modifier.width(4.dp))
            MoonItemIcon(painterResource(UIKitIcon.ic_switch_16), size = 12.dp, color = UIKit.colorScheme.accent.blue)
        }
    }
}

@Composable
private fun BuyCryptoProviderSheet(
    providers: List<BuyCryptoProviderRow>,
    onPick: (ExchangeMerchantSlug) -> Unit,
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)
    MoonModalDialog(navigator = navigator) {
        MoonTopAppBarSimple(
            title = stringResource(Localization.provider),
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = { navigator.close() },
        )
        MoonBundleCell {
            Column {
                providers.forEachIndexed { index, provider ->
                    if (index > 0) {
                        MoonItemDivider()
                    }
                    TextCheckCell(
                        title = provider.title,
                        subtitle = provider.subtitle,
                        isChecked = provider.selected,
                        onCheckedChange = {
                            navigator.close()
                            onPick(provider.id)
                        },
                        minHeight = ROW_HEIGHT,
                        image = {
                            if (provider.isMoonPay) {
                                Image(
                                    painter = painterResource(UIKitIcon.ic_provider_moonpay),
                                    contentDescription = null,
                                    modifier = Modifier.size(PROVIDER_IMAGE_SIZE).clip(UIKit.shapes.large),
                                )
                            } else {
                                MoonItemImage(image = provider.imageUrl, size = PROVIDER_IMAGE_SIZE, shape = UIKit.shapes.large)
                            }
                        },
                    )
                }
            }
        }
    }
}

private val PROVIDER_IMAGE_SIZE = 44.dp

@Preview
@Composable
private fun BuyCryptoProviderSheetPreview() {
    ThemedPreview {
        BuyCryptoProviderSheet(
            providers = listOf(
                BuyCryptoProviderRow(ExchangeMerchantSlug.moonpay, "MoonPay", "Card, Apple Pay, PayPal", null, selected = true),
                BuyCryptoProviderRow(ExchangeMerchantSlug.mercuryo, "Mercuryo", "Card, Apple Pay", null, selected = false),
                BuyCryptoProviderRow(ExchangeMerchantSlug.transak, "Transak", "Card, Apple Pay, Bank transfer", null, selected = false),
            ),
            onPick = {},
            onClose = {},
        )
    }
}
