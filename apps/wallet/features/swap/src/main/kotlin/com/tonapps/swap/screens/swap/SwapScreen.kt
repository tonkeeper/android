package com.tonapps.swap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.MysteryRaffle.MysteryRaffleSource
import com.tonapps.bus.generated.Events.SwapsNative.SwapsNativeType
import com.tonapps.bus.generated.Events.SwapsNative.SwapsNativeWalletMode
import com.tonapps.chainkit.core.chain.model.account.CoinType
import com.tonapps.chainkit.core.chain.model.num.BaseUnit
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.chainkit.core.chain.model.num.toFiatAmount
import com.tonapps.core.components.assetImageUrl
import com.tonapps.core.components.imageResourceUrl
import com.tonapps.core.components.tokenChainImageUrl
import com.tonapps.swap.screens.swap.AmountInputSide
import com.tonapps.swap.screens.swap.SwapEvent
import com.tonapps.swap.screens.swap.SwapFeature
import com.tonapps.swap.screens.swap.SwapInitState
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.RStr
import com.wallet.crypto.trustapp.common.ui.components.MoonEditText
import ui.components.moon.MoonBadgeButton
import ui.components.moon.MoonCircleIcon
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonTopAppBar
import ui.components.moon.MoonTopAppBarTitle
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.components.moon.container.MoonScaffold
import ui.components.moon.screen.MoonEmptyScreen
import ui.components.moon.screen.MoonEmptyScreenType
import ui.preview.ThemedPreview
import ui.theme.UIKit
import ui.theme.modifiers.modifyIf
import ui.theme.modifiers.rememberShimmerPhase
import ui.theme.modifiers.shimmer
import ui.theme.modifiers.simmerOn
import ui.utils.uppercased

@Composable
fun SwapScreen(
    feature: SwapFeature,
    onClose: () -> Unit,
    onSelectSendAsset: () -> Unit = {},
    onSelectReceiveAsset: () -> Unit = {},
    onContinue: (ConfirmRequest) -> Unit = {},
    onOpenRaffle: (String) -> Unit = {},
) {
    val initState by feature.initState.collectAsState()
    val sellAsset by feature.sellAsset.collectAsState()
    val buyAsset by feature.buyAsset.collectAsState()
    val sellAccount by feature.sellAccount.collectAsState()
    val buyAccount by feature.buyAccount.collectAsState()
    val amountInput by feature.amountInput.collectAsState()
    val quote by feature.quote.collectAsState()
    val isLoading by feature.isLoading.collectAsState()
    val quoteCountdown by feature.quoteCountdown.collectAsState()
    val continueEnabled by feature.continueEnabled.collectAsState()
    val sellValidatedAmount by feature.sellValidatedAmount.collectAsState()
    val buyValidatedAmount by feature.buyValidatedAmount.collectAsState()
    val insufficientFunds by feature.insufficientFunds.collectAsState()
    // TODO bring the slippage selector back to this screen
    // val slippage by feature.slippage.collectAsState()
    // val selectedSlippageBps by feature.selectedSlippageBps.collectAsState()
    val zeroFeeRaffleId by feature.zeroFeeRaffleId.collectAsState()
    val inputInFiat = amountInput.inFiat
    val raffleEvents = AnalyticsHelper.Default.events.mysteryRaffle
    LaunchedEffect(zeroFeeRaffleId != null) {
        if (zeroFeeRaffleId != null) {
            raffleEvents.raffleBannerView(MysteryRaffleSource.SwapPromo)
        }
    }

    val swapEvents = AnalyticsHelper.Default.events.swapsNative
    // Saved, so coming back from the asset picker (which disposes this entry) does not re-fire.
    var openTracked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!openTracked) {
            openTracked = true
            swapEvents.swapOpen(
                type = SwapsNativeType.Native,
                walletMode = SwapsNativeWalletMode.Multi,
            )
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(feature) {
        feature.events.collect { event ->
            when (event) {
                is SwapEvent.Continue -> {
                    keyboardController?.hide()
                    onContinue(event.request)
                }

                is SwapEvent.ShowError -> Unit // TODO surface error
            }
        }
    }

    val buyInFiat = inputInFiat && buyAccount?.rate != null
    val inputSide = amountInput.side

    val sellConverted = formatConvertedAmount(account = sellAccount, value = sellValidatedAmount, inFiat = inputInFiat)
    val buyConverted = formatConvertedAmount(account = buyAccount, value = buyValidatedAmount, inFiat = buyInFiat)
    val sellFieldAmount = when {
        inputSide == AmountInputSide.Sell -> amountInput.text
        inputInFiat -> formatFiatFieldText(account = sellAccount, value = sellValidatedAmount)
        else -> quote?.sellAmount.orEmpty()
    }
    val buyFieldAmount = when {
        inputSide == AmountInputSide.Buy -> amountInput.text
        buyInFiat -> formatFiatFieldText(account = buyAccount, value = buyValidatedAmount)
        else -> quote?.buyAmount.orEmpty()
    }
    val quoteUnavailable = !isLoading && quote != null && quote?.quote == null
    val noQuoteText = stringResource(Localization.swap_no_quote).takeIf { quoteUnavailable }
    val sellQuoteMissing = quoteUnavailable && inputSide == AmountInputSide.Buy
    val buyQuoteMissing = quoteUnavailable && inputSide == AmountInputSide.Sell

    MoonScaffold(
        modifier = Modifier
            .imePadding()
            .navigationBarsPadding(),
        topBar = {
            MoonTopAppBar(
                title = { MoonTopAppBarTitle(text = stringResource(RStr.swap)) },
                subtitle = if (zeroFeeRaffleId == null) {
                    null
                } else {
                    { ZeroFeePerkSubtitle() }
                },
                onTitleClick = zeroFeeRaffleId?.let { raffleId ->
                    {
                        raffleEvents.raffleBannerClick(MysteryRaffleSource.SwapPromo)
                        onOpenRaffle(raffleId)
                    }
                },
                actionIconRes = UIKitIcon.ic_close_16,
                onActionClick = onClose,
                showDivider = false,
                backgroundColor = Color.Transparent,
            )
        },
    ) {
        val sell = sellAsset
        val buy = buyAsset
        when {
            initState is SwapInitState.Error -> MoonEmptyScreen(
                modifier = Modifier.fillMaxSize(),
                type = MoonEmptyScreenType.Error,
                text = stringResource(Localization.something_went_wrong),
                buttonText = stringResource(Localization.retry),
                onButtonClick = feature::retry,
            )

            initState is SwapInitState.Ready && sell != null && buy != null -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SwapContainer(
                                title = stringResource(RStr.send),
                                asset = sell,
                                account = sellAccount,
                                amount = sellFieldAmount,
                                placeholder = noQuoteText.takeIf { sellQuoteMissing },
                                currencyPrefix = sellAccount.currencySymbol().takeIf { inputInFiat && !sellQuoteMissing },
                                convertedLabel = sellConverted,
                                isShimmering = isLoading && inputSide == AmountInputSide.Buy,
                                isActive = inputSide == AmountInputSide.Sell,
                                insufficientFunds = insufficientFunds,
                                onAmountChanged = feature::setSellAmount,
                                onMaxClicked = feature::setMax,
                                onAssetClicked = onSelectSendAsset,
                                onToggleCurrency = feature::toggleInputCurrency,
                            )

                            SwapContainer(
                                title = stringResource(RStr.receive),
                                asset = buy,
                                account = buyAccount,
                                amount = buyFieldAmount,
                                placeholder = noQuoteText.takeIf { buyQuoteMissing },
                                currencyPrefix = buyAccount.currencySymbol().takeIf { buyInFiat && !buyQuoteMissing },
                                convertedLabel = buyConverted,
                                isShimmering = isLoading && inputSide == AmountInputSide.Sell,
                                isActive = inputSide == AmountInputSide.Buy,
                                isReadOnly = !feature.isReceiveAmountEditable,
                                onAmountChanged = feature::setBuyAmount,
                                onAssetClicked = onSelectReceiveAsset,
                            )
                        }

                        MoonCircleIcon(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .clickable(onClick = feature::swapSides),
                            painter = painterResource(UIKitIcon.ic_swap_vertical_16),
                            size = 40.dp,
                            color = UIKit.colorScheme.buttonTertiary.primaryBackground,
                            tint = UIKit.colorScheme.buttonTertiary.primaryForeground
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        Modifier.heightIn(min = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!isLoading && quote?.quote != null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                progress = { quoteCountdown },
                                trackColor = UIKit.colorScheme.icon.secondary,
                                color = UIKit.colorScheme.icon.primary,
                                gapSize = 0.dp,
                            )

                            MoonItemSubtitle(
                                text = quote?.rateLabel ?: ""
                            )
                        } else if (quoteUnavailable) {
                            MoonItemSubtitle(
                                modifier = Modifier.padding(horizontal = 32.dp),
                                text = stringResource(Localization.swap_error_quote_not_found),
                                textAlign = TextAlign.Center,
                                maxLines = Int.MAX_VALUE,
                                color = UIKit.colorScheme.accent.red,
                            )
                        }
                    }

                    // TODO bring the slippage selector back to this screen. The quote still uses
                    // the selected value, and the confirm screen still lets the user change it.
                    /*slippage?.let { options ->
                        Spacer(Modifier.height(16.dp))

                        MoonBundleCell {
                            MoonPropertyBigCell(
                                title = {
                                    MoonPropertyTitle(
                                        title = stringResource(Localization.slippage),
                                        infoTooltip = stringResource(Localization.swap_slippage_info),
                                    )
                                },
                                content = {
                                    SlippageSelector(
                                        slippage = options,
                                        selectedBps = selectedSlippageBps,
                                        onSelect = feature::selectSlippage,
                                    )
                                },
                            )
                        }
                    }*/
                }

                MoonBottomButtonCell(
                    text = stringResource(Localization.continue_action),
                    enabled = continueEnabled,
                    onClick = {
                        swapEvents.swapClick(
                            type = SwapsNativeType.Native,
                            walletMode = SwapsNativeWalletMode.Multi,
                            assetFrom = sell.id,
                            assetTo = buy.id,
                            isMax = amountInput.isMax,
                        )
                        feature.onContinue()
                    },
                )
            }

            else -> SwapScreenShimmer()
        }
    }
}

@Composable
private fun SwapContainer(
    title: String,
    asset: AssetEntity,
    account: AccountWithDetails?,
    amount: String,
    currencyPrefix: String?,
    convertedLabel: String?,
    isShimmering: Boolean,
    isActive: Boolean,
    isReadOnly: Boolean = false,
    insufficientFunds: Boolean = false,
    placeholder: String? = null,
    onAmountChanged: (String) -> Unit,
    onMaxClicked: (() -> Unit)? = null,
    onAssetClicked: () -> Unit = {},
    onToggleCurrency: (() -> Unit)? = null,
) {
    SwapContainer(
        title = title,
        assetImageUrl = asset.assetImageUrl(),
        chainImageUrl = asset.tokenChainImageUrl(),
        symbol = asset.symbol,
        balance = remember(account?.displayBalance) {
            account?.displayBalance?.let { Formatter.formatShort(value = it) }
        },
        amount = amount,
        currencyPrefix = currencyPrefix,
        placeholder = placeholder,
        convertedLabel = convertedLabel,
        isShimmering = isShimmering,
        isActive = isActive,
        isReadOnly = isReadOnly,
        insufficientFunds = insufficientFunds,
        onAmountChanged = onAmountChanged,
        onMaxClicked = onMaxClicked,
        onAssetClicked = onAssetClicked,
        onToggleCurrency = onToggleCurrency,
    )
}

@Composable
private fun formatConvertedAmount(
    account: AccountWithDetails?,
    value: BaseUnit?,
    inFiat: Boolean,
): String? {
    val rate = account?.rate ?: return null
    val asset = account.asset
    val amount = value ?: BaseUnit(BigInteger.ZERO, asset.value.decimals)
    return remember(amount, rate, inFiat, asset) {
        if (inFiat) {
            "${Formatter.formatShort(value = amount)} ${asset.symbol}"
        } else {
            Formatter.formatFiat(value = amount, rate = rate.value, separator = " ")
        }
    }
}

@Composable
private fun formatFiatFieldText(
    account: AccountWithDetails?,
    value: BaseUnit?,
): String {
    val rate = account?.rate ?: return ""
    val amount = value ?: return ""
    return remember(amount, rate) {
        amount.toDisplayUnit().toFiatAmount(rate.value)
    }
}

private fun AccountWithDetails?.currencySymbol(): String? {
    return this?.rate?.value?.currency?.symbol
}

@Composable
private fun SwapContainer(
    assetImageUrl: String,
    chainImageUrl: String?,
    title: String,
    symbol: String,
    balance: String?,
    amount: String,
    currencyPrefix: String? = null,
    convertedLabel: String? = null,
    isShimmering: Boolean = false,
    isActive: Boolean = true,
    isReadOnly: Boolean = false,
    insufficientFunds: Boolean = false,
    placeholder: String? = null,
    onAmountChanged: (String) -> Unit,
    onMaxClicked: (() -> Unit)? = null,
    onAssetClicked: () -> Unit = {},
    onToggleCurrency: (() -> Unit)? = null,
) {
    MoonBundleCell(
        modifier = Modifier
            .modifyIf {
                if (!isActive) {
                    null
                } else {
                    border(
                        width = 1.5.dp,
                        color = UIKit.colorScheme.field.activeBorder,
                        shape = UIKit.shapes.large,
                    )
                }
            }
            .clip(UIKit.shapes.large)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MoonItemSubtitle(text = title)

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val focusRequester = remember { FocusRequester() }
                val scrollState = rememberScrollState()

                LaunchedEffect("1") {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }

                if (currencyPrefix != null) {
                    Text(
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .simmerOn(isShimmering),
                        text = currencyPrefix,
                        style = UIKit.typography.num2,
                        color = UIKit.colorScheme.text.secondary,
                    )
                }

                MoonEditText(
                    paddingValues = PaddingValues(),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .weight(1f, fill = true)
                        .horizontalScroll(scrollState)
                        .width(IntrinsicSize.Min)
                        .defaultMinSize(minWidth = 24.dp)
                        .simmerOn(isShimmering),
                    value = amount,
                    onValueChange = onAmountChanged,
                    readOnly = isReadOnly,
                    textStyle = UIKit.typography.num2,
                    keyboardActions = remember {
                        KeyboardActions(onDone = { })
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = {
                        if (placeholder != null) {
                            Text(
                                text = placeholder,
                                style = UIKit.typography.num2,
                                color = UIKit.colorScheme.text.secondary,
                            )
                        } else {
                            Text(
                                text = "0",
                                style = UIKit.typography.num2,
                                color = UIKit.colorScheme.text.tertiary
                            )
                        }
                    },
                )

                Spacer(Modifier.width(8.dp))

                MoonBadgeButton(
                    content = {
                        MoonCutBadgedBox(
                            badge = if (chainImageUrl != null) {
                                { MoonItemImage(image = chainImageUrl, size = 12.dp) }
                            } else {
                                null
                            },
                            direction = BadgeDirection.EndBottom,
                        ) {
                            MoonItemImage(
                                image = assetImageUrl,
                                size = 24.dp,
                            )
                        }

                        MoonItemTitle(text = symbol)

                        MoonItemIcon(
                            painterResource(UIKitIcon.ic_switch_16)
                        )
                    },
                    contentPadding = PaddingValues(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
                    onClick = onAssetClicked,
                )
            }

            val showConverted = !convertedLabel.isNullOrBlank()
            val showBalance = balance != null && !insufficientFunds
            Row(
                modifier = Modifier.height(
                    with(LocalDensity.current) {
                        UIKit.typography.body2.lineHeight.toDp()
                    }
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showConverted) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .modifyIf { onToggleCurrency?.let { requiredHeight(36.dp).clickable(onClick = it) } }
                                .simmerOn(isShimmering),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MoonItemSubtitle(
                                modifier = Modifier.weight(1f, fill = false),
                                text = convertedLabel,
                            )

                            if (onToggleCurrency != null) {
                                MoonItemIcon(
                                    painter = painterResource(UIKitIcon.ic_swap_vertical_16),
                                    size = 16.dp,
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                when {
                    insufficientFunds -> MoonItemSubtitle(
                        text = stringResource(Localization.insufficient_balance_title),
                        color = UIKit.colorScheme.accent.red,
                    )

                    showBalance -> MoonItemSubtitle(text = stringResource(Localization.balance_prefix, balance))
                }

                if (balance != null && onMaxClicked != null) {
                    Spacer(Modifier.width(8.dp))
                    MoonItemSubtitle(
                        text = stringResource(Localization.max).uppercased(),
                        color = UIKit.colorScheme.text.accent,
                        modifier = Modifier.clickable(onClick = onMaxClicked),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SwapScreenShimmer() {
    val phase by rememberShimmerPhase()

    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SwapContainerShimmer(
                    title = stringResource(RStr.send),
                    phase = phase,
                )

                SwapContainerShimmer(
                    title = stringResource(RStr.receive),
                    phase = phase,
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(UIKit.colorScheme.buttonTertiary.primaryBackground)
            )
        }

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier.heightIn(min = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Spacer(
                modifier = Modifier
                    .size(width = 150.dp, height = 12.dp)
                    .shimmer(phase, cornerRadius = 8.dp)
            )
        }
    }

    MoonBottomButtonCell(
        text = stringResource(Localization.continue_action),
        enabled = false,
        onClick = {},
    )
}

@Composable
private fun SwapContainerShimmer(
    title: String,
    phase: Float,
) {
    val fill = UIKit.colorScheme.background.contentTint
    val highlight = UIKit.colorScheme.background.contentAttention

    MoonBundleCell(
        modifier = Modifier
            .clip(UIKit.shapes.large)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MoonItemSubtitle(text = title)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(
                    modifier = Modifier
                        .size(width = 104.dp, height = 36.dp)
                        .shimmer(
                            phase,
                            cornerRadius = 18.dp,
                            backgroundFill = fill,
                            highlightColor = highlight,
                        )
                )

                Spacer(
                    modifier = Modifier
                        .size(width = 104.dp, height = 40.dp)
                        .shimmer(
                            phase,
                            cornerRadius = 20.dp,
                            backgroundFill = fill,
                            highlightColor = highlight,
                        )
                )
            }

            Row(
                modifier = Modifier.height(
                    with(LocalDensity.current) {
                        UIKit.typography.body2.lineHeight.toDp()
                    }
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(
                    modifier = Modifier
                        .size(width = 45.dp, height = 12.dp)
                        .shimmer(
                            phase,
                            cornerRadius = 8.dp,
                            backgroundFill = fill,
                            highlightColor = highlight,
                        )
                )

                Spacer(Modifier.weight(1f))

                Spacer(
                    modifier = Modifier
                        .size(width = 142.dp, height = 12.dp)
                        .shimmer(
                            phase,
                            cornerRadius = 8.dp,
                            backgroundFill = fill,
                            highlightColor = highlight,
                        )
                )
            }
        }
    }
}

////////////////////
// PREVIEW
////////////////////
@Preview
@Composable
private fun SwapContainerPreview() {
    val context = LocalContext.current
    val url = CoinType.Gram.imageResourceUrl(context)
    ThemedPreview {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SwapContainer(
                        title = "Send",
                        assetImageUrl = url,
                        chainImageUrl = null,
                        symbol = "TON",
                        balance = "12 322 TON",
                        amount = "12",
                        onAmountChanged = {},
                        onMaxClicked = {},
                    )

                    SwapContainer(
                        title = "Receive",
                        assetImageUrl = url,
                        chainImageUrl = null,
                        symbol = "TON",
                        balance = "5 678 TON",
                        amount = "",
                        isActive = false,
                        onAmountChanged = {},
                    )
                }

                MoonCircleIcon(
                    modifier = Modifier.align(Alignment.Center),
                    painter = painterResource(UIKitIcon.ic_swap_vertical_16),
                    size = 40.dp,
                    color = UIKit.colorScheme.buttonTertiary.primaryBackground,
                    tint = UIKit.colorScheme.buttonTertiary.primaryForeground
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    progress = { 0.5f },
                    trackColor = UIKit.colorScheme.icon.secondary,
                    color = UIKit.colorScheme.icon.primary,
                    gapSize = 0.dp,
                )

                MoonItemSubtitle(
                    text = "1 TON ≈ 1.84 USDT"
                )
            }
        }
    }
}

@Preview
@Composable
private fun SwapScreenShimmerPreview() {
    ThemedPreview {
        Column(
            modifier = Modifier.height(400.dp)
        ) {
            SwapScreenShimmer()
        }
    }
}
