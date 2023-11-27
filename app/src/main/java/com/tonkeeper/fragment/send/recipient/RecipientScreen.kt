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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        addressInput = view.findViewById(R.id.address)
        addressInput.doOnTextChange = { feature.requestAddressCheck(it) }
        addressInput.doOnIconClick = { parentScreen?.openCamera() }
        addressInput.doOnButtonClick = { paste() }

        commentInput = view.findViewById(R.id.comment)
        commentInput.doOnTextChange = {
            feature.setComment(it)
        }

        nextButton = view.findViewById(R.id.next)
        nextButton.setOnClickListener {
            next()
        }
    }

    override fun newUiState(state: RecipientScreenState) {
        setAddressState(state.addressState)

        parentFeature?.recipient = SendScreenFeature.Recipient(
            address = state.address,
            comment = state.comment,
            name = state.name
        )
    }

    private fun setAddressState(state: RecipientScreenState.AddressState) {
        nextButton.isEnabled = state == RecipientScreenState.AddressState.VALID

        if (state == RecipientScreenState.AddressState.LOADING) {
            addressInput.loading = true
            addressInput.error = false
            return
        }

        addressInput.loading = false
        addressInput.error = state == RecipientScreenState.AddressState.INVALID
    }

    private fun next() {
        parentScreen?.let {
            val subtitle = getString(R.string.to_address, feature.displayAddress)
            it.setSubtitle(subtitle)
            it.next()
        }
    }

    private fun paste() {
        context?.clipboardText()?.let {
            addressInput.text = it
        }
    }

    override fun onResume() {
        super.onResume()
        addressInput.focus()
    }
}