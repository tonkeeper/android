package com.tonapps.tonkeeper.ui.screen.transaction

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.lifecycle.lifecycleScope
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.extensions.logError
import com.tonapps.extensions.max24
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.api.shortAddress
import com.tonapps.tonkeeper.core.history.ActionType
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.core.history.nameRes
import com.tonapps.tonkeeper.extensions.copyWithToast
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.extensions.toastLoading
import com.tonapps.tonkeeper.extensions.withVerificationIcon
import com.tonapps.tonkeeper.popup.ActionSheet
import com.tonapps.tonkeeper.ui.screen.token.unverified.TokenUnverifiedScreen
import com.tonapps.tonkeeper.view.TransactionDetailView
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentGreenColor
import com.tonapps.uikit.color.textPrimaryColor
import com.tonapps.uikit.color.textTertiaryColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.SpamTransactionState
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.dialog.modal.ModalDialog
import uikit.extensions.dp
import uikit.extensions.drawable
import uikit.extensions.reject
import uikit.extensions.setColor
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.FrescoView


class TransactionScreen: BaseFragment(R.layout.dialog_transaction), BaseFragment.Modal {

    override val fragmentName: String = "TransactionScreen"

    companion object {

        private const val REPORT_SPAM_ID = 1L
        private const val NOT_SPAM_ID = 2L
        private const val OPEN_EXPLORER_ID = 3L

        private const val ARG_ACTION = "action_event"

        fun newInstance(action: HistoryItem.Event): TransactionScreen {
            val screen = TransactionScreen()
            screen.putParcelableArg(ARG_ACTION, action)
            return screen
        }
    }

    private val viewModel: TransactionViewModel by viewModel()

    private val settingsRepository: SettingsRepository by inject()

    private val actionArgs: HistoryItem.Event? by lazy {
        requireArguments().getParcelableCompat(ARG_ACTION)
    }

    private var localIsScam: Boolean? = null

    private val isScam: Boolean
        get() = (localIsScam ?: actionArgs?.isScam) == true

    private val comment: String?
        get() = actionArgs?.let { action ->
            viewModel.getComment(action.txId) ?: action.comment?.body
        }

    private val historyHelper: HistoryHelper by inject()

    private lateinit var iconView: FrescoView
    private lateinit var moreView: View
    private lateinit var iconSwapView: View
    private lateinit var iconSwap1View: FrescoView
    private lateinit var iconSwap2View: FrescoView
    private lateinit var spamView: View
    private lateinit var amountView: AppCompatTextView
    private lateinit var currencyView: AppCompatTextView
    private lateinit var dateView: AppCompatTextView
    private lateinit var dataView: LinearLayoutCompat
    private lateinit var feeView: TransactionDetailView
    private lateinit var commentView: TransactionDetailView
    private lateinit var accountNameView: TransactionDetailView
    private lateinit var accountAddressView: TransactionDetailView
    private lateinit var explorerButton: AppCompatTextView
    private lateinit var unverifiedView: View
    private lateinit var amount2View: AppCompatTextView
    private lateinit var reportSpamButton: Button
    private lateinit var notSpamButton: Button

    private val lockDrawable: Drawable by lazy {
        val drawable = requireContext().drawable(UIKitIcon.ic_lock_16)
        drawable.setTint(requireContext().accentGreenColor)
        drawable
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.close).setOnClickListener {
            finish()
        }

        moreView = view.findViewById(R.id.more)

        iconView = view.findViewById(R.id.icon)
        iconSwapView = view.findViewById(R.id.icon_swap)

        iconSwap1View = view.findViewById(R.id.icon_swap1)
        iconSwap2View = view.findViewById(R.id.icon_swap2)

        amountView = view.findViewById(R.id.amount)
        spamView = view.findViewById(R.id.spam)

        amount2View = view.findViewById(R.id.amount2)

        currencyView = view.findViewById(R.id.currency)
        dateView = view.findViewById(R.id.date)
        unverifiedView = view.findViewById(R.id.unverified)
        unverifiedView.setOnClickListener {
            navigation?.add(TokenUnverifiedScreen.newInstance())
        }

        dataView = view.findViewById(R.id.data)

        feeView = view.findViewById(R.id.fee)

        commentView = view.findViewById(R.id.comment)
        commentView.title = getString(Localization.comment)

        accountNameView = view.findViewById(R.id.account_name)
        accountAddressView = view.findViewById(R.id.account_address)

        explorerButton = view.findViewById(R.id.open_explorer)

        reportSpamButton = view.findViewById(R.id.report_spam)
        notSpamButton = view.findViewById(R.id.not_spam)

        if (actionArgs != null) {
            initArgs(actionArgs!!)
        } else {
            finish()
        }
    }

    private fun initArgs(actionArgs: HistoryItem.Event) {
        spamView.visibility = if (isScam) View.VISIBLE else View.GONE
        feeView.title = if (actionArgs.refund == null) getString(Localization.fee) else getString(Localization.refund)
        unverifiedView.visibility = if (actionArgs.unverifiedToken) {
            View.VISIBLE
        } else {
            View.GONE
        }
        moreView.setOnClickListener { openMore(it, actionArgs) }

        reportSpamButton.setOnClickListener { reportSpamWithDialog(true, actionArgs) }
        explorerButton.setOnClickListener { openTonViewer(actionArgs) }
        notSpamButton.setOnClickListener { reportSpamWithDialog(false, actionArgs) }

        if (actionArgs.hiddenBalance) {
            amountView.text = HIDDEN_BALANCE
            feeView.setData(HIDDEN_BALANCE, HIDDEN_BALANCE)
        } else {
            amountView.text = actionArgs.value.withCustomSymbol(requireContext())
            if (actionArgs.refund != null) {
                feeView.setData(actionArgs.refund!!.withCustomSymbol(requireContext()), actionArgs.refundInCurrency!!.withCustomSymbol(requireContext()))
            } else if (actionArgs.fee != null) {
                feeView.setData(actionArgs.fee!!.withCustomSymbol(requireContext()), actionArgs.feeInCurrency!!.withCustomSymbol(requireContext()))
            } else {
                feeView.visibility = View.GONE
            }
        }

        if (actionArgs.comment != null && !isScam) {
            applyComment(actionArgs, actionArgs.comment!!)
        } else {
            commentView.visibility = View.GONE
            feeView.position = ListCell.Position.LAST
        }

        applyAccount(actionArgs.isOut, actionArgs.account?.address, actionArgs.account?.name)
        applyCurrency(actionArgs.currency, actionArgs.hiddenBalance)
        applyDate(actionArgs.action, actionArgs.dateDetails)

        explorerButton.setOnLongClickListener {
            requireContext().copyWithToast(actionArgs.txId)
            true
        }

        val txHashText = getString(Localization.transaction) + " " + actionArgs.txId.substring(0, 8)
        val txHashSpannable = SpannableString(txHashText)
        txHashSpannable.setColor(
            requireContext().textTertiaryColor,
            getString(Localization.transaction).length,
            txHashText.length
        )
        explorerButton.text = txHashSpannable

        if (isScam) {
            iconView.visibility = View.GONE
            iconSwapView.visibility = View.GONE
        } else if (actionArgs.isSwap) {
            iconView.visibility = View.GONE
            iconSwap1View.setImageURI(Uri.parse(actionArgs.coinIconUrl), this)
            iconSwap2View.setImageURI(Uri.parse(actionArgs.coinIconUrl2), this)
            amount2View.visibility = View.VISIBLE
            amount2View.text = actionArgs.value2.withCustomSymbol(requireContext())
            accountAddressView.title = getString(Localization.recipient_address)
        } else if (actionArgs.hasNft) {
            iconSwapView.visibility = View.GONE
            val nft = actionArgs.nft!!
            iconView.setImageURI(nft.mediumUri, null)
            iconView.setRound(16f.dp)
            currencyView.visibility = View.VISIBLE
            if (nft.verified) {
                currencyView.text = nft.collectionName.withVerificationIcon(requireContext())
            } else {
                currencyView.text = nft.collectionName
            }
            amount2View.visibility = View.VISIBLE
            amount2View.text = nft.name
            amount2View.setTextColor(requireContext().textPrimaryColor)
        } else if (actionArgs.coinIconUrl.isNotBlank()) {
            iconSwapView.visibility = View.GONE
            iconView.setImageURI(Uri.parse(actionArgs.coinIconUrl), null)
        } else {
            iconSwapView.visibility = View.GONE
            iconView.visibility = View.GONE
        }

        applySpamState(actionArgs)
        updateDataView()
    }

    private fun reportSpamWithDialog(spam: Boolean, actionArgs: HistoryItem.Event) {
        val isEncryptedComment = actionArgs.comment?.isEncrypted == true || actionArgs.comment?.type == HistoryItem.Event.Comment.Type.OriginalEncrypted
        if (isEncryptedComment && spam) {
            CommentReportDialog(requireContext()).show {
                reportEncryptedComment(actionArgs)
            }
        } else {
            reportSpam(spam, actionArgs)
        }
    }

    private fun reportEncryptedComment(actionArgs: HistoryItem.Event) {
        if (actionArgs.comment?.isEncrypted == true) {
            decryptComment(actionArgs, actionArgs.comment) {
                applyComment(actionArgs, actionArgs.comment)
                reportSpam(true, actionArgs)
            }
        } else {
            reportSpam(true, actionArgs)
        }
    }

    private fun reportSpam(spam: Boolean, actionArgs: HistoryItem.Event) {
        navigation?.toastLoading(true)
        viewModel.reportSpam(
            wallet = actionArgs.wallet,
            txId = actionArgs.txId,
            comment = comment,
            spam = spam
        ) {
            localIsScam = spam
            spamView.visibility = if (spam) View.VISIBLE else View.GONE
            initArgs(actionArgs)
            navigation?.toastLoading(false)
            if (spam) {
                navigation?.toast(Localization.tx_marked_as_spam)
            }
        }
    }

    private fun openTonViewer(actionArgs: HistoryItem.Event) {
        val url = if (actionArgs.wallet.testnet) {
            "https://testnet.tonviewer.com/transaction"
        } else {
            "https://tonviewer.com/transaction"
        }
        navigation?.openURL("$url/${actionArgs.txId}")
    }

    private fun openMore(view: View, actionArgs: HistoryItem.Event) {
        val actionSheet = ActionSheet(requireContext())
        if (!actionArgs.isOut && !actionArgs.wallet.testnet && !actionArgs.wallet.isWatchOnly && actionArgs.isMaybeSpam) {
            if (getSpamState(actionArgs) == SpamTransactionState.SPAM) {
                actionSheet.addItem(NOT_SPAM_ID, Localization.not_spam, UIKitIcon.ic_block_16)
            } else {
                actionSheet.addItem(REPORT_SPAM_ID, Localization.report_spam, UIKitIcon.ic_block_16)
            }
        }
        actionSheet.addItem(OPEN_EXPLORER_ID, Localization.open_tonviewer, UIKitIcon.ic_globe_16)
        actionSheet.doOnItemClick = { item ->
            when (item.id) {
                REPORT_SPAM_ID -> reportSpamWithDialog(true, actionArgs)
                NOT_SPAM_ID -> reportSpamWithDialog(false, actionArgs)
                OPEN_EXPLORER_ID -> openTonViewer(actionArgs)
            }
        }
        actionSheet.show(view)
    }

    private fun applySpamState(actionArgs: HistoryItem.Event) {
        if (isCanReportSpam(actionArgs)) {
            explorerButton.visibility = View.GONE
            reportSpamButton.visibility = View.VISIBLE
            notSpamButton.visibility = View.VISIBLE
        } else {
            explorerButton.visibility = View.VISIBLE
            reportSpamButton.visibility = View.GONE
            notSpamButton.visibility = View.GONE
        }
    }

    private fun getSpamState(actionArgs: HistoryItem.Event): SpamTransactionState {
        return settingsRepository.getSpamStateTransaction(actionArgs.wallet.id, actionArgs.txId)
    }

    private fun isCanReportSpam(actionArgs: HistoryItem.Event): Boolean {
        return if (getSpamState(actionArgs) != SpamTransactionState.UNKNOWN || actionArgs.isOut || actionArgs.wallet.testnet || actionArgs.wallet.isWatchOnly) {
            false
        } else if (actionArgs.unverifiedToken) {
            true
        } else {
            actionArgs.isMaybeSpam && actionArgs.comment != null && !actionArgs.isOut && !isScam
        }
    }

    private fun applyDate(actionType: ActionType, date: String) {
        val prefix = getString(actionType.nameRes)
        dateView.text = "$prefix $date"
    }

    private fun applyCurrency(currency: CharSequence?, hiddenBalance: Boolean) {
        if (currency.isNullOrBlank()) {
            currencyView.visibility = View.GONE
        } else {
            currencyView.visibility = View.VISIBLE
            if (hiddenBalance) {
                currencyView.text = HIDDEN_BALANCE
            } else {
                currencyView.text = currency.withCustomSymbol(requireContext())
            }
        }
    }

    private fun applyComment(action: HistoryItem.Event, comment: HistoryItem.Event.Comment) {
        commentView.visibility = View.VISIBLE
        if (!comment.isEncrypted) {
            val text = comment.body
            commentView.setData(text, "")
            commentView.setOnClickListener { context?.copyWithToast(text) }
        } else {
            commentView.setData(getString(Localization.encrypted_comment), "", lockDrawable)
            commentView.setOnClickListener { decryptComment(action, comment) }
        }
    }

    private fun decryptComment(action: HistoryItem.Event, comment: HistoryItem.Event.Comment, callback: ((comment: String) -> Unit)? = null) {
        historyHelper.requestDecryptComment(requireContext(), comment, action.txId, action.sender?.address ?: "").catch {
            context?.logError(it)
            commentView.reject()
        }.onEach {
            callback?.invoke(it.body)
            applyComment(action, it)
        }.launchIn(lifecycleScope)
    }

    private fun applyAccount(out: Boolean, address: String?, name: String?) {
        accountNameView.visibility = View.GONE
        accountAddressView.visibility = View.GONE

        val hasName = !name.isNullOrBlank()
        if (hasName) {
            applyAccountWithName(out, address!!, name!!)
        } else if (address != null) {
            accountAddressView.visibility = View.VISIBLE
            accountAddressView.title = getAccountTitle(out)
            accountAddressView.setData(address.shortAddress, "")
            accountAddressView.setOnClickListener {
                context?.copyWithToast(address)
            }
        } else {
            accountAddressView.visibility = View.GONE
        }
    }

    private fun applyAccountWithName(out: Boolean, address: String, name: String) {
        accountNameView.visibility = View.VISIBLE
        accountNameView.title = getAccountTitle(out)
        accountNameView.setData(name.max24, "")
        accountNameView.setOnClickListener {
            context?.copyWithToast(name)
        }

        accountAddressView.visibility = View.VISIBLE
        accountAddressView.title = getString(
            if (out) {
                Localization.recipient_address
            } else Localization.sender_address
        )
        accountAddressView.setData(address.shortAddress, "")
        accountAddressView.setOnClickListener {
            context?.copyWithToast(address)
        }
    }

    private fun getAccountTitle(out: Boolean): String {
        return getString(
            if (out) {
                Localization.recipient
            } else Localization.sender
        )
    }

    private fun updateDataView() {
        val visibleViews = mutableListOf<TransactionDetailView>()
        for (i in 0 until dataView.childCount) {
            val child = dataView.getChildAt(i)
            if (child is TransactionDetailView && child.visibility == View.VISIBLE) {
                visibleViews.add(child)
            }
        }

        for (i in 0 until visibleViews.size) {
            visibleViews[i].position = ListCell.getPosition(visibleViews.size, i)
        }
    }

    private class CommentReportDialog(context: Context): ModalDialog(context, R.layout.dialog_tx_report_comment) {

        init {
            findViewById<View>(R.id.close)?.setOnClickListener { dismiss() }
        }

        fun show(callback: () -> Unit) {
            super.show()
            findViewById<View>(R.id.button)?.setOnClickListener {
                callback()
                dismiss()
            }
        }
    }

}