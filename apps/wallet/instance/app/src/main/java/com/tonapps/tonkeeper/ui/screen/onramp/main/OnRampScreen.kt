package com.tonapps.tonkeeper.ui.screen.onramp.main

import android.content.Context
import android.os.Bundle
import com.tonapps.log.L
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.allViews
import com.tonapps.tonkeeper.ui.component.PaymentTypeView
import com.tonapps.tonkeeper.ui.screen.onramp.main.view.CurrencyInputView
import com.tonapps.tonkeeperx.R
import com.tonapps.blockchain.model.legacy.WalletEntity
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import androidx.core.view.updateLayoutParams
import androidx.core.widget.NestedScrollView
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.tonkeeper.helper.TwinInput
import com.tonapps.tonkeeper.koin.remoteConfig
import com.tonapps.tonkeeper.ui.screen.onramp.main.state.OnRampPaymentMethodState
import com.tonapps.tonkeeper.ui.screen.onramp.main.state.UiState
import com.tonapps.tonkeeper.ui.screen.purchase.PurchaseScreen
import com.tonapps.uikit.list.ListCell
import uikit.extensions.bottomBarsOffset
import uikit.extensions.dp
import uikit.extensions.drawable
import uikit.extensions.reject
import uikit.extensions.rotate180Animation
import uikit.widget.ColumnLayout

class OnRampScreen(wallet: WalletEntity): BaseOnRampScreen(wallet) {

    private val source: String by lazy {
        requireArguments().getString(ARG_SOURCE) ?: ""
    }

    private lateinit var sellInput: CurrencyInputView
    private lateinit var buyInput: CurrencyInputView
    private lateinit var priceView: AppCompatTextView
    private lateinit var priceReversedView: AppCompatTextView
    private lateinit var paymentView: ColumnLayout
    private lateinit var confirmPageView: NestedScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analytics?.onRampOpen(source)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        confirmPageView = view.findViewById(R.id.confirm_page)

        sellInput = view.findViewById(R.id.sell_input)
        sellInput.focusWithKeyboard()
        sellInput.doOnTextChange = viewModel::updateSendInput
        sellInput.doOnCurrencyClick = { viewModel.pickCurrency(TwinInput.Type.Send) }
        sellInput.doOnFocusChange = { hasFocus ->
            if (hasFocus) {
                viewModel.updateFocusInput(TwinInput.Type.Send)
            }
        }

        buyInput = view.findViewById(R.id.buy_input)
        buyInput.setValueScale(3)
        buyInput.setPrefix(CurrencyInputView.EQUALS_SIGN_PREFIX)
        buyInput.doOnTextChange = viewModel::updateReceiveInput
        buyInput.doOnCurrencyClick = { viewModel.pickCurrency(TwinInput.Type.Receive) }
        buyInput.doOnFocusChange = { hasFocus ->
            if (hasFocus) {
                viewModel.updateFocusInput(TwinInput.Type.Receive)
            }
        }

        priceView = view.findViewById(R.id.price)
        priceReversedView = view.findViewById(R.id.price_reversed)

        reviewSend.setOnClickListener {
            viewModel.reset()
            sellInput.focusWithKeyboard()
        }

        reviewReceive.setOnClickListener {
            viewModel.reset()
            buyInput.focusWithKeyboard()
        }

        view.findViewById<View>(R.id.switch_button).setOnClickListener(::switch)

        paymentView = view.findViewById(R.id.payment)

        sellInput.doOnEditorAction = { actionId ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                if (sellInput.isEmpty) {
                    buyInput.focusWithKeyboard()
                } else if (button.isEnabled) {
                    requestAvailableProviders()
                } else {
                    minMaxView.reject()
                }
                true
            } else {
                false
            }
        }

        buyInput.doOnEditorAction = { actionId ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                if (buyInput.isEmpty) {
                    sellInput.focusWithKeyboard()
                } else if (button.isEnabled) {
                    requestAvailableProviders()
                } else {
                    minMaxView.reject()
                }
                true
            } else {
                false
            }
        }

        collectFlow(viewModel.sendOutputCurrencyFlow, sellInput::setCurrency)
        collectFlow(viewModel.sendOutputValueFlow, sellInput::setValue)

        collectFlow(viewModel.receiveOutputCurrencyFlow, buyInput::setCurrency)
        collectFlow(viewModel.receiveOutputValueFlow, buyInput::setValue)
        collectFlow(viewModel.sendValueFlow) {
            sellInput.setValue(it, true)
        }

        collectFlow(viewModel.inputPrefixFlow, ::applyPrefix)
        collectFlow(viewModel.rateFormattedFlow, ::applyRateFormatted)
        collectFlow(viewModel.balanceUiStateFlow, ::applyBalanceState)
        collectFlow(viewModel.paymentMethodUiStateFlow, ::applyPaymentMethodState)
        collectFlow(viewModel.requestFocusFlow) { inputType ->
            when (inputType) {
                TwinInput.Type.Send -> sellInput.focusWithKeyboard()
                TwinInput.Type.Receive -> buyInput.focusWithKeyboard()
            }
        }

        view.findViewById<View>(R.id.edit).setOnClickListener {
            viewModel.reset()
            sellInput.focusWithKeyboard()
        }
    }

    private fun applyPrefix(inputType: TwinInput.Type) {
        if (inputType == TwinInput.Type.Send) {
            sellInput.setPrefix(CurrencyInputView.EQUALS_SIGN_PREFIX)
            buyInput.setPrefix(null)
        } else {
            sellInput.setPrefix(null)
            buyInput.setPrefix(CurrencyInputView.EQUALS_SIGN_PREFIX)
        }
    }

    private fun switch(view: View) {
        view.rotate180Animation()
        viewModel.switch()
    }

    private fun applyRateFormatted(state: UiState.RateFormatted) {
        if (state.from.isNullOrBlank()) {
            priceView.visibility = View.GONE
        } else {
            priceView.visibility = View.VISIBLE
            priceView.text = state.from
        }

        if (state.to.isNullOrBlank()) {
            priceReversedView.visibility = View.GONE
        } else {
            priceReversedView.visibility = View.VISIBLE
            priceReversedView.text = state.to
        }

        updateDivider()
    }

    private fun applyBalanceState(state: UiState.Balance) {
        if (state.insufficientBalance) {
            sellInput.setInsufficientBalance()
        } else {
            sellInput.setTokenBalance(state.balance, state.remainingFormat)
        }
    }

    private fun createPaymentTypeView(method: OnRampPaymentMethodState.Method): PaymentTypeView {
        val view = PaymentTypeView(requireContext())
        view.title = method.title
        view.subtitle = method.subtitle
        view.tag = method.type
        view.setOnClickListener {
            viewModel.setSelectedPaymentMethod(method.type)
        }
        view.setRounding(method.country.equals("ru", true) || !method.isCard)
        view.setIcon(method.icon)
        return view
    }

    private fun createPaymentMethodViews(methods: List<OnRampPaymentMethodState.Method>) {
        if (methods.isEmpty()) {
            paymentView.visibility = View.GONE
            confirmPageView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = 0
            }
            return
        } else {
            paymentView.visibility = View.VISIBLE
        }

        if (paymentView.childCount > 1) {
            paymentView.removeViews(1, paymentView.childCount - 1)
        }

        for ((index, method) in methods.withIndex()) {
            val position = ListCell.getPosition(methods.size, index)
            val view = createPaymentTypeView(method)
            view.background = position.drawable(requireContext())
            if (method.subtitle != null) {
                paymentView.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 76.dp))
            } else {
                paymentView.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 56.dp))
            }
        }

        confirmPageView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = confirmPageView.bottomBarsOffset
        }
    }

    private fun applyPaymentMethodState(state: OnRampPaymentMethodState) {
        createPaymentMethodViews(state.methods)
        for (view in paymentView.allViews) {
            if (view is PaymentTypeView) {
                view.isChecked = view.tag.equals(state.selectedType)
            }
        }
    }

    companion object {

        private const val ARG_SOURCE = "source"

        fun newInstance(context: Context, wallet: WalletEntity, source: String): BaseFragment {
            return if (context.remoteConfig?.nativeOnrmapEnabled == true) {
                OnRampScreen(wallet).apply {
                    putStringArg(ARG_SOURCE, source)
                }
            } else {
                PurchaseScreen.newInstance(wallet, source)
            }
        }
    }
}
