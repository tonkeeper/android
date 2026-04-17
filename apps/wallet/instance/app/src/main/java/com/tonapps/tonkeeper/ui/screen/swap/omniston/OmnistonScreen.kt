package com.tonapps.tonkeeper.ui.screen.swap.omniston

import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.lifecycleScope
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.errors.InsufficientFundsException
import com.tonapps.extensions.getUserMessage
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.extensions.addFeeItem
import com.tonapps.tonkeeper.extensions.finishDelay
import com.tonapps.tonkeeper.extensions.hideKeyboard
import com.tonapps.tonkeeper.extensions.id
import com.tonapps.tonkeeper.extensions.isOverlapping
import com.tonapps.tonkeeper.extensions.routeToHistoryTab
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.helper.TwinInput
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.popup.ActionSheet
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.screen.onramp.main.view.CurrencyInputView
import com.tonapps.tonkeeper.ui.screen.onramp.main.view.ReviewInputView
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import com.tonapps.tonkeeper.ui.screen.send.InsufficientFundsDialog
import com.tonapps.tonkeeper.ui.screen.swap.omniston.state.OmnistonStep
import com.tonapps.tonkeeper.ui.screen.swap.omniston.state.SwapQuoteState
import com.tonapps.tonkeeper.ui.screen.swap.omniston.state.SwapTokenState
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.backgroundPageColor
import com.tonapps.uikit.color.textAccentColor
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.drawable.FooterDrawable
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.extensions.dp
import uikit.extensions.reject
import uikit.extensions.rotate180Animation
import uikit.extensions.withClickable
import uikit.extensions.withInterpunct
import uikit.span.ClickableSpanCompat
import uikit.widget.HeaderView
import uikit.widget.LoadableButton
import uikit.widget.ModalHeader
import uikit.widget.ProcessTaskView
import uikit.widget.SlideActionView
import uikit.widget.SlideBetweenView
import uikit.widget.item.ItemLineView
import java.util.concurrent.CancellationException

class OmnistonScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_omniston, wallet), BaseFragment.BottomSheet {

    override val viewModel: OmnistonViewModel by walletViewModel {
        parametersOf(OmnistonArgs(requireArguments()))
    }

    private val insufficientFundsDialog: InsufficientFundsDialog by lazy {
        InsufficientFundsDialog(this)
    }

    private val feeMethodSelector: ActionSheet by lazy {
        ActionSheet(requireContext())
    }

    private val disableNext: Boolean
        get() = continueButton.isLoading || !continueButton.isEnabled

    private val rootViewMode: RootViewModel by activityViewModel()

    private lateinit var slidesView: SlideBetweenView
    private lateinit var headerView: HeaderView
    private lateinit var modalHeaderView: ModalHeader
    private lateinit var sendInputView: CurrencyInputView
    private lateinit var receiveInputView: CurrencyInputView
    private lateinit var continueButton: LoadableButton
    private lateinit var actionContainerView: View
    private lateinit var detailsContainerView: View
    private lateinit var reviewSendView: ReviewInputView
    private lateinit var reviewReceiveView: ReviewInputView
    private lateinit var priceView: AppCompatTextView
    private lateinit var priceReversedView: AppCompatTextView
    private lateinit var slideActionView: SlideActionView
    private lateinit var taskView: ProcessTaskView
    private lateinit var feeView: ItemLineView
    private lateinit var slippageView: ItemLineView
    private lateinit var disclaimerView: AppCompatTextView
    private lateinit var actionContainerDrawable: FooterDrawable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analytics?.swapOpen(viewModel.swapUri, true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.edit).setOnClickListener {
            reset()
            sendInputView.focusWithKeyboard()
        }

        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { onBackPressed() }
        headerView.doOnActionClick = { finish() }

        taskView = view.findViewById(R.id.task)
        priceView = view.findViewById(R.id.price)
        priceReversedView = view.findViewById(R.id.price_reversed)
        slideActionView = view.findViewById(R.id.slide_action)
        slideActionView.doOnDone = { sign() }
        feeView = view.findViewById(R.id.details_fee)

        val bottomReviewView = view.findViewById<View>(R.id.bottom_review)
        slidesView = view.findViewById(R.id.slides)

        modalHeaderView = view.findViewById(R.id.modal_header)
        modalHeaderView.onCloseClick = { finish() }

        reviewSendView = view.findViewById(R.id.review_send)
        reviewSendView.setTitleTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        reviewSendView.setValueTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
        applyReviewSend()

        reviewReceiveView = view.findViewById(R.id.review_receive)
        reviewReceiveView.setBackgroundResource(uikit.R.drawable.bg_content_top)
        reviewReceiveView.setTitleTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        reviewReceiveView.setValueTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)

        slippageView = view.findViewById(R.id.details_slippage)

        sendInputView = view.findViewById(R.id.send_input)
        sendInputView.doOnTextChange = viewModel::updateSendInput
        sendInputView.doOnFocusChange = { hasFocus ->
            if (hasFocus) {
                viewModel.updateFocusInput(TwinInput.Type.Send)
            }
        }

        sendInputView.doOnCurrencyClick = {
            hideKeyboard()
            viewModel.pickCurrency(TwinInput.Type.Send)
        }

        receiveInputView = view.findViewById(R.id.receive_input)
        receiveInputView.doOnTextChange = viewModel::updateReceiveInput
        receiveInputView.doOnFocusChange = { hasFocus ->
            if (hasFocus) {
                viewModel.updateFocusInput(TwinInput.Type.Receive)
            }
        }
        receiveInputView.doOnCurrencyClick = {
            hideKeyboard()
            viewModel.pickCurrency(TwinInput.Type.Receive)
        }

        view.findViewById<View>(R.id.switch_button).setOnClickListener(::switch)

        reviewReceiveView.setOnClickListener {
            receiveInputView.focusWithKeyboard()
            reset()
        }
        reviewSendView.setOnClickListener {
            sendInputView.focusWithKeyboard()
            reset()
        }

        disclaimerView = view.findViewById(R.id.disclaimer)
        disclaimerView.movementMethod = LinkMovementMethod.getInstance()
        applyDisclaimer()

        continueButton = view.findViewById(R.id.continue_button)
        continueButton.setOnClickListener { next() }

        actionContainerDrawable = FooterDrawable(requireContext())
        actionContainerDrawable.setColor(requireContext().backgroundPageColor)
        actionContainerView = view.findViewById(R.id.action_container)
        actionContainerView.background = actionContainerDrawable

        detailsContainerView = view.findViewById(R.id.details_container)

        view.doKeyboardAnimation { offset, _, _ ->
            bottomReviewView.translationY = -offset.toFloat()
            actionContainerView.translationY = -offset.toFloat()
            updateDivider()
        }

        sendInputView.doOnEditorAction = { actionId ->
            if (disableNext) {
                continueButton.reject()
                true
            } else if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                if (sendInputView.isEmpty) {
                    receiveInputView.focusWithKeyboard()
                }
                next()
                true
            } else {
                false
            }
        }

        receiveInputView.doOnEditorAction = { actionId ->
            if (disableNext) {
                continueButton.reject()
                true
            } else if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                if (receiveInputView.isEmpty) {
                    sendInputView.focusWithKeyboard()
                }
                next()
                true
            } else {
                false
            }
        }

        collectFlow(viewModel.quoteStateFlow, ::applyQuoteState)
        collectFlow(viewModel.priceFlow) { (first, second) ->
            priceView.text = first
            priceReversedView.text = second
            updateDivider()
        }
        collectFlow(viewModel.stepFlow) { step ->
            if (step == OmnistonStep.Input) {
                headerView.visibility = View.GONE
                modalHeaderView.visibility = View.VISIBLE
                slidesView.prev()
            } else {
                headerView.visibility = View.VISIBLE
                modalHeaderView.visibility = View.GONE
                slideActionView.startReverseProgress()
                slidesView.next()
                hideKeyboard()
            }
        }

        collectFlow(viewModel.sendOutputCurrencyFlow, sendInputView::setCurrency)
        collectFlow(viewModel.sendPlaceholderValueFlow, sendInputView::setPlaceholder)

        collectFlow(viewModel.sendOutputValueFlow, sendInputView::setValue)
        collectFlow(viewModel.receiveOutputValueFlow, receiveInputView::setValue)

        collectFlow(viewModel.receiveOutputCurrencyFlow, receiveInputView::setCurrency)
        collectFlow(viewModel.receivePlaceholderValueFlow, receiveInputView::setPlaceholder)
        collectFlow(viewModel.uiStateToken, ::applyTokenState)
        collectFlow(viewModel.inputPrefixFlow, ::applyPrefix)
        collectFlow(viewModel.countDownFlow, ::applyResetProgress)

        collectFlow(viewModel.uiButtonStateFlow, continueButton::applyUiState)

        collectFlow(viewModel.requestFocusFlow) { inputType ->
            when (inputType) {
                TwinInput.Type.Send -> sendInputView.focusWithKeyboard()
                TwinInput.Type.Receive -> receiveInputView.focusWithKeyboard()
            }
        }
    }

    private fun updateDivider() {
        val needDivider = actionContainerView.isOverlapping(priceView) || actionContainerView.isOverlapping(priceReversedView)
        actionContainerDrawable.setDivider(needDivider)
    }

    private fun applyDisclaimer() {
        val textSecondaryColor = requireContext().textSecondaryColor
        val provider = getString(Localization.ston_fi)
        val termsOfUse = getString(Localization.terms_of_use)
        val privacyPolicy = getString(Localization.privacy_policy)
        val text = requireContext().getString(Localization.swap_disclaimer, provider, termsOfUse, privacyPolicy)
        val builder = SpannableStringBuilder(text)

        val providerStart = text.indexOf(provider)
        val providerEnd = providerStart + provider.length
        builder.setSpan(ClickableSpanCompat(textSecondaryColor) {
            BrowserHelper.open(requireContext(), "https://ston.fi/")
        }, providerStart, providerEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val termsOfUseStart = text.indexOf(termsOfUse)
        val termsOfUseEnd = termsOfUseStart + termsOfUse.length
        builder.setSpan(ClickableSpanCompat(textSecondaryColor) {
            BrowserHelper.open(requireContext(), "https://ston.fi/terms")
        }, termsOfUseStart, termsOfUseEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val privacyPolicyStart = text.indexOf(privacyPolicy)
        val privacyPolicyEnd = privacyPolicyStart + privacyPolicy.length
        builder.setSpan(ClickableSpanCompat(textSecondaryColor) {
            BrowserHelper.open(requireContext(), "https://ston.fi/privacy")
        }, privacyPolicyStart, privacyPolicyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        disclaimerView.text = builder
    }

    private fun applyResetProgress(progress: Float) {
        slideActionView.setReverseProgress(progress)
        if (progress >= 1f) {
            reset()
        }
    }

    override fun finish() {
        super.finish()
        hideKeyboard()
    }

    override fun onDragging() {
        super.onDragging()
        hideKeyboard()
    }

    private fun applyPrefix(inputType: TwinInput.Type) {
        if (inputType == TwinInput.Type.Send) {
            sendInputView.setPrefix(CurrencyInputView.EQUALS_SIGN_PREFIX)
            receiveInputView.setPrefix(null)
        } else {
            sendInputView.setPrefix(null)
            receiveInputView.setPrefix(CurrencyInputView.EQUALS_SIGN_PREFIX)
        }
    }

    private fun applyTokenState(state: SwapTokenState) {
        if (state.insufficientBalance) {
            sendInputView.setInsufficientBalance()
        } else {
            sendInputView.setTokenBalance(state.tokenBalance, state.remainingFormat, false)
        }
    }

    private fun applyReviewSend() {
        val prefix = getString(Localization.send)
        val edit = getString(Localization.edit)
        val text = "$prefix · $edit"
        val spannable = SpannableString(text)
        spannable.setSpan(ClickableSpanCompat(requireContext().textAccentColor) {
            reset()
            sendInputView.focusWithKeyboard()
        }, prefix.length + 3, text.length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)

        reviewSendView.setTitleMovementMethod(LinkMovementMethod.getInstance())
        reviewSendView.setTitle(spannable)
    }

    private fun inputErrorState() {
        sendInputView.focusWithKeyboard()
        sendInputView.reject()
    }

    private fun signDefaultState() {
        slideActionView.reset()
        slideActionView.visibility = View.VISIBLE
        taskView.visibility = View.GONE
    }

    private fun signLoading() {
        taskView.visibility = View.VISIBLE
        taskView.state = ProcessTaskView.State.LOADING
        slideActionView.visibility = View.GONE
    }

    private fun signSuccess() {
        analytics?.swapSuccess(
            jettonSymbolFrom = viewModel.jettonSymbolFrom,
            jettonSymbolTo = viewModel.jettonSymbolTo,
            providerName = viewModel.providerName,
            providerUrl = viewModel.providerUrl,
            native = true,
        )

        rootViewMode.routeToHistoryTab("swap")
        taskView.visibility = View.VISIBLE
        taskView.state = ProcessTaskView.State.SUCCESS
        finishDelay()
    }

    private fun singFailure(throwable: Throwable) {
        taskView.visibility = View.VISIBLE

        taskView.setFailedLabel(throwable.getUserMessage(requireContext()) ?: getString(Localization.error))

        taskView.state = ProcessTaskView.State.FAILED
        postDelayed(5000) { signDefaultState() }
    }

    private fun sign() {
        signLoading()
        analytics?.swapConfirm(
            jettonSymbolFrom = viewModel.jettonSymbolFrom,
            jettonSymbolTo = viewModel.jettonSymbolTo,
            providerName = viewModel.providerName,
            providerUrl = viewModel.providerUrl,
            native = true,
        )
        viewModel.sign(
            onSuccess = { signSuccess() },
            onError = { e ->
                if (e is CancellationException) {
                    signDefaultState()
                } else {
                    singFailure(e)
                }
                viewModel.restoreSwapStream()
            }
        )
    }

    private fun switch(view: View) {
        view.rotate180Animation()
        viewModel.switch()
    }

    override fun onBackPressed(): Boolean {
        if (slidesView.isFirst) {
            return super.onBackPressed()
        } else {
            reset()
            sendInputView.focusWithKeyboard()
            return false
        }
    }

    private fun reset() {
        viewModel.reset()
        continueButton.isLoading = false
        continueButton.isEnabled = true
    }

    private fun next() {
        hideKeyboard()
        continueButton.isLoading = true
        lifecycleScope.launch {
            try {
                viewModel.next()
            } catch (e: Throwable) {
                inputErrorState()
                if (e is InsufficientFundsException) {
                    insufficientFundsDialog.show(wallet, e)
                }
            } finally {
                continueButton.isLoading = false
            }
        }
        analytics?.swapClick(
            jettonSymbolFrom = viewModel.jettonSymbolFrom,
            jettonSymbolTo = viewModel.jettonSymbolTo,
            native = true,
            providerName = viewModel.providerName,
        )
    }

    private fun applyQuoteState(state: SwapQuoteState) {
        reviewSendView.setValue(state.fromUnitsFormat)
        receiveInputView.setValue(state.toUnits)
        reviewReceiveView.setValue(state.toUnitsFormat)
        slippageView.value = CurrencyFormatter.formatPercent(state.slippage / 100)
        applyDetailsContainer(state)

        if (state.insufficientFunds != null) {
            postDelayed(1000) {
                insufficientFundsDialog.show(wallet, state.insufficientFunds)
                reset()
                sendInputView.focusWithKeyboard()
            }
        }
    }

    private fun applyDetailsContainer(state: SwapQuoteState) {
        setLineValue(R.id.details_provider, state.provider)
        setLineValue(R.id.details_rate, state.exchangeRate)
        applyFee(state)
    }

    private fun applyFee(state: SwapQuoteState) {
        setLineValue(R.id.details_fee, state.getFeeFormat(requireContext()))
        if (state.canUseBattery && state.canEditFeeMethod) {
            feeView.name = getString(Localization.fee).withInterpunct().withClickable(requireContext(), Localization.edit)
            feeView.setOnClickListener { selectFeeMethod(state) }
        } else {
            feeView.name = getString(Localization.fee)
            feeView.setOnClickListener(null)
        }
    }

    private fun selectFeeMethod(state: SwapQuoteState) {
        feeMethodSelector.width = 264.dp
        if (feeMethodSelector.isShowing) {
            return
        }

        feeMethodSelector.clearItems()
        state.feeOptions.forEach { fee ->
            feeMethodSelector.addFeeItem(fee, fee.id == state.selectedFee?.id) {
                viewModel.setFeeMethod(fee)
            }
        }
        feeMethodSelector.showPopupAboveRight(feeView)
    }

    private fun setLineValue(id: Int, value: CharSequence) {
        detailsContainerView.findViewById<ItemLineView>(id).value = value
    }

    override fun onResume() {
        super.onResume()
        sendInputView.focusWithKeyboard()
    }

    companion object {

        fun newInstance(
            wallet: WalletEntity,
            fromToken: String = "TON",
            toToken: String? = null,
        ): OmnistonScreen {
            val screen = OmnistonScreen(wallet)
            screen.setArgs(OmnistonArgs(fromToken, toToken))
            return screen
        }
    }
}