package com.tonapps.tonkeeper.ui.screen.send

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.lifecycleScope
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.icu.Coins
import com.tonapps.tonkeeper.api.shortAddress
import com.tonapps.tonkeeper.core.signer.SingerResultContract
import com.tonapps.tonkeeper.ui.component.coin.CoinInputView
import com.tonapps.tonkeeper.ui.screen.send.state.SendAddressState
import com.tonapps.tonkeeper.ui.screen.send.state.SendAmountState
import com.tonapps.tonkeeper.ui.screen.send.state.SendConfig
import com.tonapps.tonkeeper.ui.screen.send.state.SendFeeState
import com.tonapps.tonkeeper.ui.screen.send.state.SendTransaction
import com.tonapps.tonkeeper.view.TransactionDetailView
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.fieldErrorBorderColor
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.localization.Localization
import io.tonapi.models.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.extensions.hideKeyboard
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.FrescoView
import uikit.widget.HeaderView
import uikit.widget.InputView
import uikit.widget.ProcessTaskView
import uikit.widget.SlideBetweenView

class SendScreen: BaseFragment(R.layout.fragment_send_new), BaseFragment.BottomSheet {

    private val args: SendArgs by lazy { SendArgs(requireArguments()) }
    private val sendViewModel: SendViewModel by viewModel { parametersOf(args.nftAddress) }

    private val signerLauncher = registerForActivityResult(SingerResultContract()) {
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
        confirmButton.setOnClickListener { confirm() }

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
        collectFlow(sendViewModel.uiInputAmountFlow, amountView::setValue)
        collectFlow(sendViewModel.uiBalanceFlow, ::setAmountState)
        collectFlow(sendViewModel.uiInputTokenFlow, ::setToken)
        collectFlow(sendViewModel.uiButtonEnabledFlow, button::setEnabled)
        collectFlow(sendViewModel.uiTransactionFlow, ::applyTransaction)
        collectFlow(sendViewModel.feeFlow, ::setFee)

        sendViewModel.userInputTokenByAddress(args.tokenAddress)
    }

    private fun onEvent(event: SendEvent) {
        when (event) {
            is SendEvent.Signer -> signerLauncher.launch(SingerResultContract.Input(event.body, event.publicKey))
            is SendEvent.Failed -> setFailed()
            is SendEvent.Success -> setSuccess()
            is SendEvent.Loading -> processTaskView.state = ProcessTaskView.State.LOADING
        }
    }

    private fun next() {
        showReview()
    }

    private fun showReview() {
        // sendViewModel.calculateFee()
        slidesView.next()
        addressInput.hideKeyboard()
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
        reviewWalletView.value = transaction.fromWallet.label.title
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

    private fun setFee(state: SendFeeState?) {
        if (state == null) {
            reviewRecipientFeeView.setLoading()
            confirmButton.isEnabled = false
        } else {
            reviewRecipientFeeView.value = "≈ ${state.format}"
            reviewRecipientFeeView.description = "≈ ${state.convertedFormat}"
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

    private fun setMax() {
        sendViewModel.setMax()
        amountView.hideKeyboard()
    }

    private fun setAmountState(state: SendAmountState) {
        convertedView.text = state.convertedFormat

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

    override fun onDragging() {
        super.onDragging()
        context?.hideKeyboard()
    }

    /*

    titleView.setText(Localization.nft_transfer)
                amountView.visibility = View.GONE

    private fun applyNft(nftEntity: NftEntity) {
        iconView.setRound(20f.dp)
        iconView.setImageURI(nftEntity.mediumUri)
        actionTitle.text = String.format("%s · %s", nftEntity.name, nftEntity.collectionName.ifEmpty {
            getString(Localization.unnamed_collection)
        })
        titleView.setText(Localization.nft_transfer)
    }
     */

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
    }
}