package com.tonapps.deposit.multicoin.screens.confirm.base.operation

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.num.FiatRate
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.core.components.Emoji
import com.tonapps.core.components.assetImageUrl
import com.tonapps.core.components.tokenChainImageUrl
import com.tonapps.deposit.multicoin.screens.confirm.SendConfirmShimmerBody
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmEvent
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.SendingState
import com.tonapps.deposit.multicoin.screens.confirm.errorText
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.account.AccountBalanceEntity
import com.tonapps.wallet.data.multichain.account.AccountEntity
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.asset.AssetRateEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletType
import com.tonapps.wallet.localization.Localization
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
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit
import ui.theme.modifiers.rememberShimmerPhase
import ui.theme.modifiers.shimmer

class OperationProperty(
    val title: String,
    val value: String,
    val description: String? = null,
)

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
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val error by feature.confirmationError.collectAsState()
    val sendingState by feature.sendingState.collectAsState()
    val wallet by feature.wallet.collectAsState()
    val account by feature.account.collectAsState()
    val fee by feature.fee.collectAsState()
    val feeRate by feature.feeRate.collectAsState()

    LaunchedEffect(feature) {
        feature.events.collect { event ->
            when (event) {
                ConfirmEvent.Done -> onSendSuccess()
            }
        }
    }

    OperationConfirmContent(
        wallet = wallet,
        account = account,
        fee = fee,
        feeRate = feeRate,
        error = error,
        sendingState = sendingState,
        title = title,
        onBack = onBack,
        onClose = onClose,
        onConfirm = { feature.sendAction(OperationConfirmAction.Confirm(context)) },
        onRetry = { feature.sendAction(OperationConfirmAction.Retry(context)) },
        modifier = modifier,
        titleDetails = titleDetails,
        headerImage = account?.asset ?: headerImage,
        properties = properties,
        footer = footer,
    )
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
    modifier: Modifier = Modifier,
    titleDetails: String? = null,
    headerImage: AssetEntity? = account?.asset,
    properties: List<OperationProperty> = emptyList(),
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    val sliderState = rememberSliderState()

    val phase by rememberShimmerPhase()
    val shimmer = when (error) {
        null -> phase
        else -> 0f
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            when {
                fee != null -> {
                    OperationHeader(
                        image = headerImage,
                        title = title,
                        titleDetails = titleDetails,
                    )
                    Spacer(Modifier.height(32.dp))
                    OperationRows(wallet = wallet, properties = properties, fee = fee, feeRate = feeRate)
                    footer()
                }

                error != null -> {
                    OperationHeader(
                        image = headerImage,
                        title = title,
                        titleDetails = titleDetails,
                    )
                    Spacer(Modifier.height(32.dp))
                    PrepareErrorMessage(error.errorText ?: stringResource(Localization.unknown_error))
                }

                else -> SendConfirmShimmerBody(shimmer)
            }
        }

        val confirmationState = when (sendingState) {
            SendingState.None -> MoonSlideConfirmationState.Slider
            SendingState.Loading -> MoonSlideConfirmationState.Loader
            SendingState.Done -> MoonSlideConfirmationState.Done
        }

        MoonSlideConfirmation(
            state = confirmationState,
            modifier = when {
                fee != null || error != null -> Modifier
                else -> Modifier.shimmer(shimmer)
            },
            sliderState = sliderState,
            error = error?.errorText,
            title = stringResource(
                when (confirmationState) {
                    MoonSlideConfirmationState.Done -> Localization.done
                    else -> Localization.confirm
                },
            ),
            subtitle = stringResource(Localization.swipe_right),
            buttonTitle = stringResource(Localization.try_again),
            enabled = fee != null && sendingState == SendingState.None,
            onConfirm = onConfirm,
            onClick = when (error) {
                // An expired request is made again on the screen that made it.
                ConfirmationError.Expired -> onBack
                else -> onRetry
            },
            onDone = {},
        )
    }
}

@Composable
private fun OperationHeader(image: AssetEntity?, title: String, titleDetails: String?) {
    OperationImage(image = image, size = 96.dp)
    Spacer(Modifier.height(20.dp))
    MoonItemSubtitle(
        text = stringResource(Localization.confirm_action),
        color = UIKit.colorScheme.text.secondary,
    )
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = UIKit.typography.h2,
            color = UIKit.colorScheme.text.primary,
        )
        if (titleDetails != null) {
            Text(
                text = titleDetails,
                style = UIKit.typography.h2,
                color = UIKit.colorScheme.text.secondary,
            )
        }
    }
}

@Composable
private fun OperationRows(
    wallet: McWalletEntity?,
    properties: List<OperationProperty>,
    fee: Fee,
    feeRate: FiatRate?,
) {
    val feeDisplay = fee.asset.decimals.toDisplayUnit(fee.amount)
    val feeAmount = remember(feeDisplay) {
        "${Formatter.formatShort(value = feeDisplay, approximate = true)} ${fee.asset.symbol}"
    }
    val feeFiat = remember(feeDisplay, feeRate) {
        feeRate?.let { Formatter.formatFiat(value = feeDisplay, rate = it, separator = " ", approximate = true) }
    }

    MoonBundleCell {
        Column {
            OperationRow {
                MoonPropertyBigCell(
                    title = { MoonPropertyTitle(title = stringResource(Localization.wallet)) },
                    content = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (wallet != null) {
                                Emoji(emoji = wallet.emoji)
                                Spacer(Modifier.width(6.dp))
                            }
                            MoonItemTitle(text = wallet?.name.orEmpty())
                        }
                    },
                )
            }
            properties.forEach { property ->
                MoonItemDivider()
                OperationRow {
                    MoonPropertyBigCell(
                        title = property.title,
                        value = property.value,
                        valueDescription = property.description,
                    )
                }
            }
            MoonItemDivider()
            OperationRow {
                MoonPropertyBigCell(
                    title = { MoonPropertyTitle(title = stringResource(Localization.network_fee)) },
                    content = {
                        MoonPropertyValue(
                            title = feeAmount,
                            subtitle = feeFiat,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun PrepareErrorMessage(text: String) {
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
    ) {
        MoonItemImage(image = image.assetImageUrl(), size = size)
    }
}

@Composable
private fun OperationRow(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.heightIn(min = ROW_HEIGHT),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

private val ROW_HEIGHT = 56.dp
private const val BADGE_RATIO = 20f / 44f
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
private fun OperationConfirmPreview(error: ConfirmationError?, priced: Boolean = true) {
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

private val PREVIEW_HEIGHT = 720.dp
