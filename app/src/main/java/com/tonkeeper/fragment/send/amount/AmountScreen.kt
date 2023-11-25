package com.tonkeeper.fragment.send.amount

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.tonkeeper.R
import com.tonkeeper.fragment.send.SendScreenFeature
import com.tonkeeper.fragment.send.pager.PagerScreen
import kotlinx.coroutines.launch
import uikit.extensions.focusWidthKeyboard

class AmountScreen: PagerScreen<AmountScreenState, AmountScreenEffect, AmountScreenFeature>(R.layout.fragment_send_amount) {

    companion object {
        fun newInstance() = AmountScreen()
    }

    override val feature: AmountScreenFeature by viewModels()

    private lateinit var valueView: AppCompatEditText
    private lateinit var rateView: AppCompatTextView
    private lateinit var availableView: AppCompatTextView
    private lateinit var maxButton: Button
    private lateinit var continueButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        valueView = view.findViewById(R.id.value)
        valueView.doOnTextChanged { _, _, _, _ ->
            feature.setValue(getValue())
        }

        rateView = view.findViewById(R.id.rate)
        availableView = view.findViewById(R.id.available)

        maxButton = view.findViewById(R.id.max)
        maxButton.setOnClickListener { setMaxValue() }

        continueButton = view.findViewById(R.id.continue_action)
        continueButton.setOnClickListener { next() }
    }

    private fun getValue(): Float {
        return valueView.text.toString().toFloatOrNull() ?: 0f
    }

    private fun next() {
        setFlowAmount()
        parentScreen?.next()
    }

    private fun setFlowAmount() {
        parentFeature?.amount = SendScreenFeature.Amount(getValue())
    }

    private fun setMaxValue() {
        lifecycleScope.launch {
            val maxValue = feature.getMaxValue()
            val text = valueView.text ?: return@launch
            text.replace(0, text.length, maxValue.toString())
        }
    }

    override fun newUiState(state: AmountScreenState) {
        rateView.text = state.rate

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
    }

    override fun onVisibleChange(visible: Boolean) {
        super.onVisibleChange(visible)
        if (visible) {
            valueView.focusWidthKeyboard()
        }
    }
}