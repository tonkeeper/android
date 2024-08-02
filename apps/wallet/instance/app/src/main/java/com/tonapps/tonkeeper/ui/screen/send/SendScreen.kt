@file:OptIn(FlowPreview::class)

package com.tonapps.tonkeeper.ui.screen.send

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.icu.Coins
import com.tonapps.ledger.ton.Transaction
import com.tonapps.tonkeeper.api.shortAddress
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.core.signer.SingerResultContract
import com.tonapps.tonkeeper.extensions.getTitle
import com.tonapps.tonkeeper.ui.component.coin.CoinInputView
import com.tonapps.tonkeeper.ui.screen.ledger.sign.LedgerSignScreen
import com.tonapps.tonkeeper.ui.screen.send.state.SendAmountState
import com.tonapps.tonkeeper.ui.screen.send.state.SendFeeState
import com.tonapps.tonkeeper.ui.screen.send.state.SendTransaction
import com.tonapps.tonkeeper.ui.screen.signer.qr.SignerQRScreen
import com.tonapps.tonkeeper.view.TransactionDetailView
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.fieldErrorBorderColor
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.localization.Localization
import io.tonapi.models.Account
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import org.ton.bitstring.BitString
import org.ton.boc.BagOfCells
import uikit.base.BaseFragment
import uikit.dialog.modal.ModalDialog
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.extensions.dp
import uikit.extensions.hideKeyboard
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.FrescoView
import uikit.widget.HeaderView
import uikit.widget.InputView
import uikit.widget.ProcessTaskView
import uikit.widget.SlideBetweenView
import java.util.UUID

class SendScreen: BaseFragment(R.layout.fragment_send_new), BaseFragment.BottomSheet {

    private val args: SendArgs by lazy { SendArgs(requireArguments()) }
    private val signerQRRequestKey: String by lazy { "send_${UUID.randomUUID()}" }
    private val sendViewModel: SendViewModel by viewModel { parametersOf(args.nftAddress) }

    private val signerResultContract = SingerResultContract()
    private val signerLauncher = registerForActivityResult(signerResultContract) {
        if (it == null) {
            setFailed()
        } else {
            sendViewModel.sendSignedMessage(it)
        }
    }

    private lateinit var slidesView: SlideBetweenView
    private lateinit var addressInput: InputView
    private lateinit var amountView: CoinInputView
    private lateinit var convertedView: AppCompatTextView
    private lateinit var swapView: View
    private lateinit var statusView: AppCompatTextView
    private lateinit var maxView: View
    private lateinit var commentInput: InputView
    private lateinit var button: Button
    private lateinit var taskContainerView: View
    private lateinit var confirmButton: Button
    private lateinit var processTaskView: ProcessTaskView
    private lateinit var reviewIconView: FrescoView
    private lateinit var reviewTitleView: AppCompatTextView
    private lateinit var reviewWalletView: TransactionDetailView
    private lateinit var reviewRecipientView: TransactionDetailView
    private lateinit var reviewRecipientAddressView: TransactionDetailView
    private lateinit var reviewRecipientAmountView: TransactionDetailView
    private lateinit var reviewRecipientFeeView: TransactionDetailView
    private lateinit var reviewRecipientCommentView: TransactionDetailView
    private lateinit var reviewSubtitleView: AppCompatTextView
    private lateinit var convertedContainerView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnalyticsHelper.trackEvent("send_open")
        navigation?.setFragmentResultListener(signerQRRequestKey) { bundle ->
            val sign = bundle.getString(SignerQRScreen.KEY_URI)?.toUri()?.getQueryParameter("sign")
            if (sign != null) {
                sendViewModel.sendSignedMessage(BitString(sign))
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        slidesView = view.findViewById(R.id.slides)
        val createHeaderView = view.findViewById<HeaderView>(R.id.create_header)
        createHeaderView.doOnActionClick = { finish() }

        val reviewHeaderView = view.findViewById<HeaderView>(R.id.review_header)
        reviewHeaderView.doOnCloseClick = { showCreate() }

        addressInput = view.findViewById(R.id.address)
        addressInput.doOnTextChange = { text ->
            reviewRecipientFeeView.setLoading()
            addressInput.loading = true
            sendViewModel.userInputAddress(text)
        }
        args.targetAddress?.let { addressInput.text = it }

        amountView = view.findViewById(R.id.amount)
        amountView.doOnValueChanged = sendViewModel::userInputAmount
        amountView.doOnTokenChanged = sendViewModel::userInputToken
        amountView.setOnDoneActionListener { commentInput.requestFocus() }

        convertedView = view.findViewById(R.id.converted)
        convertedView.setOnClickListener { sendViewModel.swap() }

        swapView = view.findViewById(R.id.swap)
        swapView.setOnClickListener { sendViewModel.swap() }

        statusView = view.findViewById(R.id.status)

        maxView = view.findViewById(R.id.max)
        maxView.setOnClickListener { setMax() }

        commentInput = view.findViewById(R.id.comment)
        commentInput.doOnTextChange = sendViewModel::userInputComment
        args.text?.let { commentInput.text = it }

        button = view.findViewById(R.id.button)
        button.setOnClickListener { next() }

        reviewIconView = view.findViewById(R.id.review_icon)
        reviewTitleView = view.findViewById(R.id.review_title)
        reviewSubtitleView = view.findViewById(R.id.review_subtitle)
        reviewWalletView = view.findViewById(R.id.review_wallet)
        reviewRecipientView = view.findViewById(R.id.review_recipient)
        reviewRecipientAddressView = view.findViewById(R.id.review_recipient_address)
        reviewRecipientAmountView = view.findViewById(R.id.review_amount)
        reviewRecipientFeeView = view.findViewById(R.id.review_fee)
        reviewRecipientCommentView = view.findViewById(R.id.review_comment)

        taskContainerView = view.findViewById(R.id.task_container)

        confirmButton = view.findViewById(R.id.confirm_button)

        processTaskView = view.findViewById(R.id.process_task)

        convertedContainerView = view.findViewById(R.id.converted_container)

        if (args.isNft) {
            amountView.visibility = View.GONE
            convertedContainerView.visibility = View.GONE
        }

        view.doKeyboardAnimation { offset, _, _ ->
            button.translationY = -offset.toFloat()
            taskContainerView.translationY = -offset.toFloat()
        }

        collectFlow(sendViewModel.uiInputAddressErrorFlow) {
            addressInput.error = it
            addressInput.loading = false
        }

        collectFlow(sendViewModel.uiEventFlow, ::onEvent)
        collectFlow(sendViewModel.uiInputAmountFlow.map { it.toDouble() }, amountView::setValue)
        collectFlow(sendViewModel.uiBalanceFlow, ::setAmountState)
        collectFlow(sendViewModel.uiInputTokenFlow, ::setToken)
        collectFlow(sendViewModel.uiInputNftFlow, ::setNft)
        collectFlow(sendViewModel.uiButtonEnabledFlow, button::setEnabled)
        collectFlow(sendViewModel.uiTransactionFlow, ::applyTransaction)
        collectFlow(sendViewModel.walletTypeFlow) { walletType ->
            if (walletType == Wallet.Type.Default || walletType == Wallet.Type.Testnet || walletType == Wallet.Type.Lockup) {
                confirmButton.setText(Localization.confirm)
                confirmButton.setOnClickListener { confirm() }
                return@collectFlow
            }
            confirmButton.setText(Localization.continue_action)
            if (walletType == Wallet.Type.Ledger) {
                confirmButton.setOnClickListener { openLedger() }
            } else if (walletType == Wallet.Type.SignerQR) {
                confirmButton.setOnClickListener { openSignerQR() }
            } else if (walletType == Wallet.Type.Signer) {
                confirmButton.setOnClickListener { openSigner() }
            }
        }

        if (args.amountNano > 0) {
            collectFlow(sendViewModel.uiInputTokenFlow.drop(1).take(1)) { token ->
                val amount = Coins.of(args.amountNano, token.decimals)
                amountView.setValue(amount.toDouble())
            }
            sendViewModel.userInputTokenByAddress(args.tokenAddress)
        }
    }

    private fun onEvent(event: SendEvent) {
        when (event) {
            is SendEvent.Signer -> signerLauncher.launch(SingerResultContract.Input(event.body, event.publicKey))
            is SendEvent.Ledger -> requestLedgerSign(event.transaction, event.walletId)
            is SendEvent.Failed -> setFailed()
            is SendEvent.Success -> setSuccess()
            is SendEvent.Loading -> processTaskView.state = ProcessTaskView.State.LOADING
            is SendEvent.Fee -> setFee(event)
            is SendEvent.InsufficientBalance -> showInsufficientBalance()
            is SendEvent.Confirm -> slidesView.next()
        }
    }

    private fun requestLedgerSign(transaction: Transaction, walletId: String) {
        val requestKey = "ledger_sign_request"
        navigation?.setFragmentResultListener(requestKey) { bundle ->
            val result = bundle.getByteArray(LedgerSignScreen.SIGNED_MESSAGE)
            if (result == null) {
                setDefault()
            } else {
                sendViewModel.sendLedgerSignedMessage(BagOfCells(result).first())
            }
        }
        navigation?.add(LedgerSignScreen.newInstance(transaction, walletId, requestKey))
    }

    private fun next() {
        setFee(null)
        addressInput.hideKeyboard()
        sendViewModel.next()
    }

    private fun showInsufficientBalance() {
        InsufficientBalanceDialog(requireContext()).show()
    }

    private fun setFailed() {
        processTaskView.state = ProcessTaskView.State.FAILED
        postDelayed(2000, ::setDefault)
    }

    private fun setSuccess() {
        processTaskView.state = ProcessTaskView.State.SUCCESS
        navigation?.openURL("tonkeeper://activity")
        postDelayed(2000) {
            finish()
        }
    }

    private fun setDefault() {
        confirmButton.visibility = View.VISIBLE
        processTaskView.visibility = View.GONE
        processTaskView.state = ProcessTaskView.State.LOADING
    }

    private fun showCreate() {
        slidesView.prev()
    }

    private fun applyTransaction(transaction: SendTransaction) {
        reviewWalletView.value = transaction.fromWallet.label.getTitle(requireContext(), reviewWalletView.valueView)
        applyTransactionAccount(transaction.targetAccount, transaction.fromWallet.testnet)
        applyTransactionAmount(transaction.amount)
        applyTransactionComment(transaction.comment)
    }

    private fun applyTransactionComment(comment: String?) {
        if (comment.isNullOrEmpty()) {
            reviewRecipientCommentView.visibility = View.GONE
        } else {
            reviewRecipientCommentView.visibility = View.VISIBLE
            reviewRecipientCommentView.value = comment
        }
    }

    private fun applyTransactionAmount(amount: SendTransaction.Amount) {
        if (!amount.value.isPositive) {
            reviewRecipientAmountView.visibility = View.GONE
            return
        }
        reviewRecipientAmountView.visibility = View.VISIBLE
        reviewRecipientAmountView.value = amount.format
        reviewRecipientAmountView.description = amount.convertedFormat
    }

    private fun applyTransactionAccount(account: Account, testnet: Boolean) {
        val address = account.address.toUserFriendly(account.isWallet, testnet).shortAddress

        if (account.name.isNullOrEmpty()) {
            reviewRecipientView.value = address
            reviewRecipientAddressView.visibility = View.GONE
        } else {
            reviewRecipientView.value = account.name
            reviewRecipientAddressView.visibility = View.VISIBLE
            reviewRecipientAddressView.value = address
        }
    }

    private fun setFee(event: SendEvent.Fee?) {
        if (event == null) {
            reviewRecipientFeeView.setLoading()
            confirmButton.isEnabled = false
        } else {
            reviewRecipientFeeView.value = "≈ ${event.format}"
            reviewRecipientFeeView.description = "≈ ${event.convertedFormat}"
            reviewRecipientFeeView.setDefault()
            confirmButton.isEnabled = true
        }
    }

    private fun confirm() {
        confirmButton.visibility = View.GONE
        processTaskView.visibility = View.VISIBLE
        processTaskView.state = ProcessTaskView.State.LOADING
        sendViewModel.send(requireContext())
    }

    private fun openLedger() {
        collectFlow(sendViewModel.signerDataLedger()) { (walletId, transaction) ->
            requestLedgerSign(transaction, walletId)
        }
    }

    private fun openSignerQR() {
        var text = commentInput.text
        if (text.isEmpty()) {
            text = reviewRecipientView.value?.toString() ?: "..."
        }

        collectFlow(sendViewModel.signerData()) { (publicKey, unsignedBody) ->
            navigation?.add(SignerQRScreen.newInstance(publicKey, unsignedBody, text, signerQRRequestKey))
        }
    }

    private fun openSigner() {
        collectFlow(sendViewModel.signerData()) { (publicKey, unsignedBody) ->
            signerLauncher.launch(SingerResultContract.Input(unsignedBody, publicKey))
        }
    }

    private fun setMax() {
        sendViewModel.setMax()
        amountView.hideKeyboard()
    }

    private fun setAmountState(state: SendAmountState) {
        convertedView.text = state.convertedFormat
        amountView.suffix = state.currencyCode

        if (state.insufficientBalance) {
            statusView.setTextColor(requireContext().fieldErrorBorderColor)
            statusView.setText(Localization.insufficient_balance)
            maxView.visibility = View.GONE
        } else {
            statusView.setTextColor(requireContext().textSecondaryColor)
            statusView.text = state.remainingFormat
            maxView.visibility = View.VISIBLE
        }
    }

    private fun setToken(token: TokenEntity) {
        amountView.setToken(token)
        reviewIconView.setImageURI(token.imageUri, null)
        reviewSubtitleView.text = getString(Localization.jetton_transfer, token.symbol)
    }

    private fun setNft(nft: NftEntity) {
        reviewIconView.setRound(20f.dp)
        reviewIconView.setImageURI(nft.mediumUri, null)
        reviewSubtitleView.setText(Localization.nft_transfer)
    }

    override fun onDragging() {
        super.onDragging()
        context?.hideKeyboard()
    }

    companion object {

        fun newInstance(
            targetAddress: String? = null,
            tokenAddress: String = TokenEntity.TON.address,
            amountNano: Long = 0,
            text: String? = null,
            nftAddress: String? = null
        ): SendScreen {
            val screen = SendScreen()
            screen.setArgs(SendArgs(targetAddress, tokenAddress, amountNano, text, nftAddress ?: ""))
            return screen
        }

        private class InsufficientBalanceDialog(
            context: Context
        ): ModalDialog(context, R.layout.dialog_insufficient_balance) {

            init {
                findViewById<HeaderView>(R.id.header)?.doOnActionClick = { dismiss() }
                findViewById<View>(R.id.ok)?.setOnClickListener { dismiss() }
            }
        }
    }
}