package com.tonapps.deposit.multicoin.screens.send

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.core.helper.rememberClipboardManager
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.deposit.utils.rememberSendAmountState
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.num.BaseUnit
import com.tonapps.core.components.assetImageUrl
import com.tonapps.core.components.coinShortName
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.RStr
import ui.components.moon.MoonBadgeButton
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonTopAppBar
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonTextFieldCell
import ui.components.moon.container.MoonScaffold
import ui.theme.UIKit
import ui.utils.uppercased

@Composable
fun SendScreen(
    feature: SendFeature,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onShowError: (String) -> Unit = {},

    scannerResult: String? = null,
    onNavigateToScanner: () -> Unit = {},
    onAddressBook: ((onResult: (String) -> Unit) -> Unit)? = null,
    onContinue: (ConfirmRequest, SendTxInfo) -> Unit = { _, _ -> },
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    val account by feature.selectedAccount.collectAsState()
    val accountNotFound by feature.accountNotFound.collectAsState()
    if (accountNotFound) {
        AccountNotFoundContent(onClose = onClose, onBack = onBack)
        return
    }
    val safeAccount = account ?: return
    val amountState = rememberSendAmountState(safeAccount.asset.decimals)

    LaunchedEffect(scannerResult) {
        val value = scannerResult ?: return@LaunchedEffect
        feature.setAddress(value)
    }

    LaunchedEffect(Unit) {
        feature.events.collect { event ->
            when (event) {
                is SendEvent.UpdateAmount -> amountState.onValueChange(event.displayString)
                is SendEvent.Continue -> onContinue(event.request, event.txInfo)
                is SendEvent.ShowError -> onShowError(event.message)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        MoonScaffold(
            Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .nestedScroll(rememberNestedScrollInteropConnection()),
            topBar = {
                MoonTopAppBar(
                    title = "",
                    actionIconRes = UIKitIcon.ic_close_16,
                    onActionClick = onClose,
                    navigationIconRes = UIKitIcon.ic_chevron_left_16,
                    onNavigationClick = onBack,
                    ignoreSystemOffset = true,
                    showDivider = false,
                    backgroundColor = Color.Transparent,
                    content = {
                        Text(
                            text = "${stringResource(Localization.send)} ${safeAccount.asset.symbol}",
                            style = UIKit.typography.h3,
                            color = UIKit.colorScheme.text.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                )
            },
        ) {
            // Address input
            val address by feature.address.collectAsState()
            val addressLoading by feature.addressLoading.collectAsState()
            val addressStatus by feature.addressStatus.collectAsState()

            MoonTextFieldCell(
                value = address,
                onValueChange = { feature.setAddress(it) },
                hint = stringResource(Localization.address),
                modifier = Modifier.focusRequester(focusRequester),
                enabled = !feature.isAddressLocked,
                loading = addressLoading,
                isError = addressStatus is AddressStatus.Invalid || addressStatus is AddressStatus.Scam,
                keyboardOptions = remember { KeyboardOptions(imeAction = ImeAction.Next) },
                keyboardActions = remember {
                    KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                },
                maxLines = 2,
                trailingAction = if (feature.isAddressLocked) {
                    null
                } else {
                    {
                        if (address.isEmpty()) {
                            val clipboard = rememberClipboardManager()
                            MoonBadgeButton(
                                text = stringResource(Localization.paste),
                            ) {
                                clipboard.getText()?.let { feature.setAddress(it) }
                                focusManager.moveFocus(FocusDirection.Next)
                            }

                            MoonItemIcon(
                                painter = painterResource(UIKitIcon.ic_qr_viewfinder_outline_28),
                                onClick = {
                                    onNavigateToScanner()
                                    focusManager.clearFocus(true)
                                },
                                color = UIKit.colorScheme.accent.blue,
                            )

                            if (onAddressBook != null) {
                                MoonItemIcon(
                                    painter = painterResource(UIKitIcon.ic_address_book_28),
                                    color = UIKit.colorScheme.accent.blue,
                                    onClick = {
                                        focusManager.clearFocus(true)
                                        onAddressBook { selectedAddress ->
                                            feature.setAddress(selectedAddress)
                                            focusManager.moveFocus(FocusDirection.Next)
                                        }
                                    },
                                )
                            }
                        }
                    }
                },
            )

            if (addressStatus is AddressStatus.Invalid || addressStatus is AddressStatus.Scam) {
                MoonItemSubtitle(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    text = when (addressStatus) {
                        is AddressStatus.Scam -> stringResource(Localization.scam_address_error)
                        else -> stringResource(Localization.invalid_address)
                    },
                    color = UIKit.colorScheme.accent.red,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Amount input
            val tokenImageUrl = safeAccount.asset.assetImageUrl()
            val inputInFiat by feature.inputInFiat.collectAsState()
            val fiatCode = safeAccount.rate?.currencyCode

            MoonTextFieldCell(
                value = amountState.textFieldValue,
                onValueChange = {
                    amountState.onTextFieldValueChange(it)
                    feature.setAmount(amountState.value)
                },
                hint = stringResource(Localization.amount),
                singleLine = true,
                disableClearButton = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                ),
                suffix = if (inputInFiat && fiatCode != null) {
                    { MoonItemSubtitle(text = fiatCode, maxLines = 1) }
                } else {
                    null
                },
                trailingAction = {
                    MoonBadgeButton(
                        content = {
                            MoonItemImage(
                                modifier = Modifier.size(24.dp),
                                image = tokenImageUrl,
                            )
                            val asset = safeAccount.asset.value
                            MoonItemTitle(text = asset.symbol)
                            val shortName = asset.coinShortName()
                            if (shortName != null) {
                                MoonItemSubtitle(text = shortName)
                            }
                        },
                    )
                },
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            )

            // Balance & fiat row
            val enteredAmount by feature.enteredAmount.collectAsState()
            val amountError by feature.amountError.collectAsState()
            val rate = safeAccount.rate
            val balance = safeAccount.displayBalance

            // Converted amount: token value when entering fiat, fiat value otherwise.
            val tokenValue = enteredAmount ?: BaseUnit(BigInteger.ZERO, safeAccount.asset.value.decimals)
            val convertedText = remember(tokenValue, inputInFiat, rate, safeAccount.asset.symbol) {
                if (inputInFiat) {
                    "${Formatter.formatShort(value = tokenValue)} ${safeAccount.asset.symbol}"
                } else {
                    rate?.let { Formatter.formatFiat(value = tokenValue, rate = it.value, separator = " ") } ?: ""
                }
            }

            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Left: converted amount + currency swap toggle
                Row(
                    modifier = Modifier.clickable(enabled = rate != null) { feature.swap() },
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MoonItemSubtitle(
                        text = convertedText,
                        color = UIKit.colorScheme.text.secondary,
                    )

                    if (rate != null) {
                        MoonItemIcon(
                            painter = painterResource(UIKitIcon.ic_swap_vertical_16),
                            size = 16.dp,
                        )
                    }
                }

                // Right: remaining balance + Max
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (amountError is AmountError.InsufficientBalance) {
                        MoonItemSubtitle(
                            text = stringResource(Localization.insufficient_balance),
                            color = UIKit.colorScheme.accent.red,
                        )
                    } else {
                        val formattedBalance = remember(balance, safeAccount.asset.symbol) {
                            "${balance.fmt()} ${safeAccount.asset.symbol}"
                        }
                        MoonItemSubtitle(
                            text = stringResource(Localization.balance_prefix, formattedBalance),
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            color = UIKit.colorScheme.text.secondary,
                        )
                    }

                    if (amountError !is AmountError.InsufficientBalance) {
                        MoonItemSubtitle(
                            text = stringResource(Localization.max).uppercased(),
                            color = UIKit.colorScheme.text.accent,
                            modifier = Modifier.clickable { feature.setMax() },
                        )
                    }
                }
            }

            // Comment input
            Spacer(Modifier.height(16.dp))

            val comment by feature.comment.collectAsState()
            val memoRequired = (addressStatus as? AddressStatus.Valid)?.memoRequired == true

            MoonTextFieldCell(
                value = comment,
                onValueChange = { feature.setComment(it) },
                hint = if (memoRequired) {
                    stringResource(Localization.required_comment)
                } else {
                    stringResource(Localization.comment)
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )

            if (memoRequired) {
                MoonItemSubtitle(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    text = stringResource(Localization.send_request_comment),
                    color = UIKit.colorScheme.accent.orange,
                    maxLines = 3,
                )
            }

            if (comment.isNotEmpty()) {
                Box(Modifier.padding(16.dp, 12.dp)) {
                    MoonItemSubtitle(stringResource(RStr.comment_decrypted_hint))
                }
            }

            LaunchedEffect(Unit) {
                if (!feature.isAddressLocked) {
                    focusRequester.requestFocus()
                }
            }

            Spacer(Modifier.weight(1f))

            // Continue button
            val continueEnabled by feature.continueEnabled.collectAsState()

            MoonBottomButtonCell(
                text = stringResource(Localization.continue_action),
                enabled = continueEnabled,
            ) {
                feature.onContinue()
            }
        }
    }
}

@Composable
private fun AccountNotFoundContent(onClose: () -> Unit, onBack: () -> Unit) {
    MoonScaffold(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        topBar = {
            MoonTopAppBar(
                title = "",
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
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Localization.not_found),
                style = UIKit.typography.h3,
                color = UIKit.colorScheme.text.secondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
