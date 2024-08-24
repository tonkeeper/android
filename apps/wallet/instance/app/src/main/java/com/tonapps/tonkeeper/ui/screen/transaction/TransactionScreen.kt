package com.tonapps.tonkeeper.ui.screen.transaction

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.SpannableString
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.lifecycle.lifecycleScope
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.extensions.ifPunycodeToUnicode
import com.tonapps.extensions.logError
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.api.shortAddress
import com.tonapps.tonkeeper.api.shortHash
import com.tonapps.tonkeeper.core.history.ActionType
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.core.history.nameRes
import com.tonapps.tonkeeper.extensions.copyWithToast
import com.tonapps.tonkeeper.ui.screen.token.unverified.TokenUnverifiedScreen
import com.tonapps.tonkeeper.view.TransactionDetailView
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentGreenColor
import com.tonapps.uikit.color.textTertiaryColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.android.inject
import uikit.base.BaseFragment
import uikit.extensions.drawable
import uikit.extensions.reject
import uikit.extensions.setColor
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.FrescoView

class TransactionScreen: BaseFragment(R.layout.dialog_transaction), BaseFragment.Modal {

    companion object {

        private const val ARG_ACTION = "action"

        fun newInstance(action: HistoryItem.Event): TransactionScreen {
            val screen = TransactionScreen()
            screen.arguments = Bundle().apply {
                putParcelable(ARG_ACTION, action)
            }
            return screen
        }
    }

    private val action: HistoryItem.Event by lazy {
        requireArguments().getParcelableCompat(ARG_ACTION)!!
    }

    private val historyHelper: HistoryHelper by inject()

    private lateinit var iconView: FrescoView
    private lateinit var spamView: View
    private lateinit var amountView: AppCompatTextView
    private lateinit var currencyView: AppCompatTextView
    private lateinit var dateView: AppCompatTextView
    private lateinit var dataView: LinearLayoutCompat
    private lateinit var txView: TransactionDetailView
    private lateinit var feeView: TransactionDetailView
    private lateinit var commentView: TransactionDetailView
    private lateinit var accountNameView: TransactionDetailView
    private lateinit var accountAddressView: TransactionDetailView
    private lateinit var explorerButton: AppCompatTextView
    private lateinit var unverifiedView: View

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

        iconView = view.findViewById(R.id.icon)

        amountView = view.findViewById(R.id.amount)
        spamView = view.findViewById(R.id.spam)
        spamView.visibility = if (action.isScam) View.VISIBLE else View.GONE

        currencyView = view.findViewById(R.id.currency)
        dateView = view.findViewById(R.id.date)
        unverifiedView = view.findViewById(R.id.unverified)
        unverifiedView.setOnClickListener {
            navigation?.add(TokenUnverifiedScreen.newInstance())
        }

        dataView = view.findViewById(R.id.data)

        feeView = view.findViewById(R.id.fee)
        feeView.title = getString(Localization.fee)

        commentView = view.findViewById(R.id.comment)
        commentView.title = getString(Localization.comment)

        accountNameView = view.findViewById(R.id.account_name)
        accountAddressView = view.findViewById(R.id.account_address)

        txView = view.findViewById(R.id.tx)
        txView.title = getString(Localization.transaction)

        explorerButton = view.findViewById(R.id.open_explorer)

        unverifiedView.visibility = if (action.unverifiedToken) {
            View.VISIBLE
        } else {
            View.GONE
        }

        if (action.hiddenBalance) {
            amountView.text = HIDDEN_BALANCE
            feeView.setData(HIDDEN_BALANCE, HIDDEN_BALANCE)
        } else {
            amountView.text = action.value.withCustomSymbol(requireContext())
            feeView.setData(action.fee!!.withCustomSymbol(requireContext()), action.feeInCurrency!!.withCustomSymbol(requireContext()))
        }

        if (action.isScam) {
            applyIcon(null)
        } else {
            applyIcon(action.coinIconUrl)
        }

        if (action.comment != null && !action.isScam) {
            applyComment(action.comment!!)
        } else {
            commentView.visibility = View.GONE
        }

        applyAccount(action.isOut, action.address, action.addressName?.ifPunycodeToUnicode())
        applyCurrency(action.currency, action.hiddenBalance)
        applyDate(action.action, action.dateDetails)

        txView.setData(action.txId.shortHash, "")
        txView.setOnClickListener {
            context?.copyWithToast(action.txId)
        }

        val txHashText = getString(Localization.transaction) + " " + action.txId.substring(0, 8)
        val txHashSpannable = SpannableString(txHashText)
        txHashSpannable.setColor(
            requireContext().textTertiaryColor,
            getString(Localization.transaction).length,
            txHashText.length
        )
        explorerButton.text = txHashSpannable


        explorerButton.setOnClickListener {
            navigation?.openURL("https://tonviewer.com/transaction/${action.txId}", true)
        }

        updateDataView()
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

    private fun applyIcon(icon: String?) {
        if (icon.isNullOrBlank()) {
            iconView.visibility = View.GONE
        } else {
            iconView.visibility = View.VISIBLE
            iconView.setImageURI(icon)
        }
    }

    private fun applyComment(comment: HistoryItem.Event.Comment) {
        commentView.visibility = View.VISIBLE
        if (!comment.isEncrypted) {
            val text = comment.body
            commentView.setData(text, "")
            commentView.setOnClickListener { context?.copyWithToast(text) }
        } else {
            commentView.setData(getString(Localization.encrypted_comment), "", lockDrawable)
            commentView.setOnClickListener { decryptComment(comment) }
        }
    }

    private fun decryptComment(comment: HistoryItem.Event.Comment) {
        historyHelper.requestDecryptComment(requireContext(), comment, action.txId, action.senderAddress ?: "").catch {
            context?.logError(it)
            commentView.reject()
        }.onEach {
            applyComment(it)
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
        accountNameView.setData(name, "")
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
            visibleViews[i].position =
                com.tonapps.uikit.list.ListCell.getPosition(visibleViews.size, i)
        }
    }
}