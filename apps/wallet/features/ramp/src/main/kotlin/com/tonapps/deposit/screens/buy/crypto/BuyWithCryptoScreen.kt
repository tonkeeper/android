package com.tonapps.deposit.screens.buy.crypto

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.core.helper.ClipboardManager
import com.tonapps.core.helper.rememberClipboardManager
import com.tonapps.core.helper.rememberShareManager
import com.tonapps.deposit.screens.method.RampAsset
import com.tonapps.deposit.screens.qr.QrContent
import com.tonapps.deposit.utils.formatDuration
import com.tonapps.mvi.props.observeSafeState
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Links
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonActionIcon
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonButtonCell
import ui.components.moon.cell.MoonButtonCellDefaults
import ui.components.moon.cell.MoonDescriptionCell
import ui.components.moon.cell.MoonInfoCell
import ui.components.moon.cell.MoonPropertyCell
import ui.components.moon.cell.MoonTextContentCell
import ui.components.moon.container.MoonCutRow
import ui.components.moon.container.MoonScaffold
import ui.components.moon.container.MoonSurface
import ui.painterResource
import ui.text.toAnnotatedString
import ui.text.withLink
import ui.theme.LocalAppColorScheme
import ui.theme.UIKit
import ui.theme.modifiers.simmerOn
import ui.utils.toRichSpanStyle

@Composable
fun BuyWithCryptoScreen(
    viewModel: BuyWithCryptoFeature,
    from: WalletCurrency,
    to: RampAsset,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.state.global.observeSafeState()

    BuyWithCryptoContent(
        state = state,
        from = from,
        to = to,
        onBack = onBack,
        onClose = onClose,
    )
}

@Composable
private fun BuyWithCryptoContent(
    state: BuyWithCryptoState,
    from: WalletCurrency,
    to: RampAsset,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val data = state as? BuyWithCryptoState.Data

    val payinAddress = data?.payinAddress ?: ""
    val payinAddressAnnotated = remember(payinAddress) { payinAddress.toAnnotatedString() }

    var isQrShown by remember { mutableStateOf(false) }

    if (isQrShown && payinAddress.isNotEmpty()) {
        QrDialog(
            address = payinAddress,
            tokenImage = from.iconUri ?: Uri.EMPTY,
            onClose = { isQrShown = false },
        )
    }

    MoonScaffold(
        Modifier.fillMaxSize()
            .navigationBarsPadding(),
        title = stringResource(Localization.deposit_send, from.code),
        onClose = onClose,
        onBack = onBack,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .nestedScroll(rememberNestedScrollInteropConnection())
                    .padding(bottom = 72.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val color = UIKit.colorScheme.accent.orange
                val prefix = stringResource(Localization.deposit_address_warning_prefix)
                val highlight = stringResource(Localization.deposit_address_warning_highlight)
                val suffix = stringResource(Localization.deposit_address_warning_suffix)
                val text = remember(prefix, highlight, suffix) {
                    buildAnnotatedString {
                        append(prefix)
                        withStyle(SpanStyle(color)) {
                            append(highlight)
                        }
                        append(suffix)
                    }
                }

                MoonInfoCell(text = text, maxLines = 4)

                Spacer(Modifier.height(32.dp))

                ExchangeCell(
                    from = from,
                    to = to.toCurrency,
                    rate = data?.rate,
                )

                Spacer(Modifier.height(16.dp))

                MoonBundleCell(
                    modifier = Modifier.simmerOn(data == null),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(Localization.deposit_address, from.code),
                                style = UIKit.typography.body2,
                                color = UIKit.colorScheme.text.secondary,
                            )

                            Spacer(Modifier.height(2.dp))

                            val displayAddress = remember(payinAddress) {
                                formatAddress(payinAddress)
                            }

                            Text(
                                text = displayAddress,
                                style = UIKit.typography.mono,
                                color = UIKit.colorScheme.text.primary,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val clipboard = rememberClipboardManager()
                            MoonAccentButton(
                                modifier = Modifier.weight(1f),
                                text = stringResource(Localization.copy_address),
                                icon = {
                                    MoonItemIcon(
                                        painter = painterResource(UIKitIcon.ic_copy_16),
                                        color = UIKit.colorScheme.icon.primary,
                                    )
                                }
                            ) { clipboard.copy(payinAddressAnnotated.toString()) }

                            MoonActionIcon(
                                painter = painterResource(id = UIKitIcon.ic_qr_code_16),
                                onClick = { isQrShown = true },
                                size = 48.dp,
                                tintColor = UIKit.colorScheme.icon.primary,
                                backgroundColor = UIKit.colorScheme.background.contentTint,
                                contentDescription = "Navigation"
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                MoonBundleCell(
                    modifier = Modifier.simmerOn(data == null, height = 56.dp),
                ) {
                    Column {
                        if (data?.minDeposit != null) {
                            MoonPropertyCell(
                                title = stringResource(Localization.min_amount_label),
                                value = "${data.minDeposit} ${from.code}",
                            )
                        }

                        if (data?.maxDeposit != null) {
                            MoonPropertyCell(
                                title = stringResource(Localization.max_amount_label),
                                value = "${data.maxDeposit} ${from.code}",
                            )
                        }

                        if (data?.network != null) {
                            MoonPropertyCell(
                                title = stringResource(Localization.network),
                                value = data.network,
                            )
                        }

                        if (data?.estimatedDurationSeconds != null) {
                            MoonPropertyCell(
                                title = stringResource(Localization.ramp_estimated_time),
                                value = formatDuration(data.estimatedDurationSeconds),
                            )
                        }
                    }
                }

                // TODO replace
                val style = UIKit.typography.body2
                val linkColor = UIKit.colorScheme.text.accent
                val termsText = stringResource(Localization.terms_of_service)
                val disclaimerTemplate = stringResource(Localization.deposit_changelly_disclaimer, termsText)
                MoonDescriptionCell(
                    text = remember(disclaimerTemplate, LocalAppColorScheme.current) {
                        buildAnnotatedString {
                            val termsStart = disclaimerTemplate.indexOf(termsText)
                            if (termsStart >= 0) {
                                append(disclaimerTemplate.substring(0, termsStart))
                                withLink(
                                    text = termsText,
                                    link = Links.ChangellyTerms,
                                    color = linkColor,
                                    style = style,
                                )
                                append(disclaimerTemplate.substring(termsStart + termsText.length))
                            } else {
                                append(disclaimerTemplate)
                            }
                        }
                    }
                )
            }

            MoonButtonCell(
                modifier = Modifier.align(Alignment.BottomCenter),
                text = stringResource(Localization.deposit_go_to_main),
                colors = MoonButtonCellDefaults.ButtonColorsSecondary
            ) { onClose() }
        }
    }
}

private fun formatAddress(address: String): String {
    if (address.isEmpty()) return ""
    val mid = address.length / 2
    return address.substring(0, mid) + "\n" + address.substring(mid)
}

@Composable
private fun ExchangeCell(
    from: WalletCurrency,
    to: WalletCurrency,
    rate: String?,
) {
    val headerStyle = UIKit.typography.h2
    val headerColor = UIKit.colorScheme.text.primary
    val headerSecondaryColor = UIKit.colorScheme.text.secondary

    val fromChainName = from.title
    val toChainName = to.title
    val sendLabel = stringResource(Localization.send_currency, from.code)
    val receiveLabel = stringResource(Localization.receive_currency, to.code)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MoonCutRow {
            MoonItemImage(size = 72.dp, image = from.iconUri?.toString())
            MoonItemImage(size = 72.dp, image = to.iconUri?.toString())
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = remember(LocalAppColorScheme.current, from, sendLabel) {
                buildAnnotatedString {
                    withStyle(headerStyle.toRichSpanStyle(color = headerColor)) {
                        append("$sendLabel ")
                    }

                    withStyle(headerStyle.toRichSpanStyle(color = headerSecondaryColor)) {
                        append(fromChainName)
                    }
                }
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
        )

        Text(
            text = remember(LocalAppColorScheme.current, to, receiveLabel) {
                buildAnnotatedString {
                    withStyle(headerStyle.toRichSpanStyle(color = headerColor)) {
                        append("$receiveLabel ")
                    }

                    withStyle(headerStyle.toRichSpanStyle(color = headerSecondaryColor)) {
                        append(toChainName)
                    }
                }
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            modifier = Modifier.simmerOn(rate == null, width = 56.dp),
            text = rate.orEmpty(),
            color = UIKit.colorScheme.text.secondary,
            style = UIKit.typography.body1
        )
    }
}

@Composable
private fun QrDialog(
    address: String,
    tokenImage: Uri,
    onClose: () -> Unit = {},
) {
    val addressAnnotated = remember(address) { address.toAnnotatedString() }
    val shareManager = rememberShareManager()
    val clipboard = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        dragHandle = null,
    ) {
        MoonSurface {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MoonTopAppBarSimple(
                    title = "",
                    navigationIconRes = UIKitIcon.ic_chevron_down_16,
                    onNavigationClick = onClose,
                    backgroundColor = Color.Transparent
                )

                MoonTextContentCell(
                    title = stringResource(Localization.deposit_payment_qr_code),
                    description = stringResource(Localization.deposit_scan_qr_description)
                )

                Spacer(Modifier.height(32.dp))

                QrContent(
                    walletType = WalletType.Default,
                    walletAddress = address,
                    content = address,
                    tokenImage = tokenImage,
                    blockchainImage = null,
                    onCopyClick = { clipboard.setText(addressAnnotated) },
                    onShareClick = { shareManager.share(address) }
                )
            }
        }
    }
}
