package com.tonkeeper.fragment.send.confirm

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.viewModels
import com.tonapps.tonkeeperx.R
import com.tonkeeper.api.shortAddress
import com.tonkeeper.fragment.passcode.lock.LockScreen
import com.tonkeeper.fragment.send.SendScreenEffect
import com.tonkeeper.fragment.send.pager.PagerScreen
import com.tonkeeper.view.TransactionDetailView
import com.tonkeeper.fragment.wallet.history.HistoryScreen
import uikit.list.ListCell
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.FrescoView
import uikit.widget.ProcessTaskView

class ConfirmScreen: PagerScreen<ConfirmScreenState, ConfirmScreenEffect, ConfirmScreenFeature>(R.layout.fragment_send_confirm) {

    companion object {
        private const val SEND_REQUEST = "send"

        fun newInstance() = ConfirmScreen()
    }

    override val feature: ConfirmScreenFeature by viewModels()

    private lateinit var iconView: FrescoView
    private lateinit var titleView: AppCompatTextView
    private lateinit var recipientView: TransactionDetailView
    private lateinit var recipientAddressView: TransactionDetailView
    private lateinit var amountView: TransactionDetailView
    private lateinit var feeView: TransactionDetailView
    private lateinit var commentView: TransactionDetailView
    private lateinit var sendButton: Button
    private lateinit var processView: ProcessTaskView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigation?.setFragmentResultListener(SEND_REQUEST) { _, _ ->
            feature.send(sendFeature.transaction.value!!)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        iconView = view.findViewById(R.id.icon)

        titleView = view.findViewById(R.id.title)

        recipientView = view.findViewById(R.id.recipient)
        recipientView.title = getString(R.string.recipient)
        recipientView.position = ListCell.Position.FIRST

        recipientAddressView = view.findViewById(R.id.recipient_address)
        recipientAddressView.title = getString(R.string.recipient_address)
        recipientAddressView.position = ListCell.Position.MIDDLE

        amountView = view.findViewById(R.id.amount)
        amountView.title = getString(R.string.amount)
        amountView.position = ListCell.Position.MIDDLE

        feeView = view.findViewById(R.id.fee)
        feeView.title = getString(R.string.fee)
        feeView.position = ListCell.Position.MIDDLE

        commentView = view.findViewById(R.id.comment)
        commentView.title = getString(R.string.comment)
        commentView.position = ListCell.Position.LAST

        sendButton = view.findViewById(R.id.send)
        sendButton.setOnClickListener { send() }

        processView = view.findViewById(R.id.process)

        sendFeature.transaction.observe(viewLifecycleOwner) { transaction ->
            setRecipient(transaction.address!!, transaction.name)
            setComment(transaction.comment)

            feature.setAmount(transaction.amount, transaction.tokenAddress, transaction.tokenSymbol)
            iconView.setImageURI(transaction.icon)

            if (transaction.isTon) {
                titleView.setText(R.string.transfer)
            } else {
                titleView.text = getString(R.string.jetton_transfer, transaction.tokenName)
            }
        }
    }

    private fun send() {
        navigation?.add(LockScreen.newInstance(SEND_REQUEST))
    }

    private fun setComment(comment: String?) {
        if (comment.isNullOrEmpty()) {
            commentView.visibility = View.GONE
            feeView.position = ListCell.Position.LAST
        } else {
            commentView.visibility = View.VISIBLE
            commentView.value = comment

            feeView.position = ListCell.Position.MIDDLE
        }
    }

    private fun setRecipient(address: String, name: String?) {
        if (name.isNullOrEmpty()) {
            setAddress(address)
        } else {
            recipientView.value = name
            recipientAddressView.visibility = View.VISIBLE
            recipientAddressView.value = address.shortAddress
        }
    }

    private fun setAddress(address: String) {
        recipientAddressView.visibility = View.GONE
        recipientView.value = address.shortAddress
    }

    override fun newUiState(state: ConfirmScreenState) {
        if (state.processActive) {
            sendButton.visibility = View.GONE
            processView.visibility = View.VISIBLE
            processView.state = state.processState
        } else {
            sendButton.visibility = View.VISIBLE
            processView.visibility = View.GONE
        }

        amountView.value = state.amount
        amountView.description = state.amountInCurrency

        sendButton.isEnabled = state.buttonEnabled

        if (state.fee.isNullOrEmpty()) {
            feeView.setLoading()
        } else {
            feeView.setData(state.fee, state.feeInCurrency)
        }
    }

    override fun newUiEffect(effect: ConfirmScreenEffect) {
        super.newUiEffect(effect)
        if (effect is ConfirmScreenEffect.CloseScreen) {
            if (effect.navigateToHistory) {
                navigation?.openURL(HistoryScreen.DeepLink)
            }

            sendFeature.sendEffect(SendScreenEffect.Finish)
        }
    }

    override fun onVisibleChange(visible: Boolean) {
        super.onVisibleChange(visible)
        sendFeature.setHeaderVisible(!visible)
        if (visible) {
            feature.requestFee(sendFeature.transaction.value!!)
        }
    }
}