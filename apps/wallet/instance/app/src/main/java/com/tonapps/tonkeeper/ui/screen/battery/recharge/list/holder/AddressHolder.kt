package com.tonapps.tonkeeper.ui.screen.battery.recharge.list.holder

import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.extensions.clipboardText
import com.tonapps.tonkeeper.ui.screen.battery.recharge.list.Item
import com.tonapps.tonkeeperx.R
import uikit.extensions.hideKeyboard
import uikit.widget.InputView
import uikit.widget.RowLayout

class AddressHolder(
    parent: ViewGroup,
    private val onTextChange: (String) -> Unit,
    private val openAddressBook: () -> Unit,
) : InputHolder<Item.Address>(parent, R.layout.fragment_recharge_address) {

    private val inputView = itemView.findViewById<InputView>(R.id.address)
    private val addressActionsView = itemView.findViewById<RowLayout>(R.id.address_actions)
    private val pasteView = itemView.findViewById<AppCompatTextView>(R.id.paste)
    private val addressBookView = itemView.findViewById<AppCompatImageView>(R.id.address_book)

    override val inputFieldView: View
        get() = inputView

    override fun onBind(item: Item.Address) {
        inputView.doOnTextChange = { text ->
            if (text != item.value) {
                onTextChange(text)
                inputView.loading = text.isNotBlank()
                addressActionsView.visibility = if (text.isBlank()) View.VISIBLE else View.GONE
            } else {
                applyState(item)
            }
        }

        addressBookView.setOnClickListener {
            openAddressBook()
        }

        pasteView.setOnClickListener {
            inputView.text = context.clipboardText()
        }

        inputView.postOnAnimation { setValue(item) }
        applyState(item)
    }

    private fun setValue(item: Item.Address) {
        if (item.value.isNotBlank() && item.value != inputView.text) {
            inputView.text = item.value
        }
        applyState(item)
    }

    private fun applyState(item: Item.Address) {
        inputView.loading = item.state == Item.Address.State.Loading
        inputView.error = item.state == Item.Address.State.Error
        addressActionsView.visibility = if (inputView.text.isBlank()) View.VISIBLE else View.GONE
    }

    override fun onUnbind() {
        super.onUnbind()
        inputView.hideKeyboard()
    }
}