package com.tonapps.deposit.screens.send

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.core.deeplink.DeepLinkRoute
import com.tonapps.core.extensions.iconExternalUrl
import com.tonapps.core.helper.rememberClipboardManager
import com.tonapps.deposit.utils.AddressError
import com.tonapps.deposit.utils.rememberSendAddressState
import com.tonapps.deposit.utils.rememberSendAmountState
import com.tonapps.deposit.utils.rememberSendCommentState
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.mvi.props.observeSafeState
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ui.components.moon.MoonBadgeButton
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonTopAppBar
import ui.components.moon.cell.MoonButtonCell
import ui.components.moon.cell.MoonTextFieldCell
import ui.components.moon.container.MoonScaffold
import ui.preview.ThemedPreview
import ui.theme.UIKit
import ui.utils.uppercased
import java.math.RoundingMode

@Composable
fun SendScreen(
    feature: SendFeature,
    scannerResult: String? = null,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onAddressBook: ((onResult: (String) -> Unit) -> Unit)? = null,
    onNavigateToConfirm: (SendParams) -> Unit = {},
    onShowError: (String) -> Unit = {},
    onNavigateToTokenPicker: (selectedTokenAddress: String?) -> Unit = {},
    onNavigateToScanner: () -> Unit = {},
) {
    val state by feature.state.global.observeSafeState()

    SendInputContent(
        state = state,
        events = feature.events,
        scannerResult = scannerResult,
        onClose = onClose,
        onBack = onBack,
        onAddressBook = onAddressBook,
        onAddressInput = { feature.sendAction(SendModel.Action.AddressInput(it)) },
        onAmountInput = { feature.sendAction(SendModel.Action.AmountInput(it)) },
        onCommentInput = { feature.sendAction(SendModel.Action.CommentInput(it)) },
        onSetMax = { feature.sendAction(SendModel.Action.SetMax) },
        onSwap = { feature.sendAction(SendModel.Action.Swap) },
        onEncryptedCommentToggle = { feature.sendAction(SendModel.Action.EncryptedCommentToggle(it)) },
        onSelectTokenByAddress = { feature.sendAction(SendModel.Action.SelectTokenByAddress(it)) },
        onContinue = { feature.sendAction(SendModel.Action.Continue) },
        onNavigateToConfirm = onNavigateToConfirm,
        onNavigateToTokenPicker = onNavigateToTokenPicker,
        onNavigateToScanner = onNavigateToScanner,
        onShowError = onShowError,
    )
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
private fun SendInputContent(
    state: SendModel.State,
    events: Flow<SendModel.Event>,
    scannerResult: String? = null,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onAddressBook: ((onResult: (String) -> Unit) -> Unit)? = null,
    onAddressInput: (String) -> Unit,
    onAmountInput: (String) -> Unit,
    onCommentInput: (String?) -> Unit,
    onSetMax: () -> Unit,
    onSwap: () -> Unit,
    onEncryptedCommentToggle: (Boolean) -> Unit,
    onSelectTokenByAddress: (String) -> Unit,
    onContinue: () -> Unit,
    onNavigateToConfirm: (SendParams) -> Unit,
    onShowError: (String) -> Unit,
    onNavigateToTokenPicker: (selectedTokenAddress: String?) -> Unit,
    onNavigateToScanner: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboardManager = LocalSoftwareKeyboardController.current

    val addressState = rememberSendAddressState(
        destination = state.destination,
        isResolving = state.isResolvingAddress,
    )

    // Pre-fill locked address
    LaunchedEffect(state.isAddressLocked, state.address) {
        if (state.isAddressLocked && state.address.isNotEmpty() && addressState.text.isEmpty()) {
            addressState.onTextChange(state.address)
        }
    }

    val commentState = rememberSendCommentState(
        isLedger = state.isLedger,
    )

    val amountDecimals = if (state.amountCurrency) Coins.DEFAULT_DECIMALS else state.selectedToken.decimals
    val amountState = rememberSendAmountState(amountDecimals)

    val expiredMessage = stringResource(Localization.expired_link)
    LaunchedEffect(scannerResult) {
        val value = scannerResult ?: return@LaunchedEffect

        val route = try {
            DeepLinkRoute.resolve(value.toUri())
        } catch (_: Throwable) {
            null
        }

        val transfer = route as? DeepLinkRoute.Transfer
        if (transfer != null) {
            if (transfer.isExpired) {
                onShowError(expiredMessage)
                return@LaunchedEffect
            }

            val address = transfer.address
            addressState.onTextChange(address)
            onAddressInput(address)

            val tokenDecimals = transfer.jettonAddress?.let { jettonAddr ->
                onSelectTokenByAddress(jettonAddr)
                state.availableTokens
                    .firstOrNull { it.address.equalsAddress(jettonAddr) }
                    ?.decimals
            } ?: Coins.DEFAULT_DECIMALS
            transfer.amount?.let {
                amountState.setAmount(Coins.ofNano(it.toString(), tokenDecimals))
                onAmountInput(amountState.value)
            }
            transfer.text?.let {
                commentState.onTextChange(it)
                onCommentInput(it)
            }
        }
    }

    LaunchedEffect(Unit) {
        events.collect { event ->
            when (event) {
                is SendModel.Event.UpdateAmount -> amountState.setAmount(event.amount)
                is SendModel.Event.ClearAmount -> amountState.clear()
                is SendModel.Event.NavigateToConfirm -> onNavigateToConfirm(event.params)
                is SendModel.Event.ShowError -> onShowError(event.message)
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
                val symbol = state.selectedToken.symbol
                val isToken = !state.selectedToken.isTon && !state.selectedToken.isTrx
                val blockchainId = state.selectedToken.blockchain.id
                val secondaryColor = UIKit.colorScheme.text.secondary

                val titleText = if (state.isExchangeMode) {
                    val receiveSymbol = state.exchangeAsset?.symbol.orEmpty()
                    val receiveType = state.exchangeAsset?.tokenType?.fmt
                    remember(receiveSymbol, receiveType, secondaryColor) {
                        buildAnnotatedString {
                            append("Receive $receiveSymbol")
                            if (receiveType != null) {
                                append(" ")
                                withStyle(SpanStyle(color = secondaryColor)) {
                                    append(receiveType)
                                }
                            }
                        }
                    }
                } else {
                    val sendText = stringResource(Localization.send)
                    remember(sendText, symbol, isToken, blockchainId, secondaryColor) {
                        buildAnnotatedString {
                            append("$sendText $symbol")
                            if (isToken) {
                                append(" ")
                                withStyle(SpanStyle(color = secondaryColor)) {
                                    append(blockchainId)
                                }
                            }
                        }
                    }
                }

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
                            text = titleText,
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
            val addressStr = stringResource(Localization.address)
            val addressHintStr = stringResource(Localization.address_hint)
            // Address input
            MoonTextFieldCell(
                value = addressState.text,
                onValueChange = {
                    addressState.onTextChange(it)
                    onAddressInput(it)
                },
                hint = remember(state.exchangeAsset, state.selectedToken.symbol, addressStr, addressHintStr) {
                    state.exchangeAsset
                        ?.let { "$addressStr ${it.symbol} ${it.tokenType?.fmt.orEmpty()}" }
                        ?: addressHintStr
                },
                modifier = Modifier.focusRequester(focusRequester),
                enabled = !state.isAddressLocked,
                isError = addressState.isError,
                loading = addressState.isResolving,
                keyboardOptions = remember { KeyboardOptions(imeAction = ImeAction.Next) },
                keyboardActions = remember {
                    KeyboardActions(onNext = {
                        focusManager.moveFocus(
                            FocusDirection.Down
                        )
                    })
                },
                maxLines = 2,
                trailingAction = if (state.isAddressLocked) null else {
                    {
                        if (addressState.text.isEmpty()) {
                            val clipboard = rememberClipboardManager()
                            MoonBadgeButton(
                                text = stringResource(Localization.paste),
                            ) {
                                clipboard.getText()?.let {
                                    addressState.onTextChange(it)
                                    onAddressInput(it)
                                }

                                focusManager.moveFocus(FocusDirection.Next)
                            }

                            if (!state.isExchangeMode) {
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
                                                addressState.onTextChange(selectedAddress)
                                                onAddressInput(selectedAddress)
                                                focusManager.moveFocus(FocusDirection.Next)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
            )

            // Address error
            if (addressState.isError) {
                val addressError = addressState.error
                MoonItemSubtitle(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    text = when (addressError) {
                        is AddressError.NotFound -> stringResource(Localization.invalid_address)
                        is AddressError.Scam -> stringResource(Localization.scam_address_error)
                        is AddressError.TokenMismatch -> stringResource(
                            Localization.send_wrong_blockchain,
                            addressError.addressBlockchain.id,
                        )
                        null -> ""
                    },
                    color = UIKit.colorScheme.accent.red,
                )

                // Swap link for USDT blockchain mismatch
                if (addressError is AddressError.TokenMismatch &&
                    (addressError.selectedToken.isUsdt || addressError.selectedToken.isUsdtTrc20) &&
                    !state.tronSwapUrl.isNullOrEmpty()
                ) {
                    val uriHandler = LocalUriHandler.current
                    val fromNetwork = state.selectedToken.blockchain.id
                    val toNetwork = addressError.addressBlockchain.id
                    val swapTitle = state.tronSwapTitle.orEmpty()
                    val accentBlue = UIKit.colorScheme.accent.blue
                    val swapFullText = stringResource(
                        Localization.send_wrong_blockchain_swap,
                        fromNetwork,
                        toNetwork,
                        swapTitle,
                    )
                    val swapAnnotated = remember(swapFullText, swapTitle, accentBlue) {
                        buildAnnotatedString {
                            val titleStart = swapFullText.indexOf(swapTitle)
                            if (titleStart >= 0) {
                                append(swapFullText.substring(0, titleStart))
                                withStyle(SpanStyle(color = accentBlue)) {
                                    append(swapTitle)
                                }
                                append(swapFullText.substring(titleStart + swapTitle.length))
                            } else {
                                append(swapFullText)
                            }
                        }
                    }
                    MoonItemSubtitle(
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .clickable { uriHandler.openUri(state.tronSwapUrl) },
                        text = swapAnnotated,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (!state.isNft) {
                // Amount input
                MoonTextFieldCell(
                    value = amountState.textFieldValue,
                    onValueChange = {
                        amountState.onTextFieldValueChange(it)
                        onAmountInput(amountState.value)
                    },
                    hint = stringResource(Localization.amount),
                    singleLine = true,
                    disableClearButton = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    trailingAction = {
                        val secondaryColor = UIKit.colorScheme.text.secondary
                        val badgeText = remember(state.exchangeAsset, state.selectedToken, secondaryColor) {
                            if (state.exchangeAsset != null) {
                                buildAnnotatedString {
                                    append(state.exchangeAsset.symbol)
                                    val type = state.exchangeAsset.tokenType
                                    if (type != null) {
                                        append(" ")
                                        withStyle(SpanStyle(color = secondaryColor)) {
                                            append(type.fmt)
                                        }
                                    }
                                }
                            } else {
                                buildAnnotatedString {
                                    append(state.selectedToken.symbol)
                                    val type = state.selectedToken.token.tokenType
                                    if (type != null) {
                                        append(" ")
                                        withStyle(SpanStyle(color = secondaryColor)) {
                                            append(type.fmt)
                                        }
                                    }
                                }
                            }
                        }

                        val context = LocalContext.current
                        MoonBadgeButton(
                            content = {
                                if (state.exchangeAsset != null) {
                                    MoonItemImage(
                                        modifier = Modifier.size(24.dp),
                                        image = state.exchangeAsset.iconExternalUrl(context).toString(),
                                    )
                                } else {
                                    MoonItemImage(
                                        modifier = Modifier.size(24.dp),
                                        image = state.selectedToken.imageUri.toString(),
                                    )
                                }

                                MoonItemTitle(text = badgeText)
                                if (state.availableTokens.size > 1 && !state.isExchangeMode) {
                                    MoonItemIcon(
                                        painter = painterResource(UIKitIcon.ic_switch_16),
                                        color = UIKit.colorScheme.icon.primary
                                    )
                                }
                            },
                            onClick = if (state.availableTokens.size > 1 && !state.isExchangeMode) {
                                {
                                    focusManager.clearFocus(true)
                                    keyboardManager?.hide()
                                    onNavigateToTokenPicker(state.selectedToken.address)
                                }
                            } else {
                                null
                            },
                        )
                    },
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                )

                // Balance and exchange rate row
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Converted amount
                    val convertedCode = if (state.amountCurrency) {
                        state.selectedToken.symbol
                    } else {
                        state.currency.code
                    }
                    val convertedFormatted = remember(state.convertedAmount, convertedCode) {
                        val value = CurrencyFormatter.format(
                            value = state.convertedAmount,
                            roundingMode = RoundingMode.DOWN,
                        )
                        if (state.amountCurrency) {
                            "$value $convertedCode"
                        } else {
                            "$convertedCode $value"
                        }
                    }
                    if (state.isExchangeMode) {
                        MoonItemSubtitle(
                            text = "1 ${state.selectedToken.symbol} = 1 ${state.exchangeAsset?.symbol.orEmpty()}",
                        )
                    } else {
                        Row(
                            modifier = Modifier.clickable { onSwap() },
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MoonItemSubtitle(text = convertedFormatted)
                            MoonItemIcon(
                                painter = painterResource(UIKitIcon.ic_swap_vertical_16),
                                size = 16.dp,
                            )
                        }
                    }

                    // Balance + Max
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (state.hiddenBalance) {
                            MoonItemSubtitle(text = HIDDEN_BALANCE)
                        } else if (state.insufficientBalance) {
                            MoonItemSubtitle(
                                text = stringResource(Localization.insufficient_balance),
                                color = UIKit.colorScheme.accent.red,
                            )
                        } else {
                            val remainingFormatted = CurrencyFormatter.format(
                                currency = state.selectedToken.symbol,
                                value = state.remainingTokenBalance,
                                roundingMode = RoundingMode.DOWN,
                                replaceSymbol = false,
                            )
                            MoonItemSubtitle(
                                text = stringResource(
                                    Localization.remaining_balance,
                                    remainingFormatted
                                ),
                                color = UIKit.colorScheme.text.secondary,
                            )
                        }

                        if (!state.insufficientBalance) {
                            MoonItemSubtitle(
                                text = stringResource(Localization.max).uppercased(),
                                color = UIKit.colorScheme.text.accent,
                                modifier = Modifier.clickable { onSetMax() },
                            )
                        }
                    }
                }

                // Exchange min/max restriction
                if (state.isExchangeMode) {
                    val exchangeError =
                        remember(state.amount, state.exchangeMinAmount, state.exchangeMaxAmount) {
                            when {
                                state.exchangeMinAmount != null && state.amount.isPositive && state.amount < state.exchangeMinAmount -> state.exchangeMinAmount
                                    .let { CurrencyFormatter.format(value = it).toString() }
                                    .let { "min" to it }

                                state.exchangeMaxAmount != null && state.amount.isPositive && state.amount > state.exchangeMaxAmount -> state.exchangeMaxAmount
                                    .let { CurrencyFormatter.format(value = it).toString() }
                                    .let { "max" to it }

                                else -> null
                            }
                        }
                    if (exchangeError != null) {
                        MoonItemSubtitle(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            text = if (exchangeError.first == "min") {
                                stringResource(Localization.min_amount, exchangeError.second)
                            } else {
                                stringResource(Localization.max_amount, exchangeError.second)
                            },
                            color = UIKit.colorScheme.accent.red,
                        )
                    }
                }
            } // end if (!state.isNft)

            // Comment input
            if (state.isCommentAvailable) {
                Spacer(Modifier.height(16.dp))

                val isEncrypted = state.isCommentEncrypted
                val hasCommentText = commentState.text.isNotBlank()

                MoonTextFieldCell(
                    value = commentState.text,
                    onValueChange = {
                        commentState.onTextChange(it)
                        onCommentInput(it)
                    },
                    hint = when {
                        state.isMemoRequired -> stringResource(Localization.required_comment)
                        isEncrypted -> stringResource(Localization.encrypted_comment)
                        else -> stringResource(Localization.comment)
                    },
                    isError = commentState.isError,
                    hintColor = if (isEncrypted && hasCommentText) {
                        UIKit.colorScheme.accent.green
                    } else {
                        null
                    },
                    activeBorderColor = if (isEncrypted) {
                        UIKit.colorScheme.accent.green
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                )

                // Comment error
                if (commentState.isError) {
                    MoonItemSubtitle(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        text = stringResource(Localization.ledger_comment_error),
                        color = UIKit.colorScheme.accent.red,
                        maxLines = 3,
                    )
                }

                // Memo required indicator
                if (state.isMemoRequired) {
                    MoonItemSubtitle(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        text = stringResource(Localization.send_request_comment),
                        color = UIKit.colorScheme.accent.orange,
                        maxLines = 3,
                    )
                }

                // Encryption toggle hint
                if (state.encryptedCommentAvailable && hasCommentText) {
                    CommentEncryptionHint(
                        isEncrypted = isEncrypted,
                        onToggle = { onEncryptedCommentToggle(!isEncrypted) },
                    )
                }
            }

            DisposableEffect(Unit) {
                if (!state.isAddressLocked) {
                    focusRequester.requestFocus()
                }
                onDispose {
                    focusRequester.freeFocus()
                }
            }

            Spacer(Modifier.weight(1f))

            MoonButtonCell(
                text = stringResource(Localization.continue_action),
                enabled = state.isContinueEnabled && !state.isProcessing,
            ) {
                onContinue()
            }
        }
    }
}

@Composable
private fun CommentEncryptionHint(
    isEncrypted: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .fillMaxWidth(),
    ) {
        MoonItemSubtitle(
            text = if (isEncrypted) {
                stringResource(Localization.comment_encrypted_hint)
            } else {
                stringResource(Localization.comment_decrypted_hint)
            },
        )

        MoonItemSubtitle(
            text = if (isEncrypted) {
                stringResource(Localization.decrypt_comment)
            } else {
                stringResource(Localization.encrypt_comment)
            },
            color = UIKit.colorScheme.accent.blue,
            modifier = Modifier.clickable(onClick = onToggle),
        )
    }
}

@Preview
@Composable
private fun SendScreenPreview() {
    ThemedPreview {
        SendInputContent(
            state = SendModel.State(),
            events = flowOf(),
            onClose = {},
            onBack = {},
            onAddressBook = null,
            onAddressInput = {},
            onAmountInput = {},
            onCommentInput = {},
            onSetMax = {},
            onSwap = {},
            onEncryptedCommentToggle = {},
            onSelectTokenByAddress = {},
            onContinue = {},
            onNavigateToConfirm = {},
            onNavigateToTokenPicker = {},
            onShowError = {}
        )
    }
}
