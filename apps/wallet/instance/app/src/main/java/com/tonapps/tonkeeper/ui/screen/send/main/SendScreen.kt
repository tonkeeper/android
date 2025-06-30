package com.tonapps.tonkeeper.ui.screen.send.main

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.updateLayoutParams
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.extensions.getUserMessage
import com.tonapps.extensions.isPositive
import com.tonapps.extensions.short4
import com.tonapps.extensions.shortTron
import com.tonapps.extensions.uri
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.core.Amount
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.core.entities.WalletPurchaseMethodEntity
import com.tonapps.tonkeeper.extensions.clipboardText
import com.tonapps.tonkeeper.extensions.copyToClipboard
import com.tonapps.tonkeeper.extensions.getTitle
import com.tonapps.tonkeeper.extensions.hideKeyboard
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.koin.settingsRepository
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.popup.ActionSheet
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.component.coin.CoinInputView
import com.tonapps.tonkeeper.ui.screen.camera.CameraMode
import com.tonapps.tonkeeper.ui.screen.camera.CameraScreen
import com.tonapps.tonkeeper.ui.screen.nft.NftScreen
import com.tonapps.tonkeeper.ui.screen.send.InsufficientFundsDialog
import com.tonapps.tonkeeper.ui.screen.send.contacts.main.SendContactsScreen
import com.tonapps.tonkeeper.ui.screen.send.main.helper.InsufficientBalanceType
import com.tonapps.tonkeeper.ui.screen.send.main.state.SendAmountState
import com.tonapps.tonkeeper.ui.screen.send.main.state.SendDestination
import com.tonapps.tonkeeper.ui.screen.send.main.state.SendFee
import com.tonapps.tonkeeper.ui.screen.send.main.state.SendTransaction
import com.tonapps.tonkeeper.ui.screen.token.viewer.TokenScreen
import com.tonapps.tonkeeper.view.TransactionDetailView
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentBlueColor
import com.tonapps.uikit.color.accentGreenColor
import com.tonapps.uikit.color.accentRedColor
import com.tonapps.uikit.color.fieldActiveBorderColor
import com.tonapps.uikit.color.fieldErrorBorderColor
import com.tonapps.uikit.color.textAccentColor
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.uikit.color.textTertiaryColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.api.entity.Blockchain
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.Plurals
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import org.koin.core.parameter.parametersOf
import org.ton.cell.Cell
import uikit.base.BaseFragment
import uikit.dialog.alert.AlertDialog
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.extensions.dp
import uikit.extensions.drawable
import uikit.extensions.expandTouchArea
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.hideKeyboard
import uikit.extensions.setEndDrawable
import uikit.span.ClickableSpanCompat
import uikit.widget.FrescoView
import uikit.widget.HeaderView
import uikit.widget.InputView
import uikit.widget.LoadableButton
import uikit.widget.ProcessTaskView
import uikit.widget.SlideBetweenView
import java.util.UUID

class SendScreen(wallet: WalletEntity) : WalletContextScreen(R.layout.fragment_send, wallet),
    BaseFragment.BottomSheet {

    override val fragmentName: String = "SendScreen"

    private val args: SendArgs by lazy { SendArgs(requireArguments()) }
    private val contractsRequestKey: String by lazy { "contacts_${UUID.randomUUID()}" }

    override val viewModel: SendViewModel by walletViewModel { parametersOf(args.nftAddress) }

    private val lockDrawable: Drawable by lazy {
        val drawable = requireContext().drawable(UIKitIcon.ic_lock_16)
        drawable.setTint(requireContext().accentGreenColor)
        drawable
    }

    private val insufficientFundsDialog: InsufficientFundsDialog by lazy {
        InsufficientFundsDialog(this)
    }

    private val feeMethodSelector: ActionSheet by lazy {
        ActionSheet(requireContext())
    }

    private lateinit var slidesView: SlideBetweenView
    private lateinit var addressInput: InputView
    private lateinit var addressErrorView: AppCompatTextView
    private lateinit var amountView: CoinInputView
    private lateinit var convertedView: AppCompatTextView
    private lateinit var swapView: View
    private lateinit var statusView: AppCompatTextView
    private lateinit var maxView: View
    private lateinit var commentInput: InputView
    private lateinit var button: LoadableButton
    private lateinit var taskContainerView: View
    private lateinit var addressActionsView: View
    private lateinit var confirmButton: Button
    private lateinit var processTaskView: ProcessTaskView
    private lateinit var reviewIconView: FrescoView
    private lateinit var reviewNetworkIconView: FrescoView
    private lateinit var reviewTitleView: AppCompatTextView
    private lateinit var reviewWalletView: TransactionDetailView
    private lateinit var reviewRecipientView: TransactionDetailView
    private lateinit var reviewRecipientAddressView: TransactionDetailView
    private lateinit var reviewRecipientAmountView: TransactionDetailView
    private lateinit var reviewRecipientFeeView: TransactionDetailView
    private lateinit var reviewRecipientCommentView: TransactionDetailView
    private lateinit var reviewSubtitleView: AppCompatTextView
    private lateinit var convertedContainerView: View
    private lateinit var commentEncryptView: AppCompatTextView
    private lateinit var commentDecryptView: AppCompatTextView
    private lateinit var commentRequiredView: AppCompatTextView
    private lateinit var commentErrorView: AppCompatTextView
    private lateinit var reviewHeaderView: HeaderView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnalyticsHelper.simpleTrackEvent("send_open", viewModel.installId)

        navigation?.setFragmentResultListener(contractsRequestKey) { bundle ->
            val contact = bundle.getParcelableCompat<SendContact>("contact")
                ?: return@setFragmentResultListener
            addressInput.text = contact.address
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        slidesView = view.findViewById(R.id.slides)
        val createHeaderView = view.findViewById<HeaderView>(R.id.create_header)
        createHeaderView.closeView.background = null
        createHeaderView.doOnActionClick = { finish() }
        createHeaderView.doOnCloseClick = { openCamera() }

        reviewHeaderView = view.findViewById(R.id.review_header)
        reviewHeaderView.doOnCloseClick = { showCreate() }

        addressInput = view.findViewById(R.id.address)

        addressActionsView = view.findViewById(R.id.address_actions)

        addressErrorView = view.findViewById(R.id.address_error)

        addressInput.doOnTextChange = { text ->
            reviewRecipientFeeView.setLoading()
            addressInput.loading = true
            viewModel.userInputAddress(text)
            addressActionsView.visibility = if (text.isBlank()) View.VISIBLE else View.GONE
        }

        view.findViewById<View>(R.id.paste).setOnClickListener {
            addressInput.text = requireContext().clipboardText()
        }

        view.findViewById<View>(R.id.address_book).setOnClickListener { openAddressBook() }

        amountView = view.findViewById(R.id.amount)
        amountView.setWallet(wallet)
        amountView.doOnValueChanged = viewModel::userInputAmount
        amountView.doOnTokenValueChanged = {
            viewModel.userInputToken(it.token!!)
        }
        amountView.setOnDoneActionListener { commentInput.requestFocus() }

        convertedView = view.findViewById(R.id.converted)
        convertedView.setOnClickListener { viewModel.swap() }

        swapView = view.findViewById(R.id.swap)
        swapView.setOnClickListener { viewModel.swap() }

        statusView = view.findViewById(R.id.status)

        maxView = view.findViewById(R.id.max)
        maxView.setOnClickListener { setMax() }

        commentInput = view.findViewById(R.id.comment)

        commentEncryptView = view.findViewById(R.id.comment_encrypt)
        commentEncryptView.movementMethod = LinkMovementMethod.getInstance()
        applyCommentEncryptView()

        commentDecryptView = view.findViewById(R.id.comment_decrypt)
        commentDecryptView.movementMethod = LinkMovementMethod.getInstance()
        applyCommentDecryptView()

        commentRequiredView = view.findViewById(R.id.comment_required)
        commentErrorView = view.findViewById(R.id.comment_error)

        commentInput.doOnTextChange = { text ->
            if (text.isEmpty()) {
                commentEncryptView.visibility = View.GONE
                commentDecryptView.visibility = View.GONE
                commentInput.hintColor = requireContext().textSecondaryColor
            } else if (viewModel.uiEncryptedCommentAvailableFlow.value && viewModel.uiInputEncryptedComment.value) {
                commentDecryptView.visibility = View.VISIBLE
                commentInput.hintColor = requireContext().accentGreenColor
            } else if (viewModel.uiEncryptedCommentAvailableFlow.value) {
                commentEncryptView.visibility = View.VISIBLE
                commentInput.hintColor = requireContext().textSecondaryColor
            }
            viewModel.userInputComment(text)
        }

        button = view.findViewById(R.id.button)
        button.setOnClickListener {
            AnalyticsHelper.simpleTrackEvent("send_click", viewModel.installId)
            next()
        }

        reviewIconView = view.findViewById(R.id.review_icon)
        reviewNetworkIconView = view.findViewById(R.id.network_icon)
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
            commentInput.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium)
            }
        }

        view.doKeyboardAnimation { offset, _, _ ->
            button.translationY = -offset.toFloat()
            taskContainerView.translationY = -offset.toFloat()
        }

        confirmButton.setOnClickListener {
            AnalyticsHelper.simpleTrackEvent("send_confirm", viewModel.installId)
            signAndSend()
        }
        confirmButton.setText(if (wallet.hasPrivateKey) Localization.confirm else Localization.continue_action)

        collectFlow(viewModel.uiInputAddressErrorFlow) {
            addressInput.error = it
            addressInput.loading = false
        }

        collectFlow(viewModel.destinationFlow) { destination ->
            when (destination) {
                is SendDestination.TokenError -> {
                    collectFlow(viewModel.swapMethodFlow) {
                        applyTokenError(destination, it)
                    }
                }

                else -> {
                    addressErrorView.visibility = View.GONE
                }
            }
        }

        collectFlow(viewModel.uiCommentAvailable) {
            commentInput.visibility = if (it) View.VISIBLE else View.GONE
        }

        collectFlow(viewModel.uiInputCommentErrorFlow) { errorResId ->
            commentInput.error = errorResId != null
            if (errorResId != null) {
                commentErrorView.setText(errorResId)
                commentErrorView.visibility = View.VISIBLE
            } else {
                commentErrorView.visibility = View.GONE
            }
        }

        collectFlow(viewModel.uiEventFlow, ::onEvent)
        collectFlow(viewModel.uiInputAmountFlow.map { it.value }, amountView::setValue)
        collectFlow(viewModel.uiBalanceFlow, ::setAmountState)
        collectFlow(viewModel.uiInputTokenFlow, ::setToken)
        collectFlow(viewModel.uiInputNftFlow, ::setNft)
        collectFlow(viewModel.uiButtonEnabledFlow, button::setEnabled)
        collectFlow(viewModel.uiTransactionFlow, ::applyTransaction)

        collectFlow(viewModel.uiInputEncryptedComment, ::applyCommentEncryptState)
        collectFlow(viewModel.uiRequiredMemoFlow) { memoRequired ->
            if (memoRequired) {
                commentRequiredView.visibility = View.VISIBLE
                commentInput.hint = getString(Localization.required_comment)
            } else {
                commentInput.hint = getString(Localization.comment)
                commentRequiredView.visibility = View.GONE
            }
        }

        initializeArgs(
            args.targetAddress,
            args.amountNano,
            args.text,
            args.tokenAddress,
            args.bin,
            args.type
        )
    }

    private fun applyTokenError(
        error: SendDestination.TokenError,
        swapMethod: WalletPurchaseMethodEntity?
    ) {
        val addressBlockchainRes = if (error.addressBlockchain == Blockchain.TON) {
            Localization.ton
        } else {
            Localization.tron
        }
        val selectedBlockchainRes = if (error.selectedToken.blockchain == Blockchain.TON) {
            Localization.ton
        } else {
            Localization.tron
        }

        val errorText = getString(
            Localization.send_wrong_blockchain,
            getString(addressBlockchainRes),
        )
        val swapTitle = swapMethod?.method?.title ?: ""
        val swapText = getString(
            Localization.send_wrong_blockchain_swap,
            getString(selectedBlockchainRes),
            getString(addressBlockchainRes),
            swapTitle,
        )

        val isUsdt = error.selectedToken.isTrc20 || error.selectedToken.isUsdt

        if (swapMethod != null && isUsdt) {
            val spannableString = SpannableString("$errorText $swapText")
            val start = spannableString.indexOf(swapTitle)
            spannableString.setSpan(
                ForegroundColorSpan(requireContext().accentBlueColor),
                spannableString.indexOf(swapTitle),
                start + swapTitle.length,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            addressErrorView.setTextColor(requireContext().textSecondaryColor)
            addressErrorView.text = spannableString
            addressErrorView.setOnClickListener {
                BrowserHelper.openPurchase(requireContext(), swapMethod)
            }
        } else {
            addressErrorView.setTextColor(requireContext().accentRedColor)
            addressErrorView.text = errorText
            addressErrorView.setOnClickListener(null)
        }

        addressErrorView.visibility = View.VISIBLE
    }

    private fun confirmSendAll() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setMessage(Localization.send_all_balance)
        builder.setNegativeButton(Localization.continue_action) { viewModel.sign() }
        builder.setPositiveButton(Localization.cancel)
        builder.show()
    }

    private fun signAndSend() {
        collectFlow(viewModel.userInputMaxFlow.take(1)) { isMax ->
            if (isMax) {
                confirmSendAll()
            } else {
                viewModel.sign()
            }
        }
    }

    private fun openAddressBook() {
        hideKeyboard()
        navigation?.add(SendContactsScreen.newInstance(wallet, contractsRequestKey))
    }

    private fun openCamera() {
        hideKeyboard()
        val chains = mutableListOf(Blockchain.TON)

        collectFlow(viewModel.tronAvailableFlow.take(1)) { isTronAvailable ->
            if (isTronAvailable) {
                chains.add(Blockchain.TRON)
            }
            navigation?.add(CameraScreen.newInstance(CameraMode.Address, chains = chains))
        }
    }

    fun initializeArgs(
        targetAddress: String?,
        amountNano: Long?,
        text: String?,
        tokenAddress: String?,
        bin: Cell? = null,
        type: Type
    ) {
        viewModel.initializeTokenAndAmount(
            tokenAddress = tokenAddress,
            amountNano = amountNano,
            type = type
        )

        text?.let { commentInput.text = it }
        targetAddress?.let { addressInput.text = it }
        bin?.let { viewModel.userInputBin(it) }

        if (type == Type.Direct && amountNano.isPositive()) {
            reviewHeaderView.setIcon(0)
            reviewHeaderView.setAction(UIKitIcon.ic_close_16)
            reviewHeaderView.doOnActionClick = { finish() }
            next()
            slidesView.next(false)
            addressInput.hideKeyboard()
        } else {
            reviewHeaderView.setAction(0)
            reviewHeaderView.setIcon(UIKitIcon.ic_chevron_left_16)
            reviewHeaderView.doOnCloseClick = { showCreate() }
            showCreate()
            addressInput.focus()
        }
    }

    private fun applyCommentEncryptState(enabled: Boolean) {
        val textIsEmpty = commentInput.text.isBlank()
        val textSecondaryColor = requireContext().textSecondaryColor
        if (enabled) {
            commentInput.hint = getString(Localization.encrypted_comment)
            val greenColor = requireContext().accentGreenColor
            if (textIsEmpty) {
                commentInput.hintColor = textSecondaryColor
            } else {
                commentInput.hintColor = greenColor
            }
            commentInput.activeBorderColor = greenColor
            if (!textIsEmpty) {
                commentDecryptView.visibility = View.VISIBLE
                commentEncryptView.visibility = View.GONE
            }
        } else {
            commentInput.hint = getString(Localization.comment)
            commentInput.hintColor = textSecondaryColor
            commentInput.activeBorderColor = requireContext().fieldActiveBorderColor
            if (!textIsEmpty) {
                commentDecryptView.visibility = View.GONE
                commentEncryptView.visibility = View.VISIBLE
            }
        }

        if (textIsEmpty) {
            commentDecryptView.visibility = View.GONE
            commentEncryptView.visibility = View.GONE
        }
    }

    private fun applyCommentDecryptView() {
        val hint = getString(Localization.comment_encrypted_hint)
        val button = getString(Localization.decrypt_comment)
        val text = "$hint $button"
        val spannableString = SpannableString(text)
        spannableString.setSpan(ClickableSpanCompat(requireContext().accentBlueColor) {
            viewModel.userInputEncryptedComment(false)
        }, hint.length + 1, text.length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)

        commentDecryptView.text = spannableString
    }

    private fun applyCommentEncryptView() {
        val hint = getString(Localization.comment_decrypted_hint)
        val button = getString(Localization.encrypt_comment)
        val text = "$hint $button"
        val spannableString = SpannableString(text)
        spannableString.setSpan(ClickableSpanCompat(requireContext().accentBlueColor) {
            viewModel.userInputEncryptedComment(true)
        }, hint.length + 1, text.length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)

        commentEncryptView.text = spannableString
    }

    private fun onEvent(event: SendEvent) {
        when (event) {
            is SendEvent.Canceled -> setDefault()
            is SendEvent.Failed -> setFailed(event.throwable)
            is SendEvent.Success -> setSuccess()
            is SendEvent.Loading -> setLoading()
            is SendEvent.Fee -> setFee(event)
            is SendEvent.InsufficientBalance -> {
                showInsufficientFundsDialog(
                    balance = event.balance,
                    required = event.required,
                    withRechargeBattery = event.withRechargeBattery,
                    singleWallet = event.singleWallet,
                    type = event.type
                )
            }

            is SendEvent.Confirm -> slidesView.next()
            is SendEvent.ResetAddress -> {
                addressInput.text = ""
            }
        }
    }

    private fun next() {
        setFee(null)
        addressInput.hideKeyboard()
        viewModel.next()
    }

    private fun setLoading() {
        confirmButton.visibility = View.GONE
        processTaskView.visibility = View.VISIBLE
        processTaskView.state = ProcessTaskView.State.LOADING
    }

    private fun setFailed(throwable: Throwable) {
        processTaskView.setFailedLabel(
            throwable.getUserMessage(requireContext()) ?: getString(
                Localization.error
            )
        )
        processTaskView.state = ProcessTaskView.State.FAILED
        postDelayed(4000, ::setDefault)
    }

    private fun setSuccess() {
        processTaskView.state = ProcessTaskView.State.SUCCESS
        navigation?.openURL("tonkeeper://activity?from=send")
        navigation?.removeByClass({
            postDelayed(2000, ::finish)
        }, NftScreen::class.java, TokenScreen::class.java)
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
        reviewWalletView.value =
            transaction.fromWallet.label.getTitle(requireContext(), reviewWalletView.valueView, 16)
        applyTransactionAccount(transaction.destination)
        applyTransactionAmount(transaction.amount)
        applyTransactionComment(transaction.comment, transaction.encryptedComment)
    }

    private fun applyTransactionComment(comment: String?, encryptedComment: Boolean) {
        if (comment.isNullOrEmpty()) {
            reviewRecipientCommentView.visibility = View.GONE
            return
        }
        reviewRecipientCommentView.visibility = View.VISIBLE
        reviewRecipientCommentView.value = comment
        if (encryptedComment) {
            reviewRecipientCommentView.setTitleRightDrawable(lockDrawable)
        } else {
            reviewRecipientCommentView.setTitleRightDrawable(null)
        }
    }

    private fun applyTransactionAmount(amount: SendTransaction.Amount) {
        if (amount.value.isNegative || amount.value.isZero) {
            reviewRecipientAmountView.visibility = View.GONE
            return
        }
        reviewRecipientAmountView.visibility = View.VISIBLE
        reviewRecipientAmountView.value = amount.format.withCustomSymbol(requireContext())
        reviewRecipientAmountView.description =
            amount.convertedFormat.withCustomSymbol(requireContext())
    }

    private fun applyTransactionAccount(destination: SendDestination) {
        if (destination is SendDestination.TonAccount) {
            if (destination.displayName == null) {
                reviewRecipientView.value = destination.displayAddress
                reviewRecipientView.setOnClickListener {
                    requireContext().copyToClipboard(destination.displayAddress)
                }

                reviewRecipientAddressView.visibility = View.GONE
            } else {
                reviewRecipientView.value = destination.displayName
                reviewRecipientView.setOnClickListener {
                    requireContext().copyToClipboard(destination.displayName!!)
                }

                reviewRecipientAddressView.visibility = View.VISIBLE
                reviewRecipientAddressView.value = destination.displayAddress
                reviewRecipientAddressView.setOnClickListener {
                    requireContext().copyToClipboard(
                        destination.displayAddress
                    )
                }
            }
        } else if (destination is SendDestination.TronAccount) {
            reviewRecipientView.value = destination.address.shortTron
            reviewRecipientView.setOnClickListener {
                requireContext().copyToClipboard(destination.address)
            }

            reviewRecipientAddressView.visibility = View.GONE
        }
    }

    private fun showInsufficientFundsDialog(
        balance: Amount,
        required: Amount,
        withRechargeBattery: Boolean,
        singleWallet: Boolean? = null,
        type: InsufficientBalanceType
    ) {
        if (!insufficientFundsDialog.isShowing) {
            insufficientFundsDialog.show(
                wallet = wallet,
                balance = balance,
                required = required,
                withRechargeBattery = withRechargeBattery,
                singleWallet = singleWallet ?: false,
                type = type
            )
        }
    }

    private fun setFee(event: SendEvent.Fee?) {
        if (event == null) {
            reviewRecipientFeeView.setLoading()
            reviewRecipientFeeView.subtitleView.isEnabled = false
            confirmButton.isEnabled = false
            button.isEnabled = false
            button.isLoading = true
        } else if (event.failed) {
            button.isLoading = false
            button.isEnabled = true
            confirmButton.isEnabled = false
        } else {
            reviewRecipientFeeView.title =
                if (event.fee is SendFee.Ton && event.fee.amount.isRefund) getString(Localization.refund) else getString(
                    Localization.fee
                )

            when (event.fee) {
                is SendFee.TokenFee -> {
                    if (event.fee.amount.value.isZero) {
                        reviewRecipientFeeView.value = getString(Localization.unknown)
                        reviewRecipientFeeView.description = ""
                    } else {
                        reviewRecipientFeeView.value =
                            "≈ ${event.format}".withCustomSymbol(requireContext())
                        reviewRecipientFeeView.description =
                            "≈ ${event.convertedFormat}".withCustomSymbol(requireContext())
                    }
                }

                is SendFee.Battery -> {
                    reviewRecipientFeeView.value =
                        "≈ " + requireContext().resources.getQuantityString(
                            Plurals.battery_charges,
                            event.fee.charges,
                            CurrencyFormatter.format(value = event.fee.charges.toBigDecimal())
                        )
                    reviewRecipientFeeView.description = requireContext().getString(
                        Localization.out_of_available_charges,
                        CurrencyFormatter.format(
                            value = event.fee.chargesBalance.toBigDecimal()
                        )
                    )
                }

                else -> {}
            }

            if (event.showToggle) {
                val paymentMethodViewed =
                    requireContext().settingsRepository?.paymentMethodViewed ?: false
                reviewRecipientFeeView.subtitle = getString(Localization.change_fee_method)
                reviewRecipientFeeView.setOnClickListener {
                    showFeeMethods(event.fee, reviewRecipientFeeView)
                }
                reviewRecipientFeeView.subtitleView.expandTouchArea(8.dp)
                reviewRecipientFeeView.subtitleView.isEnabled = true
                reviewRecipientFeeView.subtitleView.setTextColor(if (paymentMethodViewed) requireContext().textSecondaryColor else requireContext().textAccentColor)
                reviewRecipientFeeView.subtitleView.setEndDrawable(
                    getDrawable(
                        UIKitIcon.ic_chevron_right_12,
                        if (paymentMethodViewed) requireContext().textSecondaryColor else requireContext().textAccentColor
                    )
                )

            } else {
                reviewRecipientFeeView.subtitle = ""
                reviewRecipientFeeView.setOnClickListener(null)
                reviewRecipientFeeView.subtitleView.setEndDrawable(null)
                reviewRecipientFeeView.subtitleView.isEnabled = false
            }

            reviewRecipientFeeView.subtitleView.isEnabled = true
            reviewRecipientFeeView.setDefault()
            confirmButton.isEnabled = !event.insufficientFunds
            button.isEnabled = true
            button.isLoading = false
        }
    }

    private fun setMax() {
        viewModel.setMax()
        amountView.hideKeyboard()
    }

    private fun setAmountState(state: SendAmountState) {
        convertedView.text = state.convertedFormat.withCustomSymbol(requireContext())
        amountView.suffix = state.currencyCode

        if (state.insufficientBalance) {
            statusView.setTextColor(requireContext().fieldErrorBorderColor)
            statusView.setText(Localization.insufficient_balance)
            maxView.visibility = View.GONE
        } else {
            statusView.setTextColor(requireContext().textSecondaryColor)
            statusView.text =
                if (state.hiddenBalance) HIDDEN_BALANCE else state.remainingFormat.withCustomSymbol(
                    requireContext()
                )
            maxView.visibility = View.VISIBLE
        }
    }

    private fun setToken(token: TokenEntity) {
        amountView.setToken(token)
        reviewIconView.setImageURI(token.imageUri, null)

        collectFlow(viewModel.tronAvailableFlow.take(1)) { isTronAvailable ->
            if (isTronAvailable && (token.isUsdt || token.isTrc20)) {
                val networkTextRes = when (token.blockchain) {
                    Blockchain.TRON -> Localization.trc20
                    else -> Localization.ton
                }
                val tokenText = token.symbol.plus(" ").plus(getString(networkTextRes))
                reviewSubtitleView.text = getString(Localization.jetton_transfer, tokenText)

                val networkIconRes = when (token.blockchain) {
                    Blockchain.TRON -> R.drawable.ic_tron
                    else -> R.drawable.ic_ton
                }
                reviewNetworkIconView.setLocalRes(networkIconRes)
                reviewNetworkIconView.visibility = View.VISIBLE
            } else {
                reviewSubtitleView.text = getString(Localization.jetton_transfer, token.symbol)
                reviewNetworkIconView.visibility = View.GONE
            }
        }
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

    private fun showFeeMethods(currentFee: SendFee, targetView: View) {
        feeMethodSelector.width = 264.dp

        if (feeMethodSelector.isShowing) {
            return
        }

        feeMethodSelector.clearItems()

        viewModel.feeOptions.forEach { fee ->
            when (fee) {
                is SendFee.TokenFee -> {
                    val formattedAmount = CurrencyFormatter.format(
                        fee.amount.token.symbol,
                        fee.amount.value,
                        2
                    )
                    val formattedFiat = CurrencyFormatter.formatFiat(
                        fee.fiatCurrency.code,
                        fee.fiatAmount,
                    )
                    feeMethodSelector.addItem(
                        id = fee.amount.token.symbol.hashCode().toLong(),
                        title = fee.amount.token.symbol,
                        subtitle = "≈ $formattedAmount · $formattedFiat",
                        imageUri = fee.amount.token.imageUri,
                        icon = if (currentFee == fee) {
                            getDrawable(UIKitIcon.ic_done_16)
                        } else null,
                        onClick = {
                            viewModel.setFeeMethod(fee)
                        }
                    )
                }

                is SendFee.Battery -> {
                    val formattedCharges = requireContext().resources.getQuantityString(
                        Plurals.battery_charges,
                        fee.charges,
                        CurrencyFormatter.format(value = fee.charges.toBigDecimal())
                    )
                    feeMethodSelector.addItem(
                        id = "battery".hashCode().toLong(),
                        title = getString(Localization.battery_refill_title),
                        subtitle = "≈ $formattedCharges",
                        imageUri = UIKitIcon.ic_flash_24.uri(),
                        imageTintColor = requireContext().accentGreenColor,
                        icon = if (currentFee == fee) {
                            getDrawable(UIKitIcon.ic_done_16)
                        } else null,
                        onClick = {
                            viewModel.setFeeMethod(fee)
                        }
                    )
                }

                else -> {}
            }
        }

        feeMethodSelector.showPopupAboveRight(targetView)
    }

    companion object {

        class Builder(val wallet: WalletEntity) {
            private var targetAddress: String? = null
            private var tokenAddress: String? = null
            private var amountNano: Long? = null
            private var text: String? = null
            private var nftAddress: String? = null
            private var type: Type = Type.Default
            private var bin: Cell? = null

            fun setTargetAddress(targetAddress: String) = apply {
                this.targetAddress = targetAddress
            }

            fun setTokenAddress(tokenAddress: String?) = apply {
                this.tokenAddress = tokenAddress
            }

            fun setAmountNano(amountNano: Long?) = apply {
                this.amountNano = amountNano
            }

            fun setText(text: String?) = apply {
                this.text = text
            }

            fun setNftAddress(nftAddress: String) = apply {
                this.nftAddress = nftAddress
            }

            fun setType(type: Type) = apply {
                this.type = type
            }

            fun setBin(bin: Cell?) = apply {
                this.bin = bin
            }

            fun build() = newInstance(
                wallet,
                targetAddress,
                tokenAddress,
                amountNano,
                text,
                nftAddress,
                type,
                bin
            )
        }

        enum class Type {
            Default, Direct, Nft
        }

        fun newInstance(
            wallet: WalletEntity,
            targetAddress: String? = null,
            tokenAddress: String? = null,
            amountNano: Long? = null,
            text: String? = null,
            nftAddress: String? = null,
            type: Type,
            bin: Cell? = null
        ): SendScreen {
            Log.d("SendScreen", "newInstance: $targetAddress, $tokenAddress, $amountNano, $text")
            val args = SendArgs(
                targetAddress = targetAddress,
                tokenAddress = tokenAddress,
                amountNano = amountNano,
                text = text, nftAddress = nftAddress ?: "",
                type = type,
                bin = bin
            )
            return SendScreen(wallet).apply {
                setArgs(args)
            }
        }
    }
}