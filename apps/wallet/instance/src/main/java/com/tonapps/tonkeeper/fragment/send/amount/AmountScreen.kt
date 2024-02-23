package com.tonapps.tonkeeper.fragment.send.amount

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.viewModels
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.fragment.send.pager.PagerScreen
import com.tonapps.tonkeeper.fragment.send.popup.SelectTokenPopup
import com.tonapps.tonkeeper.fragment.send.view.AmountInput
import com.tonapps.uikit.color.buttonPrimaryBackgroundColor
import com.tonapps.uikit.color.buttonSecondaryBackgroundColor
import com.tonapps.uikit.color.constantRedColor
import com.tonapps.uikit.color.textSecondaryColor
import uikit.extensions.focusWithKeyboard
import uikit.extensions.hideKeyboard

class AmountScreen: PagerScreen<AmountScreenState, AmountScreenEffect, AmountScreenFeature>(R.layout.fragment_send_amount) {

    companion object {
        fun newInstance() = AmountScreen()
    }

    override val feature: AmountScreenFeature by viewModels()

    private val selectTokenPopup: SelectTokenPopup by lazy {
        val popup = SelectTokenPopup(requireContext())
        popup.doOnSelectJetton = { jetton ->
            feature.selectJetton(jetton)
            forceSetAmount(0f)
        }
        popup
    }

    private lateinit var tokenView: AppCompatTextView
    private lateinit var valueView: AmountInput
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
        valueView.setMaxLength(9)

        valueCurrencyView = view.findViewById(R.id.value_currency)

        rateView = view.findViewById(R.id.rate)
        availableView = view.findViewById(R.id.available)

        maxButton = view.findViewById(R.id.max)
        maxButton.setOnClickListener { setMaxValue() }

        continueButton = view.findViewById(R.id.continue_action)
        continueButton.setOnClickListener { next() }

        feature.setValue(0f)
    }

    fun forceSetJetton(address: String?) {
        address?.let { feature.selectJetton(it) }
    }

    fun forceSetAmount(amount: Float) {
        val text = if (0f >= amount) {
            ""
        } else {
            amount.toString()
        }
        val editable = valueView.text ?: return
        editable.replace(0, editable.length, text)
    }

    private fun getValue(): Float {
        return valueView.text.toString().toFloatOrNull() ?: 0f
    }

    private fun next() {
        sendFeature.setAmount(valueView.text.toString())
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
            availableView.setText(Localization.insufficient_balance)
            availableView.setTextColor(requireContext().constantRedColor)
        } else if (state.remaining != "") {
            availableView.text = getString(Localization.remaining_balance, state.remaining)
            availableView.setTextColor(requireContext().textSecondaryColor)
        } else {
            availableView.text = getString(Localization.available_balance, state.available)
            availableView.setTextColor(requireContext().textSecondaryColor)
        }

        continueButton.isEnabled = state.canContinue

        if (state.maxActive) {
            maxButton.background.setTint(requireContext().buttonPrimaryBackgroundColor)
        } else {
            maxButton.background.setTint(requireContext().buttonSecondaryBackgroundColor)
        }

        sendFeature.setMax(state.maxActive)
    }

    override fun onVisibleChange(visible: Boolean) {
        super.onVisibleChange(visible)
        if (visible) {
            sendFeature.setAmountHeader(requireContext())
            valueView.focusWithKeyboard()
        } else {
            valueView.hideKeyboard()
        }
    }
}