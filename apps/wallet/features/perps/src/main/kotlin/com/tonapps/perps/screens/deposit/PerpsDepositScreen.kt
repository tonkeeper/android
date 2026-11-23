package com.tonapps.perps.screens.deposit

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.core.components.assetImageUrl
import com.tonapps.core.components.tokenChainImageUrl
import com.tonapps.deposit.multicoin.screens.confirm.insufficientNetworkFeeText
import com.tonapps.extensions.fiatSymbol
import com.tonapps.mvi.graph.KState
import com.tonapps.paging.collectAsStateWorkaround
import com.tonapps.perps.data.USD_CURRENCY
import com.tonapps.perps.data.formatFiatAmount
import com.tonapps.perps.data.formatTokenCompact
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.perps.data.formatUsdAmountRoundedToCents
import com.tonapps.perps.data.limitFractionDigits
import com.tonapps.perps.data.sanitizeAmount
import com.tonapps.perps.funding.PerpsDepositQuoteError
import com.tonapps.perps.funding.PerpsDepositSource
import com.tonapps.perps.screens.common.PerpsDecimalInputTransformation
import com.tonapps.perps.screens.common.PerpsFundingTopBar
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import com.wallet.crypto.trustapp.common.ui.components.MoonEditText
import com.wallet.crypto.trustapp.common.ui.components.amountFieldScroll
import ui.components.moon.MoonBadgeButton
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLoader
import ui.components.moon.MoonSmallItemTitle
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.cell.MoonRetryCell
import ui.components.moon.cell.TextCell
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.components.moon.container.MoonScaffold
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit
import java.math.BigDecimal

@Composable
fun PerpsDepositScreen(
    feature: PerpsDepositFeature,
    isOnTop: Boolean,
    onClose: () -> Unit,
    onReview: (ConfirmRequest) -> Unit,
    onRegionRestricted: () -> Unit,
    onAddFunds: () -> Unit,
    onChooseAnotherWallet: () -> Unit,
) {
    val sources by feature.sources.collectAsStateWorkaround()
    val selected by feature.selected.collectAsStateWorkaround()
    val amountInput by feature.amountInput.collectAsStateWorkaround()
    val inputInFiat by feature.inputInFiat.collectAsStateWorkaround()
    val summary by feature.summary.collectAsStateWorkaround()
    val paymentInFlight by feature.paymentInFlight.collectAsStateWorkaround()
    val computingMax by feature.computingMax.collectAsStateWorkaround()
    val quoting by feature.quoting.collectAsStateWorkaround()
    val preview by feature.preview.collectAsStateWorkaround()
    val canReview by feature.reviewEnabled.collectAsStateWorkaround()
    val tradingKeyKept by feature.tradingKeyKept.collectAsStateWorkaround()
    val anotherWalletAvailable by feature.anotherPerpsWalletAvailable.collectAsStateWorkaround()
    var showSources by remember { mutableStateOf(false) }
    val currentOnReview by rememberUpdatedState(onReview)
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnRegionRestricted by rememberUpdatedState(onRegionRestricted)

    LaunchedEffect(feature, isOnTop) {
        if (isOnTop) {
            feature.sendAction(PerpsDepositAction.Shown)
        }
    }

    LaunchedEffect(feature) {
        feature.events.collect { event ->
            when (event) {
                is PerpsDepositEvent.Reviewed -> currentOnReview(event.request)
                PerpsDepositEvent.Closed -> currentOnClose()
                PerpsDepositEvent.RegionRestricted -> currentOnRegionRestricted()
            }
        }
    }

    val item = selected
    val source = item?.source
    val current = summary
    val shownPreview = preview
    val retryableFailure = shownPreview.retryableFailure
    val amountRefusal = shownPreview.amountRefusal
    val hasBalance = source != null && source.balanceUnits.signum() > 0
    val addressBlocked = current?.addressBlocked == true && !paymentInFlight
    val offersAnotherWallet = addressBlocked && anotherWalletAvailable
    val review = { feature.sendAction(PerpsDepositAction.Review) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val chooseAnotherWallet = {
        keyboardController?.hide()
        onChooseAnotherWallet()
    }

    MoonScaffold(
        modifier = Modifier
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        topBar = {
            PerpsFundingTopBar(
                title = stringResource(Localization.perps_deposit_title),
                onClose = onClose,
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .nestedScroll(rememberNestedScrollInteropConnection()),
            ) {
                val currencyCode = source?.account?.rate?.currencyCode ?: USD_CURRENCY
                PerpsAmountInputCell(
                    amount = amountInput,
                    currencyCode = if (inputInFiat) {
                        currencyCode
                    } else {
                        source?.symbol.orEmpty()
                    },
                    pillText = when {
                        source == null || current == null -> null
                        inputInFiat -> formatTokenCompact(current.amount, source.symbol)
                        else -> current.fiat?.let { formatFiatAmount(it, currencyCode) }
                    },
                    maxFractionDigits = if (inputInFiat) {
                        FIAT_FRACTION_DIGITS
                    } else {
                        source?.amountInputDecimals ?: FIAT_FRACTION_DIGITS
                    },
                    onAmountChange = { feature.sendAction(PerpsDepositAction.SetAmount(it)) },
                    onSwap = { feature.sendAction(PerpsDepositAction.ToggleInput) },
                    onDone = review,
                    currencyPrefix = currencyCode.fiatSymbol().takeIf { inputInFiat },
                    enabled = source != null && tradingKeyKept,
                    showPlaceholder = source != null,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MoonBadgeButton(
                        content = {
                            Box(contentAlignment = Alignment.Center) {
                                MoonSmallItemTitle(
                                    text = stringResource(Localization.max).uppercase(),
                                    modifier = if (computingMax) {
                                        Modifier.alpha(0f)
                                    } else {
                                        Modifier
                                    },
                                )
                                if (computingMax) {
                                    MoonLoader(modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = if (hasBalance) {
                            Modifier.height(MAX_BUTTON_HEIGHT)
                        } else {
                            Modifier
                                .height(MAX_BUTTON_HEIGHT)
                                .alpha(DISABLED_ALPHA)
                        },
                        onClick = { feature.sendAction(PerpsDepositAction.Max) }.takeIf { hasBalance && !computingMax },
                    )
                    when {
                        paymentInFlight -> MoonItemSubtitle(
                            text = stringResource(Localization.perps_deposit_payment_in_flight),
                            color = UIKit.colorScheme.accent.orange,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                            maxLines = 2,
                        )

                        current?.insufficient == true && !addressBlocked -> MoonItemSubtitle(
                            text = stringResource(Localization.insufficient_balance),
                            color = UIKit.colorScheme.accent.red,
                        )

                        current?.belowMinimum == true && !addressBlocked -> MoonItemSubtitle(
                            text = minimumText(current.minimum?.usd),
                            color = UIKit.colorScheme.accent.red,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                            maxLines = 2,
                        )

                        amountRefusal != null && !addressBlocked -> MoonItemSubtitle(
                            text = quoteErrorText(amountRefusal),
                            color = UIKit.colorScheme.accent.red,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                            maxLines = Int.MAX_VALUE,
                        )

                        item != null -> AddFundsLink(
                            onClick = {
                                keyboardController?.hide()
                                onAddFunds()
                            },
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                SourcesCell(
                    sources = sources,
                    selected = item,
                    onRetry = { feature.sendAction(PerpsDepositAction.Reload) },
                    onAddFunds = onAddFunds,
                    onClick = {
                        keyboardController?.hide()
                        showSources = true
                    },
                )

                if (addressBlocked) {
                    Text(
                        text = stringResource(
                            if (anotherWalletAvailable) {
                                Localization.perps_deposit_address_blocked
                            } else {
                                Localization.perps_deposit_address_blocked_no_other_wallet
                            },
                        ),
                        style = UIKit.typography.body3,
                        color = UIKit.colorScheme.accent.red,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
                    )
                }

                DepositQuote(
                    preview = if (amountRefusal == null) {
                        shownPreview
                    } else {
                        PerpsDepositQuotePreview.None
                    },
                )
            }

            MoonBottomButtonCell(
                text = stringResource(
                    when {
                        offersAnotherWallet -> Localization.perps_deposit_choose_another_wallet
                        current == null || current.amountUnits.signum() == 0 -> Localization.perps_deposit_enter_amount
                        retryableFailure -> Localization.retry
                        shownPreview is PerpsDepositQuotePreview.Failed && amountRefusal == null -> Localization.perps_deposit_no_quote
                        else -> Localization.perps_deposit_review
                    },
                ),
                enabled = canReview || offersAnotherWallet,
                loading = quoting,
                onClick = if (offersAnotherWallet) {
                    chooseAnotherWallet
                } else {
                    review
                },
            )
        }
    }

    val loaded = sources.state
    if (showSources && loaded != null) {
        PerpsDepositAssetDialog(
            sources = loaded,
            selectedAssetId = source?.assetId,
            onSelect = { feature.sendAction(PerpsDepositAction.SelectSource(it)) },
            onClose = { showSources = false },
        )
    }
}

@Composable
private fun DepositQuote(preview: PerpsDepositQuotePreview) {
    when (preview) {
        PerpsDepositQuotePreview.None -> Unit

        PerpsDepositQuotePreview.Loading -> DepositQuoteCaption {
            MoonLoader(
                modifier = Modifier
                    .padding(end = 2.dp)
                    .size(16.dp),
                color = UIKit.colorScheme.icon.secondary,
            )
            Text(
                text = stringResource(Localization.perps_deposit_fetching_quote),
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.text.secondary,
            )
        }

        is PerpsDepositQuotePreview.Failed -> DepositQuoteCaption {
            MoonItemIcon(
                painter = painterResource(UIKitIcon.ic_exclamationmark_circle_16),
                size = 16.dp,
                color = UIKit.colorScheme.accent.red,
            )
            Text(
                text = quoteErrorText(preview.error),
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.accent.red,
                textAlign = TextAlign.Center,
            )
        }

        is PerpsDepositQuotePreview.Ready -> DepositQuoteCells(
            youGetUsd = preview.youGetUsd,
            feeUsd = preview.feeUsd,
            activatesAccount = preview.activatesAccount,
            showsFees = !rememberKeyboardShown(),
        )
    }
}

@Composable
private fun rememberKeyboardShown(): Boolean {
    val ime = WindowInsets.ime
    val navigationBars = WindowInsets.navigationBars
    val density = LocalDensity.current
    val keyboardShown by remember(ime, navigationBars, density) {
        derivedStateOf { ime.getBottom(density) > navigationBars.getBottom(density) }
    }
    return keyboardShown
}

@Composable
private fun DepositQuoteCaption(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 12.dp)
            .heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        content = content,
    )
}

@Composable
private fun DepositQuoteCells(
    youGetUsd: BigDecimal,
    feeUsd: BigDecimal?,
    activatesAccount: Boolean,
    showsFees: Boolean,
) {
    val youGet = remember(youGetUsd) { formatUsdAmount(youGetUsd) }
    val fee = remember(feeUsd) { feeUsd?.let(::formatUsdAmountRoundedToCents) }?.takeIf { showsFees }
    val body2 = UIKit.typography.body2
    val footnoteStyle = remember(body2) { body2.copy(lineHeightStyle = UNTRIMMED_LINE_HEIGHT) }
    Column(Modifier.padding(top = 16.dp)) {
        MoonBundleCell {
            Column {
                DepositQuoteCell(
                    title = stringResource(Localization.perps_deposit_to_perps_account),
                    value = stringResource(Localization.perps_deposit_approx_value, youGet),
                )
                if (fee != null) {
                    MoonItemDivider()
                    DepositQuoteCell(
                        title = stringResource(Localization.perps_deposit_fees),
                        value = stringResource(Localization.perps_deposit_approx_value, fee),
                        note = if (activatesAccount) {
                            stringResource(Localization.perps_deposit_account_setup_note)
                        } else {
                            null
                        },
                    )
                }
            }
        }
        if (fee != null) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, top = 12.dp, end = 32.dp, bottom = 16.dp),
                text = stringResource(Localization.perps_deposit_no_keeper_fee),
                style = footnoteStyle,
                color = UIKit.colorScheme.text.secondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DepositQuoteCell(title: String, value: String, note: String? = null) {
    val body1 = UIKit.typography.body1
    val lineStyle = remember(body1) { body1.copy(lineHeightStyle = UNTRIMMED_LINE_HEIGHT) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = title,
                style = lineStyle,
                color = UIKit.colorScheme.text.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MoonItemTitle(text = value)
        }
        if (note != null) {
            Text(
                text = note,
                style = lineStyle,
                color = UIKit.colorScheme.text.secondary,
            )
        }
    }
}

@Preview
@Composable
private fun DepositQuoteCellsPreview() {
    ThemedPreview(isDarkOnly = true) {
        Column {
            DepositQuoteCells(
                youGetUsd = BigDecimal("709.19"),
                feeUsd = BigDecimal("0.5"),
                activatesAccount = true,
                showsFees = true,
            )
            DepositQuoteCells(
                youGetUsd = BigDecimal("709.19"),
                feeUsd = BigDecimal("0.5"),
                activatesAccount = false,
                showsFees = true,
            )
            DepositQuoteCells(
                youGetUsd = BigDecimal("709.19"),
                feeUsd = BigDecimal("0.5"),
                activatesAccount = true,
                showsFees = false,
            )
        }
    }
}

@Composable
private fun quoteErrorText(error: PerpsDepositQuoteError): String {
    return when (error) {
        is PerpsDepositQuoteError.BelowMinimum -> minimumText(error.minUsd)
        is PerpsDepositQuoteError.InsufficientNetworkFee -> insufficientNetworkFeeText(error.symbol, error.required)
        PerpsDepositQuoteError.InsufficientBalance -> stringResource(Localization.insufficient_balance)
        PerpsDepositQuoteError.ServiceUnavailable -> stringResource(Localization.perps_service_unavailable)
        PerpsDepositQuoteError.Failed -> stringResource(Localization.perps_deposit_quote_failed)
        PerpsDepositQuoteError.QuoteFailed -> stringResource(Localization.perps_deposit_no_quote_change_amount)
        PerpsDepositQuoteError.AddressBlocked -> stringResource(Localization.perps_deposit_address_blocked)
    }
}

@Composable
private fun minimumText(minUsd: BigDecimal?): String {
    return if (minUsd != null) {
        stringResource(Localization.perps_deposit_min_amount_usd, formatUsdAmount(minUsd))
    } else {
        stringResource(Localization.perps_deposit_min_amount)
    }
}

@Composable
private fun AddFundsLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier.clickable(onClick = onClick),
        text = stringResource(Localization.add_funds),
        style = UIKit.typography.body2,
        color = UIKit.colorScheme.text.accent,
        maxLines = 1,
    )
}

@Composable
private fun SourcesCell(
    sources: KState<List<PerpsDepositSourceUi>?>,
    selected: PerpsDepositSourceUi?,
    onRetry: () -> Unit,
    onAddFunds: () -> Unit,
    onClick: () -> Unit,
) {
    when {
        selected != null -> SourceCell(item = selected, onClick = onClick)
        sources is KState.Loading -> MoonLoaderCell(height = 76.dp)
        sources.state == null -> MoonRetryCell(
            message = stringResource(Localization.perps_deposit_sources_failed),
            buttonText = stringResource(Localization.perps_retry),
            onRetry = onRetry,
        )
        sources.state.isNullOrEmpty() -> MoonRetryCell(
            message = stringResource(Localization.perps_deposit_no_sources),
            buttonText = stringResource(Localization.add_funds),
            onRetry = onAddFunds,
        )
    }
}

@Composable
internal fun SourceCell(item: PerpsDepositSourceUi, onClick: () -> Unit) {
    val source = item.source
    MoonBundleCell {
        TextCell(
            title = source.symbol,
            subtitle = sourceSubtitle(item),
            onClick = onClick,
            content = { MoonItemIcon(painterResource(UIKitIcon.ic_switch_16)) },
            minHeight = 76.dp,
            image = { SourceImage(source) },
        )
    }
}

@Composable
internal fun SourceImage(source: PerpsDepositSource, size: Dp = 44.dp) {
    val asset = source.account.asset
    val chainImageUrl = asset.tokenChainImageUrl()
    MoonCutBadgedBox(
        badge = if (chainImageUrl != null) {
            { MoonItemImage(image = chainImageUrl, size = size * BADGE_RATIO) }
        } else {
            null
        },
        direction = BadgeDirection.EndBottom,
    ) {
        MoonItemImage(image = asset.assetImageUrl(), size = size)
    }
}

internal fun showsNetwork(source: PerpsDepositSource): Boolean {
    if (!source.fundingAsset.isCoin) {
        return true
    }
    return when (source.account.value.chain.network.type) {
        Network.Type.Arbitrum,
        Network.Type.Base,
        Network.Type.Robinhood -> true

        else -> false
    }
}

internal fun networkName(chain: Chain): String {
    return chain.name
}

@Composable
internal fun sourceSubtitle(item: PerpsDepositSourceUi): String {
    if (item.source.price == null) {
        return item.balanceFormat
    }
    return stringResource(Localization.perps_deposit_source_pattern, item.balanceFormat, item.fiatFormat)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PerpsAmountInputCell(
    amount: String,
    currencyCode: String,
    pillText: String?,
    maxFractionDigits: Int,
    onAmountChange: (String) -> Unit,
    onSwap: (() -> Unit)?,
    onDone: () -> Unit,
    currencyPrefix: String? = null,
    enabled: Boolean = true,
    showPlaceholder: Boolean = true,
) {
    MoonBundleCell(modifier = Modifier.height(156.dp).semantics { testTagsAsResourceId = true }) {
        BoxWithConstraints(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
        ) {
            val numStyle = UIKit.typography.num1
            val baseStyle = remember(numStyle) { numStyle.copy(fontSize = AMOUNT_FONT_SIZE, lineHeight = AMOUNT_LINE_HEIGHT, letterSpacing = 0.sp) }
            val suffixStyle = UIKit.typography.num2
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val availableWidth = constraints.maxWidth
            val scale = remember(amount, currencyPrefix, currencyCode, availableWidth, baseStyle, suffixStyle) {
                val shown = PerpsDecimalInputTransformation.filter(AnnotatedString(amount.ifEmpty { "0" })).text
                val digits = measurer.measure(shown, baseStyle).size.width
                val prefix = currencyPrefix?.let { measurer.measure(it, baseStyle).size.width } ?: 0
                val suffix = if (currencyPrefix == null) {
                    measurer.measure(currencyCode, suffixStyle).size.width
                } else {
                    0
                }
                val gaps = with(density) { AMOUNT_ROW_ALLOWANCE.roundToPx() }
                (availableWidth.toFloat() / (digits + prefix + suffix + gaps)).coerceIn(AMOUNT_MIN_SCALE, 1f)
            }
            val style = baseStyle.copy(fontSize = AMOUNT_FONT_SIZE * scale)
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    val focusRequester = remember { FocusRequester() }
                    val scrollState = rememberScrollState()

                    var field by remember { mutableStateOf(TextFieldValue(amount, TextRange(amount.length))) }
                    var lastSent by remember { mutableStateOf(amount) }

                    LaunchedEffect(amount) {
                        if (amount != lastSent) {
                            lastSent = amount
                            field = TextFieldValue(amount, TextRange(amount.length))
                        }
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }

                    if (currencyPrefix != null) {
                        Text(
                            text = currencyPrefix,
                            style = style,
                            color = UIKit.colorScheme.text.secondary,
                        )
                    }

                    MoonEditText(
                        paddingValues = PaddingValues(),
                        modifier = Modifier
                            .testTag("perps_amount_input")
                            .focusRequester(focusRequester)
                            .weight(1f, fill = false)
                            .amountFieldScroll(scrollState),
                        value = field,
                        enabled = enabled,
                        onValueChange = { changed ->
                            val sanitized = limitFractionDigits(sanitizeAmount(changed.text), maxFractionDigits)
                            val caret = (changed.selection.end + sanitized.length - changed.text.length)
                                .coerceIn(0, sanitized.length)
                            field = TextFieldValue(sanitized, TextRange(caret))
                            if (sanitized != lastSent) {
                                lastSent = sanitized
                                onAmountChange(sanitized)
                            }
                        },
                        textStyle = style,
                        keyboardActions = remember { KeyboardActions(onDone = { onDone() }) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        visualTransformation = PerpsDecimalInputTransformation,
                        placeholder = if (showPlaceholder) {
                            { Text(text = "0", style = style, color = UIKit.colorScheme.text.tertiary) }
                        } else {
                            null
                        },
                    )

                    if (currencyPrefix == null) {
                        Text(
                            modifier = Modifier.padding(bottom = 4.dp),
                            text = currencyCode,
                            style = suffixStyle.copy(fontSize = suffixStyle.fontSize * scale),
                            color = UIKit.colorScheme.text.secondary,
                        )
                    }

                    DisposableEffect(enabled) {
                        if (enabled) {
                            focusRequester.requestFocus()
                        }
                        onDispose { focusRequester.freeFocus() }
                    }
                }

                if (pillText != null) {
                    Row(
                        modifier = Modifier
                            .testTag("amount_input_converted")
                            .clip(CircleShape)
                            .border(width = 1.5.dp, color = UIKit.colorScheme.background.contentTint, shape = CircleShape)
                            .then(
                                if (onSwap != null) {
                                    Modifier.clickable(onClick = onSwap)
                                } else {
                                    Modifier
                                },
                            )
                            .height(40.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = pillText,
                            color = UIKit.colorScheme.text.secondary,
                            style = UIKit.typography.body1,
                        )
                        if (onSwap != null) {
                            MoonItemIcon(
                                painter = painterResource(UIKitIcon.ic_swap_vertical_16),
                                size = 16.dp,
                                color = UIKit.colorScheme.icon.secondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val AMOUNT_FONT_SIZE = 44.sp
private val AMOUNT_LINE_HEIGHT = 56.sp
private val AMOUNT_ROW_ALLOWANCE = 24.dp
private const val AMOUNT_MIN_SCALE = 0.5f
private const val FIAT_FRACTION_DIGITS = 2
private const val BADGE_RATIO = 20f / 44f
private const val DISABLED_ALPHA = 0.48f
private val MAX_BUTTON_HEIGHT = 36.dp
private val UNTRIMMED_LINE_HEIGHT = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)
