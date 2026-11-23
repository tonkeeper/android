package com.tonapps.deposit.multicoin.screens.buy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonapps.core.LocalIsTopFragment
import com.tonapps.core.components.tokenChainImageUrl
import com.tonapps.deposit.multicoin.FiatSubtitle
import com.tonapps.deposit.multicoin.moonpay.MoonPayDisclosureText
import com.tonapps.deposit.multicoin.moonpay.MoonPayPaymentDisclosures
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayBuyQuote
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrames
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayInlineFrame
import com.tonapps.deposit.multicoin.screens.moonpay.MoonPayPaymentData
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import com.wallet.crypto.trustapp.common.ui.components.MoonEditText
import com.wallet.crypto.trustapp.common.ui.components.amountFieldScroll
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonTopAppBar
import ui.components.moon.MoonTopAppBarTitle
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonButtonCellDefaults
import ui.components.moon.cell.TextCell
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.components.moon.container.MoonScaffold
import ui.components.moon.screen.MoonEmptyScreen
import ui.components.moon.screen.MoonEmptyScreenType
import ui.preview.ThemedPreview
import ui.theme.UIKit
import ui.theme.modifiers.rememberShimmerPhase
import ui.theme.modifiers.shimmer
import uikit.navigation.Navigation.Companion.navigation

internal const val KEY_BUY_CRYPTO_ASSET_RESULT = "buy_crypto_asset_result"

@Composable
fun BuyCryptoScreen(
    feature: BuyCryptoFeature,
    onClose: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMoonPay: (MoonPayPaymentData) -> Unit,
    onPickCurrency: (fiat: String) -> Unit,
    onPickAsset: (fiat: String) -> Unit,
    onPickMethod: () -> Unit,
    onCryptoDeposit: () -> Unit,
    showKeyboard: Boolean,
    onShowKeyboardChange: (Boolean) -> Unit,
    covered: Boolean = false,
) {
    val loading by feature.loading.collectAsState()
    val failed by feature.failed.collectAsState()
    val nothingToBuy by feature.nothingToBuy.collectAsState()
    val fiat by feature.fiat.collectAsState()
    val fiatFlag by feature.fiatFlag.collectAsState()
    val amount by feature.amount.collectAsState()
    val amountField by feature.amountField.collectAsState()
    val receive by feature.receive.collectAsState()
    val quoting by feature.quoting.collectAsState()
    val rate by feature.rate.collectAsState()
    val limit by feature.limit.collectAsState()
    val rateTimer by feature.rateTimer.collectAsState()
    val payment by feature.payment.collectAsState()
    val quickAmounts by feature.quickAmounts.collectAsState()
    val canContinue by feature.canContinue.collectAsState()
    val continuing by feature.continuing.collectAsState()
    val currentSetup by feature.currentSetup.collectAsState()
    val payButton by feature.payButton.collectAsState()
    val disclosures by feature.disclosures.collectAsState()
    val priceDetails by feature.priceDetails.collectAsState()
    val priceDetailsOpenable by feature.priceDetailsOpenable.collectAsState()
    val frames = koinInject<MoonPayFrames>()

    // Under MoonPay's status sheet the screen neither refreshes the rate nor takes the keyboard. [covered] comes from
    // the route: a pop during the push never takes the screen out of composition, so it never shows a return.
    val onTop = LocalIsTopFragment.current
    LaunchedEffect(feature, onTop, covered) {
        feature.sendAction(BuyCryptoAction.SetVisible(onTop && !covered))
    }
    DisposableEffect(feature) {
        onDispose { feature.sendAction(BuyCryptoAction.SetVisible(false)) }
    }

    val setup = currentSetup
    val pickMethod = onPickMethod.takeIf { setup?.canPickMethod == true }

    val context = LocalContext.current
    val errorText = stringResource(Localization.unknown_error)
    val offlineText = stringResource(Localization.no_internet_connection)
    val warningColor = UIKit.colorScheme.accent.orange.toArgb()
    val toastColor = UIKit.colorScheme.background.contentTint.toArgb()
    LaunchedEffect(feature) {
        feature.events.collect { event ->
            when (event) {
                is BuyCryptoEvent.OpenUrl -> onOpenUrl(event.url)
                is BuyCryptoEvent.OpenMoonPay -> onOpenMoonPay(event.data)
                is BuyCryptoEvent.PickMethod -> onPickMethod()
                is BuyCryptoEvent.Failed -> context.navigation?.toast(
                    message = errorText,
                    loading = false,
                    color = toastColor,
                )
                is BuyCryptoEvent.Offline -> context.navigation?.toast(
                    message = offlineText,
                    loading = false,
                    color = toastColor,
                    iconRes = UIKitIcon.ic_exclamationmark_triangle_16,
                    iconTint = warningColor,
                )
            }
        }
    }

    BuyCryptoContent(
        loading = loading,
        failed = failed,
        nothingToBuy = nothingToBuy,
        fiat = fiat,
        fiatFlag = fiatFlag,
        amount = amount,
        amountField = amountField,
        receive = receive,
        quoting = quoting,
        rate = rate,
        limit = limit,
        rateTimer = rateTimer,
        payment = payment,
        quickAmounts = quickAmounts,
        canContinue = canContinue,
        continuing = continuing,
        payButton = payButton,
        disclosures = disclosures,
        priceDetails = priceDetails,
        priceDetailsOpenable = priceDetailsOpenable,
        payButtonFrame = { id, accessible, modifier -> MoonPayInlineFrame(frames, id, accessible, modifier) },
        onClose = onClose,
        onAmount = { feature.sendAction(BuyCryptoAction.SetAmount(it)) },
        onToggleRate = { feature.sendAction(BuyCryptoAction.ToggleRateDirection) },
        onRetryRate = { feature.sendAction(BuyCryptoAction.RetryRate) },
        onContinue = { feature.sendAction(BuyCryptoAction.Continue) },
        onReload = { feature.sendAction(BuyCryptoAction.Reload) },
        onPickCurrency = if (setup != null) {
            { onPickCurrency(setup.fiat) }
        } else {
            null
        },
        onPickAsset = if (setup != null) {
            { onPickAsset(setup.fiat) }
        } else {
            null
        },
        showKeyboard = showKeyboard && onTop,
        onShowKeyboardChange = onShowKeyboardChange,
        onPickMethod = pickMethod,
        onCryptoDeposit = onCryptoDeposit,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuyCryptoContent(
    loading: Boolean,
    failed: Boolean,
    nothingToBuy: String?,
    fiat: String?,
    fiatFlag: String?,
    amount: BuyCryptoAmount,
    amountField: BuyCryptoAmountField?,
    receive: BuyCryptoReceive?,
    quoting: Boolean,
    rate: BuyCryptoRate?,
    limit: BuyCryptoLimit?,
    rateTimer: Float?,
    payment: BuyCryptoPaymentRow?,
    quickAmounts: BuyCryptoQuickAmounts?,
    canContinue: Boolean,
    continuing: Boolean,
    onClose: () -> Unit,
    onAmount: (String) -> Unit,
    onToggleRate: () -> Unit,
    onRetryRate: () -> Unit,
    onContinue: () -> Unit,
    onReload: () -> Unit,
    onPickCurrency: (() -> Unit)? = null,
    onPickAsset: (() -> Unit)? = null,
    onPickMethod: (() -> Unit)? = null,
    onCryptoDeposit: () -> Unit,
    showKeyboard: Boolean = false,
    onShowKeyboardChange: (Boolean) -> Unit = {},
    payButton: BuyCryptoPayButton? = null,
    payButtonFrame: @Composable (externalTransactionId: String, accessible: Boolean, modifier: Modifier) -> Unit = { _, _, _ -> },
    disclosures: List<MoonPayDisclosureText> = emptyList(),
    priceDetails: BuyCryptoPriceDetails? = null,
    priceDetailsOpenable: Boolean = true,
) {
    val imeVisible = WindowInsets.isImeVisible
    val scrollState = rememberScrollState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var showsPriceDetails by rememberSaveable { mutableStateOf(false) }
    if (showsPriceDetails) {
        BuyCryptoPriceDetailsSheet(
            details = priceDetails?.takeIf { priceDetailsOpenable },
            timer = rateTimer,
            onToggleRate = onToggleRate,
            onClose = { showsPriceDetails = false },
        )
    }
    // MoonPay's texts sit under the keyboard while it is up. As on iOS, the first tap on the action, the Google Pay
    // button included, only lowers it, so they are seen before the payment; the next one pays. The stand-in stays for as
    // long as the keyboard shows and is off whenever the action is, so a refreshing quote dims it in place instead of
    // swapping in a disabled Google Pay button. Nothing is remembered: the keyboard raised again asks for the extra tap
    // again.
    val actionEnabled = if (payButton != null) {
        payButton.enabled
    } else {
        canContinue && !continuing
    }
    val revealsDisclosures = imeVisible && disclosures.isNotEmpty() && limit == null
    val revealDisclosures: () -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
    }
    // The keyboard is down: bring the texts it hid into view. Read once it is all the way down, not every frame of it.
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val keyboardDown by remember(ime, density) { derivedStateOf { ime.getBottom(density) == 0 } }
    var hadKeyboard by remember { mutableStateOf(false) }
    val hasDisclosures by rememberUpdatedState(disclosures.isNotEmpty())
    LaunchedEffect(keyboardDown) {
        if (!keyboardDown) {
            hadKeyboard = true
        } else if (hadKeyboard) {
            hadKeyboard = false
            if (hasDisclosures) {
                // The content area has its new height laid out a frame later.
                withFrameNanos {}
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
    }
    // A shut field never shows the keyboard, so a pick over it does not overwrite the flag with its hidden one.
    val keepKeyboard = {
        if (amountField?.editable == true) {
            onShowKeyboardChange(imeVisible)
        }
    }
    val pickCurrency = if (onPickCurrency != null) {
        {
            keepKeyboard()
            onPickCurrency()
        }
    } else {
        null
    }
    val pickAsset = if (onPickAsset != null) {
        {
            keepKeyboard()
            onPickAsset()
        }
    } else {
        null
    }
    val pickMethod = if (onPickMethod != null) {
        {
            onShowKeyboardChange(imeVisible)
            onPickMethod()
        }
    } else {
        null
    }
    MoonScaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        topBar = {
            MoonTopAppBar(
                title = { MoonTopAppBarTitle(text = stringResource(Localization.buy_crypto_title)) },
                onTitleClick = pickCurrency,
                subtitle = if (fiat != null) {
                    { FiatSubtitle(text = stringResource(Localization.buy_crypto_pay_in, fiat), fiat = fiat, flagUri = fiatFlag) }
                } else {
                    null
                },
                actionIconRes = UIKitIcon.ic_close_16,
                onActionClick = onClose,
                ignoreSystemOffset = true,
                showDivider = false,
                backgroundColor = Color.Transparent,
            )
        },
    ) {
        if (loading) {
            BuyCryptoSkeleton()
            return@MoonScaffold
        }
        if (failed) {
            MoonEmptyScreen(
                modifier = Modifier.weight(1f),
                text = stringResource(Localization.buy_crypto_load_failed),
                type = MoonEmptyScreenType.Error,
                buttonText = stringResource(Localization.retry),
                onButtonClick = onReload,
                minHeight = null,
            )
            return@MoonScaffold
        }
        if (nothingToBuy != null) {
            NothingToBuy(
                fiatName = nothingToBuy,
                amountCell = {
                    if (amountField != null) {
                        AmountCell(
                            onPickAsset = pickAsset,
                            showKeyboard = false,
                            onKeyboardShown = {},
                            amount = amount,
                            field = amountField,
                            receive = receive,
                            quoting = quoting,
                            onAmount = onAmount,
                        )
                    }
                },
                onChangeCurrency = pickCurrency,
                onCryptoDeposit = onCryptoDeposit,
            )
            return@MoonScaffold
        }
        val background = UIKit.colorScheme.background.page
        val fadeColors = remember(background) { listOf(background.copy(alpha = 0f), background) }
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                if (amountField != null) {
                    AmountCell(
                        onPickAsset = pickAsset,
                        showKeyboard = showKeyboard,
                        onKeyboardShown = { onShowKeyboardChange(false) },
                        amount = amount,
                        field = amountField,
                        receive = receive,
                        quoting = quoting,
                        onAmount = onAmount,
                    )
                }
                if (fiat != null) {
                    RateRow(
                        rate = rate,
                        timer = rateTimer,
                        onToggle = onToggleRate,
                        onRetry = onRetryRate,
                        // The keyboard stays down after the sheet: the user taps the amount to change it.
                        onPriceDetails = if (priceDetails != null) {
                            {
                                if (priceDetailsOpenable) {
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                    showsPriceDetails = true
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
                if (payment != null) {
                    Spacer(modifier = Modifier.height(PAYMENT_TOP_GAP))
                }
                when (payment) {
                    is BuyCryptoPaymentRow.Method -> PaymentCell(payment.payment, loading = payment.loading, onClick = pickMethod)
                    is BuyCryptoPaymentRow.Failed -> PaymentFailedCell(onRetry = onReload)
                    null -> Unit
                }
                // In the scroll, so the amount and the method stay in view with the keyboard up.
                if (payment is BuyCryptoPaymentRow.Method && disclosures.isNotEmpty()) {
                    PaymentDisclosures(disclosures)
                }
                Spacer(modifier = Modifier.height(BOTTOM_FADE_HEIGHT))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(BOTTOM_FADE_HEIGHT)
                    .background(Brush.verticalGradient(fadeColors)),
            )
        }
        Column {
            if (quickAmounts != null) {
                QuickAmountsRow(
                    quickAmounts = quickAmounts,
                    enabled = amountField?.editable == true,
                    onAmount = onAmount,
                )
            }
            val buttonText = when (limit) {
                is BuyCryptoLimit.BelowMin -> stringResource(Localization.buy_crypto_min_amount, limit.amount)
                is BuyCryptoLimit.AboveMax -> stringResource(Localization.buy_crypto_max_amount, limit.amount)
                null -> stringResource(Localization.continue_action)
            }
            // Out of limits the frame stays laid out under the limit's button: out of the layout its WebView is gone.
            if (payButton != null) {
                Box(contentAlignment = Alignment.Center) {
                    // Under the Continue that lowers the keyboard the frame stays laid out and live, so it does not load
                    // again once the keyboard is down.
                    PayButton(
                        payButton,
                        payButtonFrame,
                        hidden = limit != null || revealsDisclosures,
                        onTouch = { onShowKeyboardChange(imeVisible) },
                    )
                    if (limit != null) {
                        MoonBottomButtonCell(
                            text = buttonText,
                            colors = MoonButtonCellDefaults.ButtonColorsSecondary,
                            enabled = false,
                            onClick = {},
                        )
                    } else if (revealsDisclosures) {
                        MoonBottomButtonCell(
                            text = buttonText,
                            enabled = actionEnabled,
                            loading = continuing,
                            onClick = revealDisclosures,
                        )
                    }
                }
                return@Column
            }
            MoonBottomButtonCell(
                text = buttonText,
                colors = if (limit != null) {
                    MoonButtonCellDefaults.ButtonColorsSecondary
                } else {
                    MoonButtonCellDefaults.ButtonColorsPrimary
                },
                enabled = canContinue,
                loading = continuing,
                onClick = {
                    if (revealsDisclosures) {
                        revealDisclosures()
                    } else {
                        onShowKeyboardChange(imeVisible)
                        onContinue()
                    }
                },
            )
        }
    }
}

// The design's stub: no animation, the text centred under the amount, its own two buttons in place of Continue.
@Composable
private fun ColumnScope.NothingToBuy(
    fiatName: String,
    amountCell: @Composable () -> Unit,
    onChangeCurrency: (() -> Unit)?,
    onCryptoDeposit: () -> Unit,
) {
    Column(modifier = Modifier.weight(1f)) {
        amountCell()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = NOTHING_TO_BUY_TEXT_INSET, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(Localization.buy_crypto_unavailable_title),
                    style = UIKit.typography.h3,
                    color = UIKit.colorScheme.text.primary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(Localization.buy_crypto_unavailable_subtitle, fiatName),
                    style = UIKit.typography.body1,
                    color = UIKit.colorScheme.text.secondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    MoonBottomButtonCell(
        text = stringResource(Localization.buy_crypto_change_currency),
        enabled = onChangeCurrency != null,
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp),
        onClick = { onChangeCurrency?.invoke() },
    )
    MoonBottomButtonCell(
        text = stringResource(Localization.buy_crypto_crypto_deposit),
        colors = MoonButtonCellDefaults.ButtonColorsSecondary,
        onClick = onCryptoDeposit,
    )
}

// MoonPay's legal texts in full, as its go-live guide asks: verbatim, never cut, their links open in the browser.
@Composable
private fun PaymentDisclosures(disclosures: List<MoonPayDisclosureText>) {
    val color = UIKit.colorScheme.text.secondary
    val linkStyle = TextLinkStyles(SpanStyle(color = UIKit.colorScheme.text.accent, textDecoration = TextDecoration.None))
    val bodyStyle = UIKit.typography.body3
    val style = remember(bodyStyle) { bodyStyle.copy(lineHeight = DISCLOSURE_LINE_HEIGHT) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = DISCLOSURE_SPACING, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(DISCLOSURE_SPACING),
    ) {
        disclosures.forEach { disclosure ->
            val text = remember(disclosure, linkStyle) {
                buildAnnotatedString {
                    append(disclosure.text)
                    disclosure.links.forEach { link ->
                        addLink(LinkAnnotation.Url(link.url, linkStyle), link.start, link.end)
                    }
                }
            }
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = text,
                style = style,
                color = color,
                textAlign = TextAlign.Start,
            )
        }
    }
}

private val DISCLOSURE_SPACING = 12.dp
private val DISCLOSURE_LINE_HEIGHT = 16.sp

// MoonPay's button frame, at the place and size of Continue less 2 dp as in the design; a shimmer holds the place until
// the frame draws it. The frame stays laid out while hidden: a WebView out of the layout does not load.
@Composable
private fun PayButton(
    button: BuyCryptoPayButton,
    frame: @Composable (externalTransactionId: String, accessible: Boolean, modifier: Modifier) -> Unit,
    hidden: Boolean,
    // A touch on the button, as Continue's tap: the screen remembers the keyboard for its return from the sheet.
    onTouch: () -> Unit,
) {
    val phase = rememberShimmerPhase()
    val touch by rememberUpdatedState(onTouch)
    Box(
        modifier = Modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) {
                            touch()
                        }
                    }
                }
            }
            .fillMaxWidth()
            // Continue's 88 dp: the row does not jump when the method changes.
            .padding(horizontal = 16.dp, vertical = 17.dp)
            .height(PAY_BUTTON_HEIGHT)
            .clip(RoundedCornerShape(PAY_BUTTON_RADIUS)),
    ) {
        if (!button.ready && !hidden) {
            SkeletonPlate(
                Modifier.fillMaxSize(),
                phase,
                UIKit.colorScheme.background.contentTint,
                UIKit.colorScheme.background.contentAttention,
            )
        }
        frame(
            button.externalTransactionId,
            button.enabled && !hidden,
            Modifier
                .fillMaxSize()
                .alpha(
                    when {
                        // Not 0: a WebView not drawn at all may not run the page to its `ready`, as headless frames do.
                        hidden || !button.ready -> HIDDEN_PAY_BUTTON_ALPHA
                        button.enabled -> 1f
                        else -> DISABLED_PAY_BUTTON_ALPHA
                    },
                ),
        )
        if (!button.enabled || hidden) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        }
                    },
            )
        }
    }
}

private val PAY_BUTTON_HEIGHT = 54.dp
private val PAY_BUTTON_RADIUS = 26.dp
private const val DISABLED_PAY_BUTTON_ALPHA = 0.48f
private const val HIDDEN_PAY_BUTTON_ALPHA = 0.01f

@Composable
private fun ColumnScope.BuyCryptoSkeleton() {
    val phase = rememberShimmerPhase()
    val plate = UIKit.colorScheme.background.contentTint
    val highlight = UIKit.colorScheme.background.contentAttention
    Column(modifier = Modifier.weight(1f)) {
        MoonBundleCell {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AMOUNT_CELL_HEIGHT),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            ) {
                SkeletonPlate(Modifier.size(width = 202.dp, height = 44.dp), phase, plate, highlight)
                SkeletonPlate(Modifier.size(width = 140.dp, height = 32.dp), phase, plate, highlight)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RATE_ROW_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            Spacer(modifier = Modifier.size(width = 150.dp, height = 16.dp).shimmer(phase, cornerRadius = 8.dp))
        }
        Spacer(modifier = Modifier.height(PAYMENT_TOP_GAP))
        MoonBundleCell {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonPlate(Modifier.size(ICON_SIZE), phase, plate, highlight)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonPlate(Modifier.size(width = 72.dp, height = 12.dp), phase, plate, highlight)
                    SkeletonPlate(Modifier.size(width = 64.dp, height = 12.dp), phase, plate, highlight)
                }
            }
        }
    }
    Row(
        modifier = Modifier.padding(horizontal = QUICK_AMOUNTS_PADDING),
        horizontalArrangement = Arrangement.spacedBy(QUICK_AMOUNTS_SPACING),
    ) {
        repeat(SKELETON_QUICK_AMOUNTS) {
            SkeletonPlate(Modifier.weight(1f).height(QUICK_AMOUNT_HEIGHT), phase, plate, highlight)
        }
    }
    MoonBottomButtonCell(text = stringResource(Localization.continue_action), enabled = false, onClick = {})
}

@Composable
private fun SkeletonPlate(modifier: Modifier, phase: State<Float>, plate: Color, highlight: Color) {
    Spacer(
        modifier = modifier.shimmer(
            phase = phase,
            cornerRadius = 999.dp,
            backgroundFill = plate,
            highlightColor = highlight,
        ),
    )
}

@Composable
private fun AmountCell(
    amount: BuyCryptoAmount,
    field: BuyCryptoAmountField,
    receive: BuyCryptoReceive?,
    quoting: Boolean,
    onAmount: (String) -> Unit,
    onPickAsset: (() -> Unit)?,
    showKeyboard: Boolean,
    onKeyboardShown: () -> Unit,
) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(amount.text, TextRange(amount.text.length)))
    }
    // The view model trims or puts in what it decides; the field follows each answer and keeps the cursor otherwise.
    LaunchedEffect(amount.revision) {
        if (text.text != amount.text) {
            text = TextFieldValue(amount.text, TextRange(amount.text.length))
        }
    }
    MoonBundleCell {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(AMOUNT_CELL_HEIGHT),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                val focusRequester = remember { FocusRequester() }
                val keyboard = LocalSoftwareKeyboardController.current
                val scrollState = rememberScrollState()
                val numStyle = UIKit.typography.num1
                val style = remember(numStyle) { numStyle.copy(fontSize = AMOUNT_FONT_SIZE) }
                val errorColor = UIKit.colorScheme.accent.red
                val grouping = remember(field.grouping, field.outOfLimits, errorColor) {
                    GroupedAmountTransformation(field.grouping, errorColor.takeIf { field.outOfLimits })
                }
                MoonEditText(
                    paddingValues = PaddingValues(),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .weight(1f, fill = false)
                        .amountFieldScroll(scrollState),
                    value = text,
                    onValueChange = {
                        text = it
                        onAmount(it.text)
                    },
                    enabled = field.editable,
                    textStyle = style,
                    visualTransformation = grouping,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text(text = "0", style = style, color = UIKit.colorScheme.text.tertiary) },
                )
                Text(
                    modifier = Modifier.padding(bottom = 4.dp),
                    text = field.fiat,
                    style = UIKit.typography.num2,
                    color = UIKit.colorScheme.text.secondary,
                )
                LaunchedEffect(field.editable, showKeyboard) {
                    if (!field.editable) {
                        keyboard?.hide()
                    } else if (showKeyboard) {
                        // A screen only passed through on the way to another one neither shows the keyboard nor
                        // spends the flag kept for its return.
                        delay(KEYBOARD_SETTLE_DELAY)
                        focusRequester.requestFocus()
                        onKeyboardShown()
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (receive != null) {
                ReceiveChip(receive, quoting, onClick = onPickAsset)
            }
        }
    }
}

@Composable
private fun ReceiveChip(receive: BuyCryptoReceive, quoting: Boolean, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .run {
                if (!quoting) {
                    border(width = 1.5.dp, color = UIKit.colorScheme.background.contentTint, shape = CircleShape)
                } else {
                    this
                }
            }
            .run {
                if (quoting) {
                    val phase by rememberShimmerPhase()
                    shimmer(
                        phase = phase,
                        backgroundFill = UIKit.colorScheme.background.contentTint,
                        highlightColor = UIKit.colorScheme.background.contentAttention,
                    )
                } else {
                    this
                }
            }
            .height(RECEIVE_CHIP_HEIGHT)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (receive.asset != null) {
            MoonCutBadgedBox(
                badge = receive.assetId?.let { id -> remember(id) { parseAsset(id) } }?.tokenChainImageUrl()?.let { url ->
                    { MoonItemImage(image = url, size = RECEIVE_NETWORK_BADGE_SIZE) }
                },
                direction = BadgeDirection.EndBottom,
                cutPadding = 1.dp,
            ) {
                MoonItemImage(image = receive.iconUrl, size = RECEIVE_ICON_SIZE)
            }
            Spacer(modifier = Modifier.width(6.dp))
            if (receive.amount != null) {
                Text(
                    text = receive.amount,
                    style = UIKit.typography.body1,
                    color = if (receive.muted) {
                        UIKit.colorScheme.text.secondary
                    } else {
                        UIKit.colorScheme.text.primary
                    },
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(text = receive.asset, style = UIKit.typography.body1, color = UIKit.colorScheme.text.secondary)
        } else {
            Text(text = stringResource(Localization.choose_asset), style = UIKit.typography.body1, color = UIKit.colorScheme.text.secondary)
        }
        if (onClick != null) {
            Spacer(modifier = Modifier.width(4.dp))
            MoonItemIcon(painter = painterResource(UIKitIcon.ic_switch_16), color = UIKit.colorScheme.icon.secondary)
        }
    }
}

@Composable
private fun RateRow(
    rate: BuyCryptoRate?,
    timer: Float?,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
    onPriceDetails: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RATE_ROW_HEIGHT),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (rate) {
            is BuyCryptoRate.Quoted -> {
                if (rate.refreshing) {
                    RateSpinner()
                } else {
                    RateTimer(timer)
                }
                RateToggle(text = stringResource(Localization.buy_crypto_rate, rate.from, rate.to), onToggle = onToggle)
                if (onPriceDetails != null) {
                    // Weighted, so it gives way to the rate on a narrow screen.
                    PriceDetailsLink(onClick = onPriceDetails, modifier = Modifier.weight(1f, fill = false))
                }
            }
            is BuyCryptoRate.Loading -> {
                RateSpinner()
                Text(
                    modifier = Modifier.padding(start = 6.dp),
                    text = stringResource(Localization.buy_crypto_rate_loading),
                    style = UIKit.typography.body2,
                    color = UIKit.colorScheme.text.secondary,
                    maxLines = 1,
                )
            }
            is BuyCryptoRate.Failed -> {
                Text(
                    text = stringResource(Localization.buy_crypto_rate_failed),
                    style = UIKit.typography.body2,
                    color = UIKit.colorScheme.text.secondary,
                    maxLines = 1,
                )
                Text(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 4.dp),
                    text = stringResource(Localization.retry),
                    style = UIKit.typography.body2,
                    color = UIKit.colorScheme.accent.blue,
                    maxLines = 1,
                )
            }
            null -> Unit
        }
    }
}

@Composable
private fun PriceDetailsLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "·",
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.secondary,
            maxLines = 1,
        )
        Text(
            text = stringResource(Localization.buy_crypto_incl_fees),
            style = UIKit.typography.body2.copy(textDecoration = TextDecoration.Underline),
            color = UIKit.colorScheme.text.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            painter = painterResource(UIKitIcon.ic_information_circle_16),
            contentDescription = null,
            tint = UIKit.colorScheme.icon.tertiary,
        )
    }
}

@Composable
internal fun RateSpinner() {
    CircularProgressIndicator(
        modifier = Modifier.size(16.dp),
        strokeWidth = 2.dp,
        trackColor = UIKit.colorScheme.icon.secondary,
        color = UIKit.colorScheme.icon.primary,
    )
}

@Composable
internal fun RateTimer(timer: Float?) {
    // The timer ticks once a second; the ring runs smoothly between ticks and refills at once.
    val progress = remember { Animatable(1f) }
    LaunchedEffect(timer) {
        val target = timer ?: 1f
        if (target >= progress.value) {
            progress.snapTo(target)
        } else {
            progress.animateTo(target, tween(durationMillis = TIMER_TICK_MILLIS, easing = LinearEasing))
        }
    }
    CircularProgressIndicator(
        modifier = Modifier.size(16.dp),
        strokeWidth = 2.dp,
        progress = { progress.value },
        trackColor = UIKit.colorScheme.icon.secondary,
        color = UIKit.colorScheme.icon.primary,
        gapSize = 0.dp,
    )
}

@Composable
private fun RateToggle(text: String, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(start = 6.dp)
            .minimumInteractiveComponentSize()
            .fillMaxHeight()
            .clickable(onClick = onToggle),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.secondary,
            maxLines = 1,
        )
        Icon(
            painter = painterResource(UIKitIcon.ic_swap_horizontal_16),
            contentDescription = null,
            tint = UIKit.colorScheme.icon.tertiary,
        )
    }
}

@Composable
private fun QuickAmountsRow(quickAmounts: BuyCryptoQuickAmounts, enabled: Boolean, onAmount: (String) -> Unit) {
    val max = quickAmounts.max
    val showsMax = max != null || quickAmounts.loading
    val count = quickAmounts.items.size + if (showsMax) {
        1
    } else {
        0
    }
    // Equal shares of the row; a title too wide for its share (a costly currency, a large font) widens
    // its button and the row scrolls.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val minWidth = (maxWidth - QUICK_AMOUNTS_PADDING * 2 - QUICK_AMOUNTS_SPACING * (count - 1)) / count
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = QUICK_AMOUNTS_PADDING),
            horizontalArrangement = Arrangement.spacedBy(QUICK_AMOUNTS_SPACING),
        ) {
            quickAmounts.items.forEach { item ->
                QuickAmountButton(
                    text = item.title,
                    minWidth = minWidth,
                    loading = quickAmounts.loading,
                    enabled = enabled,
                    onClick = { onAmount(item.amount) },
                )
            }
            if (showsMax) {
                QuickAmountButton(
                    text = stringResource(Localization.max).uppercase(),
                    minWidth = minWidth,
                    loading = quickAmounts.loading,
                    enabled = enabled && max != null,
                    onClick = { max?.let(onAmount) },
                )
            }
        }
    }
}

@Composable
private fun QuickAmountButton(
    text: String,
    minWidth: Dp,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .widthIn(min = minWidth)
            .height(QUICK_AMOUNT_HEIGHT)
            .clip(CircleShape)
            .run {
                if (loading) {
                    val phase by rememberShimmerPhase()
                    shimmer(phase)
                } else {
                    background(UIKit.colorScheme.buttonTertiary.primaryBackground).clickable(enabled = enabled, onClick = onClick)
                }
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!loading) {
            Text(
                text = text,
                style = UIKit.typography.label2,
                color = if (enabled) {
                    UIKit.colorScheme.buttonTertiary.primaryForeground
                } else {
                    UIKit.colorScheme.text.tertiary
                },
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PaymentCell(payment: BuyCryptoPayment, loading: Boolean, onClick: (() -> Unit)?) {
    val badge = payment.badge
    MoonBundleCell {
        TextCell(
            modifier = if (loading) {
                val phase by rememberShimmerPhase()
                Modifier.shimmer(phase)
            } else {
                Modifier
            },
            title = payment.title,
            subtitle = payment.subtitle,
            onClick = onClick,
            content = if (onClick != null) {
                { MoonItemIcon(painterResource(UIKitIcon.ic_switch_16)) }
            } else {
                null
            },
            minHeight = 76.dp,
            image = {
                MoonCutBadgedBox(
                    badge = when (badge) {
                        is BuyCryptoBadge.MoonPay -> {
                            {
                                Image(
                                    painter = painterResource(UIKitIcon.ic_provider_moonpay),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(BADGE_SIZE)
                                        .clip(CircleShape),
                                )
                            }
                        }
                        is BuyCryptoBadge.Image -> {
                            { MoonItemImage(image = badge.url, size = BADGE_SIZE) }
                        }
                        null -> null
                    },
                    direction = BadgeDirection.EndBottom,
                ) {
                    val card = payment.card
                    if (card != null) {
                        // As in the design and on iOS, the badge sits on the card's square slot, not on the card.
                        Box(modifier = Modifier.size(ICON_SIZE), contentAlignment = Alignment.Center) {
                            BuyCryptoCardIcon(card)
                        }
                    } else {
                        MoonItemImage(image = payment.iconUrl, size = ICON_SIZE)
                    }
                }
            },
        )
    }
}

@Composable
private fun PaymentFailedCell(onRetry: () -> Unit) {
    MoonBundleCell {
        TextCell(
            title = stringResource(Localization.buy_crypto_load_failed),
            titleColor = UIKit.colorScheme.text.secondary,
            content = {
                Text(text = stringResource(Localization.retry), style = UIKit.typography.label1, color = UIKit.colorScheme.accent.blue)
            },
            onClick = onRetry,
            minHeight = 76.dp,
        )
    }
}

private val AMOUNT_CELL_HEIGHT = 180.dp
private val RATE_ROW_HEIGHT = 48.dp
private val PAYMENT_TOP_GAP = 8.dp
private const val SKELETON_QUICK_AMOUNTS = 4
private val QUICK_AMOUNT_HEIGHT = 36.dp
private val BOTTOM_FADE_HEIGHT = 16.dp
private val NOTHING_TO_BUY_TEXT_INSET = 56.dp
private val QUICK_AMOUNTS_PADDING = 16.dp
private val QUICK_AMOUNTS_SPACING = 8.dp
private val PREVIEW_QUICK_AMOUNTS = BuyCryptoQuickAmounts(
    items = listOf(
        BuyCryptoQuickAmount("$50", "50"),
        BuyCryptoQuickAmount("$100", "100"),
        BuyCryptoQuickAmount("$500", "500"),
    ),
    max = "29000",
    loading = false,
)
private const val TIMER_TICK_MILLIS = 1000
private val ICON_SIZE = 44.dp
private val BADGE_SIZE = 20.dp

@Preview
@Composable
private fun BuyCryptoPreview() {
    ThemedPreview {
        BuyCryptoContent(
            loading = false,
            failed = false,
            nothingToBuy = null,
            fiat = "USD",
            fiatFlag = null,
            amount = BuyCryptoAmount("100", revision = 0),
            amountField = BuyCryptoAmountField(fiat = "USD", editable = true, outOfLimits = false, grouping = " "),
            receive = BuyCryptoReceive(amount = "92.54", muted = false, asset = "USDT (TON)", iconUrl = null, assetId = PREVIEW_USDT_TON),
            quoting = false,
            rate = BuyCryptoRate.Quoted(from = "USD", to = "0.925 USDT (TON)", refreshing = false),
            limit = null,
            rateTimer = 0.6f,
            payment = BuyCryptoPaymentRow.Method(
                BuyCryptoPayment(title = "Google Pay", subtitle = "Instant", iconUrl = null, badge = null, card = null),
                loading = false,
            ),
            quickAmounts = PREVIEW_QUICK_AMOUNTS,
            canContinue = true,
            continuing = false,
            onClose = {},
            onAmount = {},
            onToggleRate = {},
            onRetryRate = {},
            onContinue = {},
            onReload = {},
            onCryptoDeposit = {},
        )
    }
}

@Preview
@Composable
private fun BuyCryptoDisclosuresPreview() {
    ThemedPreview {
        BuyCryptoContent(
            loading = false,
            failed = false,
            nothingToBuy = null,
            fiat = "USD",
            fiatFlag = null,
            amount = BuyCryptoAmount("100", revision = 0),
            amountField = BuyCryptoAmountField(fiat = "USD", editable = true, outOfLimits = false, grouping = " "),
            receive = BuyCryptoReceive(amount = "92.54", muted = false, asset = "USDT (TON)", iconUrl = null, assetId = PREVIEW_USDT_TON),
            quoting = false,
            rate = BuyCryptoRate.Quoted(from = "USD", to = "0.925 USDT (TON)", refreshing = false),
            limit = null,
            rateTimer = 0.6f,
            payment = BuyCryptoPaymentRow.Method(
                BuyCryptoPayment(title = "Google Pay", subtitle = "Instant", iconUrl = null, badge = null, card = null),
                loading = false,
            ),
            quickAmounts = PREVIEW_QUICK_AMOUNTS,
            canContinue = true,
            continuing = false,
            onClose = {},
            onAmount = {},
            onToggleRate = {},
            onRetryRate = {},
            onContinue = {},
            onReload = {},
            onCryptoDeposit = {},
            disclosures = MoonPayPaymentDisclosures.texts(
                listOf(MoonPayBuyQuote.PaymentDisclosure(id = "eea-crypto-asset-risk", version = "1")),
            ),
        )
    }
}

@Preview
@Composable
private fun BuyCryptoRateLoadingPreview() {
    ThemedPreview {
        BuyCryptoContent(
            loading = false,
            failed = false,
            nothingToBuy = null,
            fiat = "USD",
            fiatFlag = null,
            amount = BuyCryptoAmount("100", revision = 0),
            amountField = BuyCryptoAmountField(fiat = "USD", editable = true, outOfLimits = false, grouping = " "),
            receive = BuyCryptoReceive(amount = null, muted = false, asset = "USDT (TON)", iconUrl = null, assetId = null),
            quoting = true,
            rate = BuyCryptoRate.Loading,
            limit = null,
            rateTimer = null,
            payment = BuyCryptoPaymentRow.Method(
                BuyCryptoPayment(title = "Google Pay", subtitle = "Instant", iconUrl = null, badge = null, card = null),
                loading = false,
            ),
            quickAmounts = PREVIEW_QUICK_AMOUNTS,
            canContinue = false,
            continuing = false,
            onClose = {},
            onAmount = {},
            onToggleRate = {},
            onRetryRate = {},
            onContinue = {},
            onReload = {},
            onCryptoDeposit = {},
        )
    }
}

@Preview
@Composable
private fun BuyCryptoRateFailedPreview() {
    ThemedPreview {
        BuyCryptoContent(
            loading = false,
            failed = false,
            nothingToBuy = null,
            fiat = "USD",
            fiatFlag = null,
            amount = BuyCryptoAmount("100", revision = 0),
            amountField = BuyCryptoAmountField(fiat = "USD", editable = true, outOfLimits = false, grouping = " "),
            receive = BuyCryptoReceive(amount = null, muted = false, asset = "USDT (TON)", iconUrl = null, assetId = null),
            quoting = false,
            rate = BuyCryptoRate.Failed,
            limit = null,
            rateTimer = null,
            payment = BuyCryptoPaymentRow.Method(
                BuyCryptoPayment(title = "Google Pay", subtitle = "Instant", iconUrl = null, badge = null, card = null),
                loading = false,
            ),
            quickAmounts = PREVIEW_QUICK_AMOUNTS,
            canContinue = false,
            continuing = false,
            onClose = {},
            onAmount = {},
            onToggleRate = {},
            onRetryRate = {},
            onContinue = {},
            onReload = {},
            onCryptoDeposit = {},
        )
    }
}

@Preview
@Composable
private fun BuyCryptoBelowMinPreview() {
    ThemedPreview {
        BuyCryptoContent(
            loading = false,
            failed = false,
            nothingToBuy = null,
            fiat = "USD",
            fiatFlag = null,
            amount = BuyCryptoAmount("10", revision = 0),
            amountField = BuyCryptoAmountField(fiat = "USD", editable = true, outOfLimits = true, grouping = " "),
            receive = BuyCryptoReceive(amount = "0", muted = true, asset = "USDT (TON)", iconUrl = null, assetId = null),
            quoting = false,
            rate = null,
            limit = BuyCryptoLimit.BelowMin("$25"),
            rateTimer = null,
            payment = BuyCryptoPaymentRow.Method(
                BuyCryptoPayment(title = "Google Pay", subtitle = "Instant", iconUrl = null, badge = null, card = null),
                loading = false,
            ),
            quickAmounts = PREVIEW_QUICK_AMOUNTS,
            canContinue = false,
            continuing = false,
            onClose = {},
            onAmount = {},
            onToggleRate = {},
            onRetryRate = {},
            onContinue = {},
            onReload = {},
            onCryptoDeposit = {},
        )
    }
}

@Preview
@Composable
private fun BuyCryptoNothingToBuyPreview() {
    ThemedPreview {
        BuyCryptoContent(
            loading = false,
            failed = false,
            nothingToBuy = "Chilean Peso (CLP)",
            fiat = "CLP",
            fiatFlag = null,
            amount = BuyCryptoAmount("", revision = 0),
            amountField = BuyCryptoAmountField(fiat = "CLP", editable = false, outOfLimits = false, grouping = " "),
            receive = null,
            quoting = false,
            rate = null,
            limit = null,
            rateTimer = null,
            payment = null,
            quickAmounts = null,
            canContinue = false,
            continuing = false,
            onClose = {},
            onAmount = {},
            onToggleRate = {},
            onRetryRate = {},
            onContinue = {},
            onReload = {},
            onCryptoDeposit = {},
        )
    }
}

@Preview
@Composable
private fun BuyCryptoPickFailedPreview() {
    ThemedPreview {
        BuyCryptoContent(
            loading = false,
            failed = false,
            nothingToBuy = null,
            fiat = "EUR",
            fiatFlag = null,
            amount = BuyCryptoAmount("", revision = 0),
            amountField = BuyCryptoAmountField(fiat = "EUR", editable = false, outOfLimits = false, grouping = " "),
            receive = BuyCryptoReceive(amount = null, muted = false, asset = "USDT (TON)", iconUrl = null, assetId = null),
            quoting = false,
            rate = null,
            limit = null,
            rateTimer = null,
            payment = BuyCryptoPaymentRow.Failed,
            quickAmounts = null,
            canContinue = false,
            continuing = false,
            onClose = {},
            onAmount = {},
            onToggleRate = {},
            onRetryRate = {},
            onContinue = {},
            onReload = {},
            onCryptoDeposit = {},
        )
    }
}

@Preview
@Composable
private fun BuyCryptoPickLoadingPreview() {
    ThemedPreview {
        BuyCryptoContent(
            loading = false,
            failed = false,
            nothingToBuy = null,
            fiat = "USD",
            fiatFlag = null,
            amount = BuyCryptoAmount("100", revision = 0),
            amountField = BuyCryptoAmountField(fiat = "USD", editable = false, outOfLimits = false, grouping = " "),
            receive = BuyCryptoReceive(amount = null, muted = false, asset = null, iconUrl = null, assetId = null),
            quoting = true,
            rate = null,
            limit = null,
            rateTimer = null,
            payment = BuyCryptoPaymentRow.Method(
                BuyCryptoPayment(title = "PayPal", subtitle = "Instant", iconUrl = null, badge = null, card = null),
                loading = true,
            ),
            quickAmounts = PREVIEW_QUICK_AMOUNTS.copy(loading = true),
            canContinue = false,
            continuing = false,
            onClose = {},
            onAmount = {},
            onToggleRate = {},
            onRetryRate = {},
            onContinue = {},
            onReload = {},
            onCryptoDeposit = {},
        )
    }
}

@Preview
@Composable
private fun BuyCryptoFailedPreview() {
    ThemedPreview {
        BuyCryptoContent(
            loading = false,
            failed = true,
            nothingToBuy = null,
            fiat = null,
            fiatFlag = null,
            amount = BuyCryptoAmount("", revision = 0),
            amountField = null,
            receive = null,
            quoting = false,
            rate = null,
            limit = null,
            rateTimer = null,
            payment = null,
            quickAmounts = null,
            canContinue = false,
            continuing = false,
            onClose = {},
            onAmount = {},
            onToggleRate = {},
            onRetryRate = {},
            onContinue = {},
            onReload = {},
            onCryptoDeposit = {},
        )
    }
}

@Preview
@Composable
private fun BuyCryptoLoadingPreview() {
    ThemedPreview {
        BuyCryptoContent(
            loading = true,
            failed = false,
            nothingToBuy = null,
            fiat = null,
            fiatFlag = null,
            amount = BuyCryptoAmount("", revision = 0),
            amountField = null,
            receive = null,
            quoting = false,
            rate = null,
            limit = null,
            rateTimer = null,
            payment = null,
            quickAmounts = null,
            canContinue = false,
            continuing = false,
            onClose = {},
            onAmount = {},
            onToggleRate = {},
            onRetryRate = {},
            onContinue = {},
            onReload = {},
            onCryptoDeposit = {},
        )
    }
}

private val KEYBOARD_SETTLE_DELAY = 300.milliseconds

@Preview
@Composable
private fun PayButtonPreview() {
    ThemedPreview {
        Column {
            listOf(
                BuyCryptoPayButton("loading", ready = false, enabled = false),
                BuyCryptoPayButton("ready", ready = true, enabled = true),
                BuyCryptoPayButton("dimmed", ready = true, enabled = false),
            ).forEach { button ->
                PayButton(
                    button = button,
                    frame = { _, _, modifier -> Box(modifier.background(Color.White)) },
                    hidden = false,
                    onTouch = {},
                )
            }
        }
    }
}

private val AMOUNT_FONT_SIZE = 40.sp
private val RECEIVE_CHIP_HEIGHT = 40.dp
private val RECEIVE_ICON_SIZE = 20.dp
private val RECEIVE_NETWORK_BADGE_SIZE = 10.dp

private const val PREVIEW_USDT_TON = "ton/mainnet/jetton/0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe"
