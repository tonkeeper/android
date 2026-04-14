package com.tonapps.tonkeeper.ui.screen.events.compose.details

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.contract.Blockchain
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.core.extensions.iconExternalUrl
import com.tonapps.extensions.withApproximately
import com.tonapps.extensions.withMinus
import com.tonapps.extensions.withPlus
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.api.shortAddress
import com.tonapps.tonkeeper.core.history.nameRes
import com.tonapps.tonkeeper.extensions.composeIcon
import com.tonapps.tonkeeper.extensions.copyWithToast
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.helper.DateHelper
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.events.compose.TxScope.decryptComment
import com.tonapps.tonkeeper.ui.screen.events.compose.details.state.UiState
import com.tonapps.tonkeeper.ui.screen.nft.NftScreen
import com.tonapps.tonkeeper.ui.screen.transaction.CommentReportDialog
import com.tonapps.uikit.color.accentGreenColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.wallet.data.events.ActionType
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.events.tx.model.TxActionBody
import com.tonapps.wallet.data.events.tx.model.TxEvent
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.rates.entity.RatesEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.SpamTransactionState
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.Plurals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ui.components.details.UiDetails
import ui.components.popup.ComposeActionItem
import java.util.Locale

class TxDetailsViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val tx: TxEvent,
    private val actionIndex: Int,
    private val accountRepository: AccountRepository,
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
    private val passcodeManager: PasscodeManager,
    private val eventsRepository: EventsRepository,
    private val collectiblesRepository: CollectiblesRepository,
    private val api: API,
) : BaseWalletVM(app) {

    private companion object {
        private const val ACCOUNT_NAME_ROW_ID = "account"
        private const val ADDRESS_ROW_ID = "address"
        private const val COMMENT_ROW_ID = "comment"
        private const val EXTRA_ROW_ID = "extra"
        private const val OPERATION_ROW_ID = "operation"
        private const val DESCRIPTION_ROW_ID = "description"
        private const val PROTOCOL_ROW_ID = "protocol"

        private const val NOT_SPAM_ACTION_ID = "not_spam"
        private const val REPORT_SPAM_ACTION_ID = "report_spam"
        private const val OPEN_EXPLORER_ACTION_ID = "open_explorer"
    }

    private val locale: Locale
        get() = settingsRepository.getLocale()

    private val isSimplePreview: Boolean
        get() = action.type == ActionType.Unknown

    private val currency: WalletCurrency
        get() = settingsRepository.currency

    private val action = tx.actions[actionIndex]
    private val primaryValue = action.primaryValue

    private val txId: String
        get() = tx.hash

    private val incomingValue: TxActionBody.Value?
        get() = action.amount.incoming

    private val outgoingValue: TxActionBody.Value?
        get() = action.amount.outgoing

    private val incomingFormatted: CharSequence?
        get() = incomingValue?.formattedFull?.withPlus

    private val outgoingFormatted: CharSequence?
        get() = outgoingValue?.formattedFull?.withMinus

    private val isUsdt: Boolean
        get() = primaryValue?.currency?.isUSDT == true

    private val title: CharSequence?
        get() {
            return if (isSimplePreview) {
                action.body.value
            } else {
                action.product?.title ?: (incomingFormatted ?: outgoingFormatted)
            }
        }

    private val localSpamState: SpamTransactionState
        get() = settingsRepository.getSpamStateTransaction(wallet.id, txId)

    private val remoteSpamState: UiState.Spam
        get() = if (tx.spam) {
            UiState.Spam.Spam
        } else if (action.isMaybeSpam) {
            UiState.Spam.Maybe
        } else {
            UiState.Spam.No
        }

    private val spam: UiState.Spam
        get() {
            val state = localSpamState
            return when (state) {
                SpamTransactionState.UNKNOWN -> remoteSpamState
                SpamTransactionState.SPAM -> UiState.Spam.Spam
                else -> UiState.Spam.No
            }
        }

    private val _uiActionItemsFlow = MutableStateFlow<List<ComposeActionItem>>(emptyList())
    val uiActionItemsFlow = _uiActionItemsFlow.asStateFlow()

    private val _uiStateFlow = MutableStateFlow(UiState.Data(
        hash = txId.shortAddress,
        imageUrl = action.product?.imageUrl,
        title = title,
        aboveTitle = if (incomingFormatted != null && outgoingFormatted != null) outgoingFormatted else null,
        subtitle = action.product?.subtitle,
        verifiedSubtitle = action.hasVerifiedNft,
        date = date(),
        icons = currencyIcons(),
        details = details(),
        spam = spam,
        warningText = getWarningStatus()
    ))

    val uiStateFlow = _uiStateFlow.asStateFlow()

    init {
        viewModelScope.launch { updateData() }
        eventsRepository.decryptedCommentFlow.collectFlow { updateData() }
    }

    private fun date(): String {
        if (isSimplePreview) {
            return formatDate()
        }
        return getString(action.type.nameRes) + " " + formatDate()
    }

    private fun getWarningStatus(): String? {
        return if (action.isFailed) {
            getString(Localization.failed)
        } else if (action.hasUnverifiedToken) {
            getString(Localization.unverified_token)
        } else if (action.hasUnverifiedNft) {
            getString(Localization.nft_unverified)
        } else {
            null
        }
    }

    private fun updateUiActionItems() {
        _uiActionItemsFlow.value = buildActionItems()
    }

    private suspend fun updateData() {
        val rates = ratesRepository.getRates(wallet.network, currency, action.tokens.map { it.address })
        val rateAmount = if (!isUsdt && primaryValue != null) {
            val value = rates.convert(primaryValue.currency.code, primaryValue.value)
            if (value.isPositive) {
                CurrencyFormatter.formatFiat(currency.symbol, value)
            } else {
                null
            }
        } else {
            null
        }

        _uiStateFlow.update { uiState ->
            uiState.copy(
                subtitle = rateAmount ?: uiState.subtitle,
                details = details(rates)
            )
        }

        updateUiActionItems()
    }

    private fun accountRow(
        account: TxActionBody.Account,
        sender: Boolean = false,
    ): List<UiDetails.Row> {
        val rows = mutableListOf<UiDetails.Row>()
        val key = getString(if (sender) Localization.sender else Localization.recipient)
        if (account.name.isNullOrBlank()) {
            rows.add(UiDetails.Row(
                id = ADDRESS_ROW_ID,
                key = key,
                value = account.userFriendlyAddress,
                clickable = true
            ))
        } else {
            account.name?.let {
                rows.add(UiDetails.Row(
                    id = ACCOUNT_NAME_ROW_ID,
                    key = key,
                    value = it,
                    clickable = true
                ))
            }
            rows.add(UiDetails.Row(
                id = ADDRESS_ROW_ID,
                key = getString(if (sender) Localization.sender_address else Localization.recipient_address),
                value = account.userFriendlyAddress,
                clickable = true
            ))
        }
        return rows
    }

    private fun extraDetails(extra: TxEvent.Extra, rates: RatesEntity?): UiDetails.Row? {
        when (extra) {
            is TxEvent.Extra.Battery -> {
                val charges = extra.charges
                return if (charges > 0) {
                    UiDetails.Row(
                        id = EXTRA_ROW_ID,
                        key = getString(Localization.fee),
                        value = context.resources.getQuantityString(
                            Plurals.battery_charges, charges, charges
                        )
                    )
                } else {
                    null
                }
            }
            is TxEvent.Extra.Refund -> {
                val rate = rates?.convertTON(extra.value)
                return UiDetails.Row(
                    id = EXTRA_ROW_ID,
                    key = getString(Localization.refund),
                    value = CurrencyFormatter.format(WalletCurrency.TON.symbol,extra.value).withApproximately,
                    secondaryValue = rate?.let {
                        CurrencyFormatter.formatFiat(currency.symbol, it).withApproximately
                    }
                )
            }
            is TxEvent.Extra.Fee -> {
                val rate = rates?.convertTON(extra.value)
                return UiDetails.Row(
                    id = EXTRA_ROW_ID,
                    key = getString(Localization.fee),
                    value = CurrencyFormatter.format(WalletCurrency.TON.symbol,extra.value).withApproximately,
                    secondaryValue = rate?.let {
                        CurrencyFormatter.formatFiat(currency.symbol, it).withApproximately
                    }
                )
            }
        }
    }

    private fun getDecryptedComment() = eventsRepository.getDecryptedComment(txId)

    private fun isCommentDecrypted() = getDecryptedComment() != null

    private fun getCommentText(): String? {
        val text = action.text
        return when (text) {
            is TxActionBody.Text.Encrypted -> getDecryptedComment()
            is TxActionBody.Text.Plain -> text.text
            else -> null
        }
    }

    private fun textDetails(text: TxActionBody.Text?): UiDetails.Row? {
        if (text is TxActionBody.Text.Encrypted) {
            val decryptedText = getDecryptedComment()
            if (!decryptedText.isNullOrBlank()) {
                return textDetails(TxActionBody.Text.Plain(decryptedText))
            }
        }

        return when (text) {
            is TxActionBody.Text.Plain -> {
                UiDetails.Row(
                    id = COMMENT_ROW_ID,
                    key = getString(Localization.comment),
                    value = text.text,
                    clickable = true
                )
            }
            is TxActionBody.Text.Encrypted -> {
                UiDetails.Row(
                    id = COMMENT_ROW_ID,
                    key = getString(Localization.comment),
                    value = getString(Localization.encrypted_comment),
                    iconLeft = context.composeIcon(UIKitIcon.ic_lock_16, context.accentGreenColor),
                    clickable = true,
                    spoiler = true
                )
            }
            else -> null
        }
    }

    private fun details(rates: RatesEntity? = null): UiDetails {
        val rows = buildList {
            if (isSimplePreview) {
                action.title?.let {
                    add(UiDetails.Row(
                        id = OPERATION_ROW_ID,
                        key = getString(Localization.operation),
                        value = it
                    ))
                }

                action.subtitle?.let {
                    add(UiDetails.Row(
                        id = DESCRIPTION_ROW_ID,
                        key = getString(Localization.description),
                        value = it
                    ))
                }
            }

            if (action.type == ActionType.DepositStake && action.account == null) {
                action.subtitle?.let {
                    add(UiDetails.Row(
                        id = PROTOCOL_ROW_ID,
                        key = getString(Localization.protocol),
                        value = it
                    ))
                }
            }

            if (action.isOut) {
                action.recipient?.let { addAll(accountRow(it)) }
            } else {
                action.sender?.let { addAll(accountRow(it, true)) }
            }
            extraDetails(tx.extra, rates)?.let(::add)
            if (spam == UiState.Spam.No) {
                textDetails(action.text)?.let(::add)
            }
        }
        return UiDetails(rows)
    }

    private fun formatDate() = DateHelper.formatTransactionDetailsTime(tx.timestamp.toLong(), locale)

    private fun currencyIcons(): List<UiState.Icon> {
        return action.tokens.mapNotNull { currency ->
            val iconUrl = currency.iconExternalUrl(context) ?: return@mapNotNull null
            val subicon = if (currency.isUSDT) {
                currency.chain.iconExternalUrl(context)
            } else null
            UiState.Icon(
                url = iconUrl,
                subicon = subicon
            )
        }.asReversed()
    }

    fun onClickDetailsRow(rowId: String) {
        when (rowId) {
            COMMENT_ROW_ID -> clickOnComment()
            ADDRESS_ROW_ID, ACCOUNT_NAME_ROW_ID -> copyAccount(rowId == ADDRESS_ROW_ID)
        }
    }

    fun onClickActionMenuItem(id: String) {
        when (id) {
            OPEN_EXPLORER_ACTION_ID -> openTx()
            NOT_SPAM_ACTION_ID -> markAsNotSpam()
            REPORT_SPAM_ACTION_ID -> markAsSpam()
        }
    }

    private fun clickOnComment() {
        val text = action.text ?: return
        if (text is TxActionBody.Text.Encrypted) {
            viewModelScope.launch {
                decryptComment()
            }
        } else if (text is TxActionBody.Text.Plain) {
            copyComment()
        }
    }

    private suspend fun decryptComment() = decryptComment(
        wallet = wallet,
        tx = tx,
        actionIndex = actionIndex,
        accountRepository = accountRepository,
        settingsRepository = settingsRepository,
        passcodeManager = passcodeManager,
        eventsRepository = eventsRepository
    )

    fun openTx() {
        val url = api.getConfig(wallet.network)
            .formatTransactionExplorer(wallet.testnet, tx.blockchain == Blockchain.TRON, txId)
        BrowserHelper.open(context, url)
    }

    fun copyTxHash() {
        context.copyWithToast(txId)
    }

    fun openNft() {
        val nftAddress = action.product?.id ?: return
        viewModelScope.launch {
            try {
                val nft = collectiblesRepository.getNft(
                    accountId = wallet.accountId,
                    network = wallet.network,
                    address = nftAddress
                ) ?: throw Throwable()
                openScreen(NftScreen.newInstance(wallet, nft))
            } catch (e: Throwable) {
                toast(Localization.unknown_error)
            }
        }
    }

    private fun copyAccount(onlyAddress: Boolean) {
        action.account?.let {
            if (onlyAddress) {
                context.copyWithToast(it.address.toUserFriendly(
                    wallet = it.isWallet,
                    testnet = it.testnet
                ))
            } else {
                val name = it.name
                if (!name.isNullOrBlank()) {
                    context.copyWithToast(name)
                }
            }
        }
    }

    private fun copyComment() {
        val text = (action.text as? TxActionBody.Text.Plain)?.text
        if (!text.isNullOrBlank()) {
            context.copyWithToast(text)
        }
    }

    fun reportSpam(spam: Boolean) {
        if (!spam) {
            markAsNotSpam()
        } else {
            markAsSpam()
        }
    }

    private fun markAsNotSpam() {
        _uiStateFlow.update {
            it.copy(spam = UiState.Spam.No)
        }

        viewModelScope.launch {
            settingsRepository.setSpamStateTransaction(wallet.id, txId, SpamTransactionState.NOT_SPAM)
            eventsRepository.removeSpam(wallet.accountId, wallet.network, txId)
            toast(Localization.tx_marked_as_not_spam)

            updateUiActionItems()
        }
    }

    private fun markAsSpam() {
        viewModelScope.launch {
            if (action.hasEncryptedText && !isCommentDecrypted()) {
                CommentReportDialog.show(context)
                if (!decryptComment()) {
                    return@launch
                }
            }

            loading(true)
            settingsRepository.setSpamStateTransaction(wallet.id, txId, SpamTransactionState.SPAM)
            val comment = getCommentText()
            try {
                api.reportTX(
                    txId = txId,
                    comment = comment,
                    recipient = wallet.accountId
                )
                eventsRepository.markAsSpam(wallet.accountId, wallet.network, txId)
                loading(false)
                toast(Localization.tx_marked_as_spam)
            } catch (ignored: Throwable) {
                loading(false)
                toast(Localization.unknown_error)
            }

            updateUiActionItems()
        }
    }

    private fun buildActionItems(): List<ComposeActionItem> {
        val actionItems = mutableListOf<ComposeActionItem>()
        if (!action.isOut && !wallet.testnet && !wallet.isWatchOnly) {
            if (spam == UiState.Spam.Spam) {
                actionItems.add(ComposeActionItem(
                    id = NOT_SPAM_ACTION_ID,
                    text = getString(Localization.not_spam),
                    icon = context.composeIcon(UIKitIcon.ic_block_16)
                ))
            } else if (spam == UiState.Spam.Maybe) {
                actionItems.add(ComposeActionItem(
                    id = REPORT_SPAM_ACTION_ID,
                    text = getString(Localization.report_spam),
                    icon = context.composeIcon(UIKitIcon.ic_block_16)
                ))
            }
        }

        val openExplorerText = if (tx.blockchain == Blockchain.TON) Localization.open_tonviewer else Localization.open_explorer

        actionItems.add(ComposeActionItem(
            id = OPEN_EXPLORER_ACTION_ID,
            text = getString(openExplorerText),
            icon = context.composeIcon(UIKitIcon.ic_globe_16)
        ))

        return actionItems.toList()
    }
}