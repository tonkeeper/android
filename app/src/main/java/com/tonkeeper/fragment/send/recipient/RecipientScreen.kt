package com.tonkeeper.fragment.send.recipient

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.viewModels
import com.tonkeeper.R
import com.tonkeeper.api.shortAddress
import com.tonkeeper.extensions.clipboardText
import com.tonkeeper.fragment.send.SendScreenFeature
import com.tonkeeper.fragment.send.pager.PagerScreen
import ton.TonAddress
import uikit.widget.InputView

class RecipientScreen: PagerScreen<RecipientScreenState, RecipientScreenEffect, RecipientScreenFeature>(R.layout.fragment_send_recipient) {

    companion object {
        fun newInstance() = RecipientScreen()
    }

    override val feature: RecipientScreenFeature by viewModels()

    private lateinit var addressInput: InputView
    private lateinit var commentInput: InputView
    private lateinit var nextButton: Button

    private var checkAddressRunnable = Runnable { checkAddress() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        addressInput = view.findViewById(R.id.address)
        addressInput.doOnTextChange = {
            if (it.isEmpty()) {
                addressInput.error = false
            } else {
                addressInput.loading = true
                postDelayed(500, checkAddressRunnable)
            }
        }
        addressInput.doOnButtonClick = {
            paste()
        }

        commentInput = view.findViewById(R.id.comment)

        nextButton = view.findViewById(R.id.next)
        nextButton.setOnClickListener {
            next()
        }
    }

    private fun next() {
        val address = addressInput.text
        parentScreen?.let {
            setFlowRecipient()

            it.next()
            val subtitle = getString(R.string.to_address, address.shortAddress)
            it.setSubtitle(subtitle)
        }
    }

    private fun setFlowRecipient() {
        val address = addressInput.text
        val comment = commentInput.text

        parentFeature?.recipient = SendScreenFeature.Recipient(
            address = address,
            comment = comment
        )
    }

    private fun paste() {
        context?.clipboardText()?.let {
            addressInput.text = it
        }
    }

    private fun checkAddress() {
        addressInput.loading = false
        val value = addressInput.text
        if (value.isEmpty()) {
            addressInput.error = false
            nextButton.isEnabled = false
            return
        }
        val isValid = TonAddress.isValid(value)
        addressInput.error = isValid.not()
        nextButton.isEnabled = isValid
    }

    override fun onResume() {
        super.onResume()
        addressInput.focus()
    }


    override fun newUiState(state: RecipientScreenState) {

    }
}