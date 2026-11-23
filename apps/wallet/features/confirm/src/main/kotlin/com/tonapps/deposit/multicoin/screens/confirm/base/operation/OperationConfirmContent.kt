package com.tonapps.deposit.multicoin.screens.confirm.base.operation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.num.FiatRate
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.op.ValidationError
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import com.tonapps.blockchain.model.DappConnectRequest.DomainStatus
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.core.components.Emoji
import com.tonapps.core.components.MoonDomainRiskBottomSheet
import com.tonapps.core.components.assetImageUrl
import com.tonapps.core.components.tokenChainImageUrl
import com.tonapps.deposit.multicoin.screens.confirm.SendConfirmShimmerBody
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmEvent
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.SendingState
import com.tonapps.deposit.multicoin.screens.confirm.UnavailableFeeCell
import com.tonapps.deposit.multicoin.screens.confirm.errorText
import com.tonapps.deposit.multicoin.screens.confirm.feeErrorText
import com.tonapps.deposit.multicoin.screens.confirm.insufficientNetworkFeeText
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.account.AccountBalanceEntity
import com.tonapps.wallet.data.multichain.account.AccountEntity
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.asset.AssetRateEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletType
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.launch
import ui.components.moon.MoonCircleIcon
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.cell.MoonPropertyTitle
import ui.components.moon.cell.MoonPropertyValue
import ui.components.moon.cell.MoonSlideConfirmation
import ui.components.moon.cell.MoonSlideConfirmationState
import ui.components.moon.cell.rememberSliderState
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.components.moon.container.MoonScaffold
import ui.components.moon.dialog.MoonAlertDialog
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit
import ui.theme.modifiers.modifyIf
import ui.theme.modifiers.rememberShimmerPhase
import ui.theme.modifiers.shimmer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import com.tonapps.core.helper.rememberClipboardManager
import ui.components.moon.MoonLargeItemSubtitle
import ui.moon.MoonToastHost
import ui.moon.rememberMoonToastHostState
import uikit.widget.ToastFeedback

/**
 * A row of what the operation does. [label] when the row names who or what takes part — a wallet, a
 * recipient, an app — whose title the transaction confirm sets as a label; the value then takes up to
 * [valueMaxLines] lines, in [valueColor].
 */
class OperationProperty(
    val title: String,
    val value: String,
    val description: String? = null,
    val infoTooltip: String? = null,
    val onClick: (() -> Unit)? = null,
    val label: Boolean = false,
    val valueColor: Color? = null,
    val valueMaxLines: Int = 1,
)

/** How the screen is laid out. */
enum class OperationLayout {
    /**
     * As the transaction confirm, which this screen takes over: what [OperationConfirmContent]'s footer
     * adds stays in the rows' card, a relayer's refusal is told in a toast, and a prepare that fails with
     * nothing priced keeps the skeleton.
     */
    Transaction,

    /** The perps deposit's own design. */
    Deposit,
}

/**
 * @param domainRisk the risk of the dapp that asked for the operation: the swipe warns before it confirms.
 * The operation's own findings, warning or worse, are listed on the swipe too, after it.
 * @param onTopUp with [onOpenBattery], what a shortfall offers: buying the asset, or recharging the
 * battery that would pay the fee. Without them a shortfall is the swipe's error alone.
 * @param onConfirm invoked when the user commits the operation, before it is signed and sent.
 * @param body what the screen shows of the operation in place of its header and rows, a swap's own
 * design; it places the fee row it is handed. The swipe, findings and errors stay the screen's.
 * @param footer what the screen adds under the rows; ignored when [body] is set. It stays the last
 * parameter: a trailing lambda that lands in [body] instead replaces the header, the rows and the fee.
 * The screen is laid out as [BaseOperationConfirmFeature.layout] says.
 */
@Composable
fun OperationConfirmContent(
    feature: BaseOperationConfirmFeature,
    title: String,
    onBack: (() -> Unit)?,
    onClose: () -> Unit,
    onSendSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    titleDetails: String? = null,
    headerImage: AssetEntity? = null,
    properties: List<OperationProperty> = emptyList(),
    trailingProperties: List<OperationProperty> = emptyList(),
    feeReady: Boolean = true,
    feeRow: @Composable (fee: Fee, feeRate: FiatRate?) -> Unit = { fee, feeRate -> NetworkFeeCell(fee, feeRate) },
    confirmPrompt: String? = null,
    domainRisk: DomainStatus? = null,
    onTopUp: ((assetId: String) -> Unit)? = null,
    onOpenBattery: ((walletId: String, from: BatteryNativeFrom) -> Unit)? = null,
    onConfirm: () -> Unit = {},
    body: (@Composable ColumnScope.(feeRow: @Composable () -> Unit) -> Unit)? = null,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    val error by feature.confirmationError.collectAsState()
    val feeUnavailable by feature.feeUnavailable.collectAsState()
    val shortfall by feature.shortfall.collectAsState()
    val sendingState by feature.sendingState.collectAsState()
    val wallet by feature.wallet.collectAsState()
    val account by feature.account.collectAsState()
    val fee by feature.fee.collectAsState()
    val feeRate by feature.feeRate.collectAsState()
    val findings by feature.findings.collectAsState()
    val feeQuoting by feature.feeQuoting.collectAsState()
    val findingsReading by feature.findingsReading.collectAsState()
    val layout = feature.layout
    val toastHost = rememberMoonToastHostState()
    val rejectedText = stringResource(Localization.sending_error)

    // Taken only while the screen is started: a dapp's request sends the app back before the done
    // state is over, and a sheet told to hide in the background never leaves.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(feature, lifecycle) {
        feature.events.flowWithLifecycle(lifecycle).collect { event ->
            when (event) {
                ConfirmEvent.Done -> onSendSuccess()
            }
        }
    }

    // The transaction confirm tells a relayer's refusal in a toast; the swipe says only that it failed.
    // Why it refused is for the logs: our own check's text or the relayer's, neither written for the user.
    LaunchedEffect(error, layout) {
        if (error is ConfirmationError.RelayerRejected && layout == OperationLayout.Transaction) {
            toastHost.showToast(rejectedText, feedback = ToastFeedback.Failure)
        }
    }

    val content = @Composable {
        OperationConfirmContent(
            wallet = wallet,
            account = account,
            fee = fee?.takeIf { feeReady },
            feeRate = feeRate,
            error = error,
            sendingState = sendingState,
            // The swipe waits for the findings of what it signs as it waits for the fee.
            feeQuoting = feeQuoting || findingsReading,
            title = title,
            onBack = onBack,
            onClose = onClose,
            onConfirm = {
                onConfirm()
                feature.sendAction(OperationConfirmAction.Confirm)
            },
            onRetry = { feature.sendAction(OperationConfirmAction.Retry) },
            modifier = modifier,
            layout = layout,
            titleDetails = titleDetails,
            headerImage = account?.asset ?: headerImage,
            properties = properties,
            findings = findings,
            trailingProperties = trailingProperties,
            feeRow = feeRow,
            confirmPrompt = confirmPrompt,
            domainRisk = domainRisk,
            feeUnavailable = feeUnavailable,
            shortfall = shortfall,
            onTopUp = onTopUp,
            onOpenBattery = onOpenBattery,
            footer = footer,
            body = body,
        )
    }

    // The transaction confirm's toasts sit over the whole screen; the perps deposit is laid out on its own.
    if (layout == OperationLayout.Transaction) {
        Box(Modifier.fillMaxSize()) {
            content()
            MoonToastHost(toastHost)
        }
    } else {
        content()
    }
}

@Composable
internal fun OperationConfirmContent(
    wallet: McWalletEntity?,
    account: AccountWithDetails?,
    fee: Fee?,
    feeRate: FiatRate?,
    error: ConfirmationError?,
    sendingState: SendingState,
    title: String,
    onBack: (() -> Unit)?,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    feeQuoting: Boolean = false,
    modifier: Modifier = Modifier,
    layout: OperationLayout = OperationLayout.Deposit,
    titleDetails: String? = null,
    headerImage: AssetEntity? = account?.asset,
    properties: List<OperationProperty> = emptyList(),
    findings: List<OperationFinding> = emptyList(),
    trailingProperties: List<OperationProperty> = emptyList(),
    feeRow: @Composable (fee: Fee, feeRate: FiatRate?) -> Unit = { fee, feeRate -> NetworkFeeCell(fee, feeRate) },
    confirmPrompt: String? = null,
    domainRisk: DomainStatus? = null,
    feeUnavailable: Boolean = false,
    shortfall: OperationShortfall? = null,
    onTopUp: ((assetId: String) -> Unit)? = null,
    onOpenBattery: ((walletId: String, from: BatteryNativeFrom) -> Unit)? = null,
    body: (@Composable ColumnScope.(feeRow: @Composable () -> Unit) -> Unit)? = null,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    val sliderState = rememberSliderState()
    val scope = rememberCoroutineScope()
    var showDomainRiskWarning by remember { mutableStateOf(false) }
    var showFindings by remember { mutableStateOf(false) }
    var showPrompt by remember { mutableStateOf(false) }

    val phase by rememberShimmerPhase()
    val shimmer = when (error) {
        null -> phase
        else -> 0f
    }

    val transaction = layout == OperationLayout.Transaction

    // The transaction confirm names any shortage the same.
    val shortage = error is ConfirmationError.InsufficientNetworkFee && transaction
    val errorText = when {
        shortage -> stringResource(Localization.insufficient_balance)
        error is ConfirmationError.InsufficientNetworkFee -> insufficientNetworkFeeText(error.symbol, error.required)
        else -> error?.errorText
    }
    val feeErrorText = when {
        shortage -> stringResource(Localization.insufficient_balance)
        error is ConfirmationError.InsufficientNetworkFee -> insufficientNetworkFeeText(error.symbol, error.required)
        else -> error?.feeErrorText
    }

    LaunchedEffect(sendingState, error) {
        if (sendingState == SendingState.None && error == null && sliderState.isConfirmed) {
            sliderState.reset()
        }
    }

    MoonScaffold(
        modifier = modifier.padding(
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        ),
        title = "",
        onBack = onBack,
        onClose = onClose,
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .nestedScroll(rememberNestedScrollInteropConnection()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The transaction confirm keeps what the footer adds in the rows' card.
            val inner = footer.takeIf { transaction }
            when {
                fee != null && body != null -> body { feeRow(fee, feeRate) }

                fee != null -> {
                    OperationHeader(image = headerImage, title = title, titleDetails = titleDetails, layout = layout)
                    Spacer(Modifier.height(32.dp))
                    OperationRows(wallet, properties, trailingProperties, layout, inner) {
                        feeRow(fee, feeRate)
                    }
                    if (!transaction) {
                        footer()
                    }
                }

                // What is sent still shows: only the fee could not be priced, and the row prices it again.
                error != null && feeUnavailable && body != null -> {
                    body { UnavailableFeeCell(onRetry = onRetry, divider = false) }
                    Spacer(Modifier.height(16.dp))
                    PrepareErrorMessage(feeErrorText ?: stringResource(Localization.unknown_error))
                }

                error != null && feeUnavailable -> {
                    OperationHeader(image = headerImage, title = title, titleDetails = titleDetails, layout = layout)
                    Spacer(Modifier.height(32.dp))
                    OperationRows(wallet, properties, trailingProperties, layout, inner) {
                        UnavailableFeeCell(onRetry = onRetry, divider = false)
                    }
                    if (!transaction) {
                        footer()
                    }
                    Spacer(Modifier.height(16.dp))
                    PrepareErrorMessage(feeErrorText ?: stringResource(Localization.unknown_error))
                }

                // Nothing priced: the transaction confirm keeps its skeleton, still, and the swipe tells why.
                error != null && transaction -> SendConfirmShimmerBody(shimmer)

                error != null -> {
                    OperationHeader(image = headerImage, title = title, titleDetails = titleDetails, layout = layout)
                    Spacer(Modifier.height(32.dp))
                    PrepareErrorMessage(errorText ?: stringResource(Localization.unknown_error))
                }

                else -> SendConfirmShimmerBody(shimmer)
            }
        }

        val confirmationState = when (sendingState) {
            SendingState.None -> MoonSlideConfirmationState.Slider
            SendingState.Loading -> MoonSlideConfirmationState.Loader
            SendingState.Done -> MoonSlideConfirmationState.Done
        }

        // An info finding asks nothing of the user: only a warning or worse stops the swipe.
        val findingSeverity = findings.severity?.takeIf { it != FindingSeverity.Info }

        // The prompt is asked last, once the findings are read.
        val prompt: () -> Unit = {
            when (confirmPrompt) {
                null -> onConfirm()
                else -> showPrompt = true
            }
        }

        // The dapp's domain is warned of first, then the operation's own findings.
        val proceed: () -> Unit = {
            when (findingSeverity) {
                null -> prompt()
                else -> showFindings = true
            }
        }

        val domainRiskText = when (domainRisk) {
            DomainStatus.Scam -> stringResource(Localization.domain_status_scam)
            DomainStatus.Invalid -> stringResource(Localization.domain_status_invalid)
            DomainStatus.Valid,
            DomainStatus.Unknown,
            null -> null
        }
        if (domainRiskText != null) {
            Text(
                text = domainRiskText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, top = 16.dp, end = 32.dp),
                color = UIKit.colorScheme.accent.red,
                style = UIKit.typography.body2,
                textAlign = TextAlign.Center,
            )
        }

        MoonSlideConfirmation(
            state = confirmationState,
            modifier = when {
                fee != null || error != null -> Modifier
                else -> Modifier.shimmer(shimmer)
            },
            sliderState = sliderState,
            // The transaction confirm tells a relayer's refusal in a toast; its reason is for the logs.
            error = stringResource(Localization.sending_error).takeIf { error is ConfirmationError.RelayerRejected && !transaction }
                ?: errorText?.takeUnless { feeUnavailable },
            thumbColor = when {
                domainRiskText != null -> UIKit.colorScheme.accent.red
                else -> findingSeverity?.color() ?: UIKit.colorScheme.buttonPrimary.primaryBackground
            },
            title = stringResource(
                when (confirmationState) {
                    MoonSlideConfirmationState.Done -> Localization.done
                    else -> Localization.confirm
                },
            ),
            subtitle = stringResource(Localization.swipe_right),
            buttonTitle = stringResource(Localization.try_again),
            enabled = fee != null && sendingState == SendingState.None && !feeQuoting,
            onConfirm = {
                when (domainRiskText) {
                    null -> proceed()
                    else -> showDomainRiskWarning = true
                }
            },
            onClick = when {
                // An expired request is made again on the screen that made it.
                error == ConfirmationError.Expired -> onBack
                error is ConfirmationError.DepositBelowMinimum && fee == null -> onBack
                // It may already be out: the history tells, not another try.
                error == ConfirmationError.OutcomeUnknown || error == ConfirmationError.NonceTaken -> null
                else -> onRetry
            },
            onDone = {},
        )

        if (showDomainRiskWarning && domainRisk != null) {
            MoonDomainRiskBottomSheet(
                status = domainRisk,
                confirmText = stringResource(Localization.confirm_anyway),
                onConfirm = {
                    showDomainRiskWarning = false
                    proceed()
                },
                onClose = {
                    showDomainRiskWarning = false
                    scope.launch { sliderState.reset() }
                },
            )
        }

        if (showFindings && findingSeverity != null) {
            // An info asks nothing of the user: it is not listed among what they confirm past.
            OperationFindingsDialog(
                findings = findings.filter { it.severity != FindingSeverity.Info },
                onConfirm = {
                    showFindings = false
                    prompt()
                },
                onClose = {
                    showFindings = false
                    scope.launch { sliderState.reset() }
                },
            )
        }

        // Leaving it leaves the screen too: the send is made again, fresh, once the balance is topped up.
        val shortage = shortfall
        if (shortage != null && onTopUp != null && onOpenBattery != null) {
            OperationShortfallDialog(
                shortfall = shortage,
                walletId = wallet?.id,
                onTopUp = onTopUp,
                onOpenBattery = onOpenBattery,
                onClose = { onBack?.invoke() ?: onClose() },
            )
        }

        if (showPrompt && confirmPrompt != null) {
            MoonAlertDialog(
                message = confirmPrompt,
                positiveButtonText = stringResource(Localization.continue_action),
                negativeButtonText = stringResource(Localization.cancel),
                onPositiveClick = {
                    showPrompt = false
                    onConfirm()
                },
                onDismiss = {
                    showPrompt = false
                    scope.launch { sliderState.reset() }
                },
            )
        }
    }
}

@Composable
private fun OperationHeader(image: AssetEntity?, title: String, titleDetails: String?, layout: OperationLayout) {
    if (layout == OperationLayout.Transaction) {
        TransactionHeader(image, title)
        return
    }

    OperationImage(image = image, size = 96.dp)
    Spacer(Modifier.height(20.dp))
    Text(
        text = stringResource(Localization.confirm_action),
        style = UIKit.typography.body1,
        color = UIKit.colorScheme.text.secondary,
    )
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = UIKit.typography.h3,
            color = UIKit.colorScheme.text.primary,
        )
        if (titleDetails != null) {
            Text(
                text = titleDetails,
                style = UIKit.typography.h3,
                color = UIKit.colorScheme.text.tertiary,
            )
        }
    }
}

// The transaction confirm's header: the asset with its chain's badge, then what is confirmed.
@Composable
private fun TransactionHeader(image: AssetEntity?, title: String) {
    if (image != null) {
        val chainImageUrl = image.tokenChainImageUrl()
        MoonCutBadgedBox(
            badge = if (chainImageUrl != null) {
                { MoonItemImage(image = chainImageUrl, size = 32.dp) }
            } else {
                null
            },
            direction = BadgeDirection.EndBottom,
        ) {
            MoonItemImage(image = image.assetImageUrl(), size = 96.dp)
        }
    }

    Spacer(Modifier.height(16.dp))
    MoonLargeItemSubtitle(text = stringResource(Localization.confirm_action))
    Spacer(Modifier.height(4.dp))
    Text(
        text = title,
        color = UIKit.colorScheme.text.primary,
        style = UIKit.typography.h3,
        textAlign = TextAlign.Center,
    )
}

/** The wallet, [properties], the fee and [trailingProperties] in one card; [inner] after them, inside it. */
@Composable
private fun OperationRows(
    wallet: McWalletEntity?,
    properties: List<OperationProperty>,
    trailingProperties: List<OperationProperty>,
    layout: OperationLayout,
    inner: (@Composable ColumnScope.() -> Unit)?,
    feeRow: @Composable () -> Unit,
) {
    val clipboardManager = rememberClipboardManager()
    val walletRow = OperationProperty(
        title = stringResource(Localization.wallet),
        value = wallet?.name.orEmpty(),
        label = true,
        onClick = wallet?.takeIf { layout == OperationLayout.Transaction }?.let { { clipboardManager.copy(it.name) } },
    )

    MoonBundleCell {
        Column {
            OperationRow(layout) {
                MoonPropertyBigCell(
                    title = { PropertyTitle(walletRow, layout) },
                    content = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (wallet != null) {
                                Emoji(emoji = wallet.emoji)
                                Spacer(Modifier.width(6.dp))
                            }
                            MoonItemTitle(text = walletRow.value)
                        }
                    },
                    onClick = walletRow.onClick,
                )
            }
            properties.forEach { property ->
                MoonItemDivider()
                OperationPropertyRow(property, layout)
            }
            MoonItemDivider()
            OperationRow(layout) {
                feeRow()
            }
            trailingProperties.forEach { property ->
                MoonItemDivider()
                OperationPropertyRow(property, layout)
            }
            inner?.invoke(this)
        }
    }
}

@Composable
private fun OperationPropertyRow(property: OperationProperty, layout: OperationLayout) {
    val description = property.description
    val minHeight = if (description != null) {
        TWO_LINE_ROW_HEIGHT
    } else {
        ROW_HEIGHT
    }
    OperationRow(layout, minHeight) {
        if (description != null && layout == OperationLayout.Deposit) {
            DepositPropertyCell(property, description)
        } else {
            MoonPropertyBigCell(
                title = { PropertyTitle(property, layout) },
                content = { PropertyValue(property) },
                contentDescription = description?.let {
                    { MoonItemSubtitle(text = it) }
                },
                onClick = property.onClick,
            )
        }
    }
}

@Composable
private fun DepositPropertyCell(property: OperationProperty, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .modifyIf { property.onClick?.let { clickable(onClick = it) } },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PropertyTitle(property, OperationLayout.Deposit)
            PropertyValue(property, Modifier.padding(start = 8.dp))
        }
        MoonItemSubtitle(
            text = description,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun PropertyValue(property: OperationProperty, modifier: Modifier = Modifier) {
    MoonItemTitle(
        text = property.value,
        modifier = modifier,
        color = property.valueColor ?: UIKit.colorScheme.text.primary,
        maxLines = property.valueMaxLines,
    )
}

// The transaction confirm sets the title of a row that names who takes part as a label.
@Composable
private fun PropertyTitle(property: OperationProperty, layout: OperationLayout) {
    val tooltip = property.infoTooltip
    when {
        property.label && layout == OperationLayout.Transaction -> MoonItemTitle(
            text = property.title,
            color = UIKit.colorScheme.text.secondary,
        )
        tooltip != null -> MoonPropertyTitle(title = property.title, infoTooltip = tooltip)
        else -> MoonLargeItemSubtitle(text = property.title)
    }
}

@Composable
internal fun NetworkFeeCell(fee: Fee, feeRate: FiatRate?, infoTooltip: String? = null) {
    val feeDisplay = fee.asset.decimals.toDisplayUnit(fee.amount)
    val feeAmount = remember(feeDisplay) {
        "${Formatter.formatShort(value = feeDisplay, approximate = true)} ${fee.asset.symbol}"
    }
    val feeFiat = remember(feeDisplay, feeRate) {
        feeRate?.let { Formatter.formatFiat(value = feeDisplay, rate = it, separator = " ", approximate = true) }
    }
    MoonPropertyBigCell(
        title = { MoonPropertyTitle(title = stringResource(Localization.network_fee), infoTooltip = infoTooltip) },
        content = {
            MoonPropertyValue(
                title = feeAmount,
                subtitle = feeFiat,
            )
        },
    )
}

@Composable
internal fun PrepareErrorMessage(text: String) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        text = text,
        style = UIKit.typography.body1,
        color = UIKit.colorScheme.accent.red,
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun OperationImage(image: AssetEntity?, size: Dp) {
    if (image == null) {
        MoonCircleIcon(
            painter = painterResource(UIKitIcon.ic_dollar_outline_minus_16),
            size = size,
            iconSize = size * FALLBACK_ICON_RATIO,
            color = UIKit.colorScheme.accent.blue.copy(alpha = ICON_BACKGROUND_ALPHA),
            tint = UIKit.colorScheme.accent.blue,
        )
        return
    }
    val chainImageUrl = image.tokenChainImageUrl()
    MoonCutBadgedBox(
        badge = if (chainImageUrl != null) {
            { MoonItemImage(image = chainImageUrl, size = size * BADGE_RATIO) }
        } else {
            null
        },
        direction = BadgeDirection.EndBottom,
        cutPadding = size * BADGE_CUT_RATIO,
    ) {
        MoonItemImage(image = image.assetImageUrl(), size = size)
    }
}

// The perps deposit's design sets each row's height; the transaction confirm's rows take their own.
@Composable
private fun OperationRow(layout: OperationLayout, minHeight: Dp = ROW_HEIGHT, content: @Composable () -> Unit) {
    if (layout == OperationLayout.Transaction) {
        content()
        return
    }

    Box(
        modifier = Modifier.heightIn(min = minHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

private val ROW_HEIGHT = 56.dp
private val TWO_LINE_ROW_HEIGHT = 76.dp
private const val BADGE_RATIO = 24f / 96f
private const val BADGE_CUT_RATIO = 4f / 96f
private const val ICON_BACKGROUND_ALPHA = 0.12f
private const val FALLBACK_ICON_RATIO = 0.5f

private fun previewAccount(
    assetId: String,
    name: String,
    symbol: String,
    decimals: Int,
    balance: String,
    price: String,
) = AccountWithDetails(
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

@Composable
private fun OperationConfirmPreview(
    error: ConfirmationError?,
    priced: Boolean = true,
    findings: List<ValidationError> = emptyList(),
) {
    val energy = previewAccount(
        assetId = "ton/mainnet/coin",
        name = "Toncoin",
        symbol = "TON",
        decimals = 9,
        balance = "12400000000",
        price = "5.42",
    )
    val account = previewAccount(
        assetId = "ton/mainnet/jetton/0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe",
        name = "Tether USD",
        symbol = "USDT",
        decimals = 6,
        balance = "420000000",
        price = "1.0",
    )

    ThemedPreview(isDarkOnly = true) {
        OperationConfirmContent(
            wallet = McWalletEntity(
                credentialId = "preview",
                name = "Wallet",
                emoji = "\uD83D\uDE00",
                id = "preview",
                type = McWalletType.Multicoin,
            ),
            account = account,
            fee = Fee.Value(energy.asset.value, BigInteger.parseString("55000000")).takeIf { priced },
            feeRate = energy.rate?.value,
            error = error,
            sendingState = SendingState.None,
            title = "Deposit USDT",
            titleDetails = "TON",
            properties = listOf(
                OperationProperty(title = "Total amount", value = "50 USDT", description = "$ 50"),
                OperationProperty(title = "Min. received", value = "$ 49.85"),
            ),
            onBack = {},
            onClose = {},
            onConfirm = {},
            onRetry = {},
            modifier = Modifier.height(PREVIEW_HEIGHT),
            findings = findings.map { OperationFinding(it, asset = null) },
        )
    }
}

@Preview(heightDp = 1460)
@Composable
private fun OperationConfirmPreview() {
    OperationConfirmPreview(error = null)
}

// Priced, and then the quote ran out: the rows stay, the slot says so and "Try again" goes back.
@Preview(heightDp = 1460)
@Composable
private fun OperationConfirmExpiredPreview() {
    OperationConfirmPreview(error = ConfirmationError.Expired)
}

@Preview(heightDp = 1460)
@Composable
private fun OperationConfirmPrepareFailedPreview() {
    OperationConfirmPreview(error = ConfirmationError.InsufficientBalance, priced = false)
}

// A warning turns the thumb orange; the swipe lists the findings before it signs.
@Preview(heightDp = 1460)
@Composable
private fun OperationConfirmWarningPreview() {
    OperationConfirmPreview(
        error = null,
        findings = listOf(ValidationError.DurableNonce("0")),
    )
}

@Preview(heightDp = 1460)
@Composable
private fun OperationConfirmDangerPreview() {
    OperationConfirmPreview(
        error = null,
        findings = listOf(ValidationError.ForeignRecipient("0", expected = "0:aa", actual = "0:bb")),
    )
}

private val PREVIEW_HEIGHT = 720.dp
