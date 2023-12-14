package com.tonkeeper.fragment.send.amount

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.tonkeeper.App
import com.tonkeeper.R
import com.tonkeeper.core.transaction.TransactionHelper
import com.tonkeeper.fragment.send.SendScreenFeature
import com.tonkeeper.fragment.send.pager.PagerScreen
import com.tonkeeper.fragment.send.popup.SelectTokenPopup
import kotlinx.coroutines.launch
import ton.SupportedTokens
import uikit.extensions.focusWidthKeyboard
import uikit.extensions.hideKeyboard
import uikit.widget.LoaderView

class AmountScreen: PagerScreen<AmountScreenState, AmountScreenEffect, AmountScreenFeature>(R.layout.fragment_send_amount) {

    companion object {
        fun newInstance() = AmountScreen()
    }

    override val feature: AmountScreenFeature by viewModels()

    private val selectTokenPopup: SelectTokenPopup by lazy {
        val popup = SelectTokenPopup(requireContext())
        popup.doOnSelectJetton = { jetton ->
            feature.selectJetton(jetton)
        }
        popup
    }

    private lateinit var tokenView: AppCompatTextView
    private lateinit var valueView: AppCompatEditText
    private lateinit var valueCurrencyView: AppCompatTextView
    private lateinit var rateView: AppCompatTextView
    private lateinit var availableView: AppCompatTextView
    private lateinit var maxButton: Button
    private lateinit var continueButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tokenView = view.findViewById(R.id.token)
        tokenView.setOnClickListener { selectTokenPopup.show(it) }

        valueView = view.findViewById(R.id.value)
        valueView.doOnTextChanged { _, _, _, _ ->
            feature.setValue(getValue())
        }

        valueCurrencyView = view.findViewById(R.id.value_currency)

        rateView = view.findViewById(R.id.rate)
        availableView = view.findViewById(R.id.available)

        maxButton = view.findViewById(R.id.max)
        maxButton.setOnClickListener { setMaxValue() }

        continueButton = view.findViewById(R.id.continue_action)
        continueButton.setOnClickListener { next() }

        feature.setValue(0f)
    }

    fun forceSetAmount(amount: Float) {
        valueView.setText(amount.toString())
    }

    private fun getValue(): Float {
        return valueView.text.toString().toFloatOrNull() ?: 0f
    }

    private fun next() {
        sendFeature.setAmount(getValue())
        sendFeature.nextPage()
    }

    private fun setMaxValue() {
        val maxValue = feature.currentBalance
        val text = valueView.text ?: return
        text.replace(0, text.length, maxValue.toString())
    }

    override fun newUiState(state: AmountScreenState) {
        rateView.text = state.rate

        if (1 >= state.jettons.size) {
            tokenView.visibility = View.GONE
        } else {
            tokenView.visibility = View.VISIBLE
            tokenView.text = state.selectedToken
            selectTokenPopup.selectedJetton = state.selectedJetton
            selectTokenPopup.jettons = state.jettons
        }

        sendFeature.setJetton(state.selectedJetton)

        valueCurrencyView.text = state.selectedToken

        if (state.insufficientBalance) {
            availableView.setText(R.string.insufficient_balance)
            availableView.setTextColor(getColor(uikit.R.color.constantRed))
        } else if (state.remaining != "") {
            availableView.text = getString(R.string.remaining_balance, state.remaining)
            availableView.setTextColor(getColor(uikit.R.color.textSecondary))
        } else {
            availableView.text = getString(R.string.available_balance, state.available)
            availableView.setTextColor(getColor(uikit.R.color.textSecondary))
        }

        continueButton.isEnabled = state.canContinue

        if (state.maxActive) {
            maxButton.background.setTint(getColor(uikit.R.color.buttonPrimaryBackground))
        } else {
            maxButton.background.setTint(getColor(uikit.R.color.buttonSecondaryBackground))
        }

        sendFeature.setMax(state.maxActive)
    }

    override fun onVisibleChange(visible: Boolean) {
        super.onVisibleChange(visible)
        if (visible) {
            sendFeature.setAmountHeader(requireContext())
            valueView.focusWidthKeyboard()
        } else {
            valueView.hideKeyboard()
        }
    }
}