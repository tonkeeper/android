package com.tonapps.perps.screens.deposit

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.core.components.assetImageUrl
import com.tonapps.core.components.tokenChainImageUrl
import com.tonapps.extensions.fiatSymbol
import com.tonapps.mvi.graph.KState
import com.tonapps.paging.collectAsStateWorkaround
import com.tonapps.perps.data.USD_CURRENCY
import com.tonapps.perps.data.formatFiatAmount
import com.tonapps.perps.data.formatTokenCompact
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.perps.data.limitFractionDigits
import com.tonapps.perps.data.sanitizeAmount
import com.tonapps.perps.funding.PerpsDepositQuoteError
import com.tonapps.perps.funding.PerpsDepositSource
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import com.wallet.crypto.trustapp.common.ui.components.MoonEditText
import ui.components.moon.MoonBadgeButton
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLabelDefault
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
import ui.theme.UIKit
import java.math.BigDecimal

@Composable
fun PerpsDepositScreen(
    feature: PerpsDepositFeature,
    onClose: () -> Unit,
    onReview: (ConfirmRequest) -> Unit,
    onAddFunds: () -> Unit,
) {
    val sources by feature.sources.collectAsStateWorkaround()
    val selected by feature.selected.collectAsStateWorkaround()
    val amountInput by feature.amountInput.collectAsStateWorkaround()
    val inputInFiat by feature.inputInFiat.collectAsStateWorkaround()
    val summary by feature.summary.collectAsStateWorkaround()
    val paymentInFlight by feature.paymentInFlight.collectAsStateWorkaround()
    val computingMax by feature.computingMax.collectAsStateWorkaround()
    val quoting by feature.quoting.collectAsStateWorkaround()
    val quoteError by feature.quoteError.collectAsStateWorkaround()
    var showSources by remember { mutableStateOf(false) }
    val currentOnReview by rememberUpdatedState(onReview)

    LaunchedEffect(feature) {
        feature.events.collect { event ->
            when (event) {
                is PerpsDepositEvent.Reviewed -> currentOnReview(event.request)
            }
        }
    }

    val item = selected
    val source = item?.source
    val current = summary
    val currentQuoteError = quoteError
    val canReview = source != null &&
        current?.canReview == true &&
        !paymentInFlight &&
        !computingMax
    val hasBalance = source != null && source.balanceUnits.signum() > 0
    val review = { feature.sendAction(PerpsDepositAction.Review) }

    MoonScaffold(
        modifier = Modifier
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        title = stringResource(Localization.perps_deposit_title),
        onClose = onClose,
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
                        source?.decimals ?: FIAT_FRACTION_DIGITS
                    },
                    onAmountChange = { feature.sendAction(PerpsDepositAction.SetAmount(it)) },
                    onSwap = { feature.sendAction(PerpsDepositAction.ToggleInput) },
                    onDone = review,
                    currencyPrefix = currencyCode.fiatSymbol().takeIf { inputInFiat },
                    enabled = source != null,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                            Modifier
                        } else {
                            Modifier.alpha(DISABLED_ALPHA)
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

                        current?.insufficient == true -> MoonItemSubtitle(
                            text = stringResource(Localization.insufficient_balance),
                            color = UIKit.colorScheme.accent.red,
                        )

                        current?.belowMinimum == true -> MoonItemSubtitle(
                            text = minimumText(current.minimum?.usd),
                            color = UIKit.colorScheme.accent.red,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                            maxLines = 2,
                        )

                        currentQuoteError != null -> MoonItemSubtitle(
                            text = quoteErrorText(currentQuoteError),
                            color = UIKit.colorScheme.accent.red,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                            maxLines = 2,
                        )

                        item != null -> AvailableRow(
                            item = item,
                            inputInFiat = inputInFiat,
                            modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                val keyboardController = LocalSoftwareKeyboardController.current
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
            }

            MoonBottomButtonCell(
                text = stringResource(Localization.perps_deposit_review),
                enabled = canReview,
                loading = quoting,
                onClick = review,
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
private fun quoteErrorText(error: PerpsDepositQuoteError): String {
    return when (error) {
        is PerpsDepositQuoteError.BelowMinimum -> minimumText(error.minUsd)
        is PerpsDepositQuoteError.InsufficientNetworkFee -> stringResource(
            Localization.perps_deposit_insufficient_network_fee,
            error.symbol,
        )
        PerpsDepositQuoteError.InsufficientBalance -> stringResource(Localization.insufficient_balance)
        PerpsDepositQuoteError.ServiceUnavailable -> stringResource(Localization.perps_service_unavailable)
        PerpsDepositQuoteError.Failed -> stringResource(Localization.perps_deposit_failed)
        PerpsDepositQuoteError.QuoteFailed -> stringResource(Localization.perps_deposit_quote_failed)
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
private fun AvailableRow(
    item: PerpsDepositSourceUi,
    inputInFiat: Boolean,
    modifier: Modifier = Modifier,
) {
    val value = if (inputInFiat && item.source.price != null) {
        item.fiatFormat
    } else {
        item.balanceFormat
    }
    MoonItemSubtitle(
        text = stringResource(Localization.available, value),
        modifier = modifier,
        textAlign = TextAlign.End,
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
            tags = { SourceTags(source) },
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

@Composable
internal fun SourceTags(source: PerpsDepositSource) {
    if (showsNetwork(source)) {
        MoonLabel(text = networkName(source.account.value.chain))
    }
    if (source.isBestValue) {
        MoonLabel(
            text = stringResource(Localization.perps_deposit_best_value),
            colors = MoonLabelDefault.blue(),
        )
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
) {
    MoonBundleCell(modifier = Modifier.height(188.dp)) {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                val focusRequester = remember { FocusRequester() }
                val scrollState = rememberScrollState()
                val style = remember { TextStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp) }

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
                        .focusRequester(focusRequester)
                        .weight(1f, fill = false)
                        .horizontalScroll(scrollState)
                        .width(IntrinsicSize.Min)
                        .defaultMinSize(minWidth = 24.dp),
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
                    placeholder = { Text(text = "0", style = style, color = UIKit.colorScheme.text.tertiary) },
                )

                if (currencyPrefix == null) {
                    Text(
                        modifier = Modifier.padding(bottom = 4.dp),
                        text = currencyCode,
                        style = UIKit.typography.num2,
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

            Spacer(Modifier.height(8.dp))

            if (pillText != null) {
                Row(
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(CircleShape)
                        .border(width = 1.5.dp, color = UIKit.colorScheme.background.contentTint, shape = CircleShape)
                        .then(
                            if (onSwap != null) {
                                Modifier.clickable(onClick = onSwap)
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = pillText,
                        color = UIKit.colorScheme.text.secondary,
                        style = UIKit.typography.body1,
                    )
                    if (onSwap != null) {
                        MoonItemIcon(
                            painter = painterResource(UIKitIcon.ic_switch_16),
                            size = 16.dp,
                            color = UIKit.colorScheme.icon.secondary,
                        )
                    }
                }
            }
        }
    }
}

private const val FIAT_FRACTION_DIGITS = 2
private const val BADGE_RATIO = 20f / 44f
private const val DISABLED_ALPHA = 0.48f
