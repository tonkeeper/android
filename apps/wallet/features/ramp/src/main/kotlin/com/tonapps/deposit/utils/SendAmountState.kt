package com.tonapps.deposit.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.tonapps.core.components.sanitizeAmountInput
import com.tonapps.icu.Coins

@Stable
class SendAmountState internal constructor() {

    var textFieldValue by mutableStateOf(TextFieldValue(""))
        private set

    var decimals: Int = Coins.DEFAULT_DECIMALS
        internal set

    val value: String get() = textFieldValue.text

    fun onValueChange(newValue: String) {
        onTextFieldValueChange(textFieldValue.copy(text = newValue))
    }

    fun onTextFieldValueChange(newValue: TextFieldValue) {
        val sanitized = newValue.text.sanitizeAmountInput(decimals)
        val selection = if (sanitized.length != newValue.text.length) {
            TextRange(sanitized.length)
        } else {
            newValue.selection
        }
        textFieldValue = TextFieldValue(text = sanitized, selection = selection)
    }

    fun setAmount(amount: Coins) {
        val formatted = amount.value.stripTrailingZeros().toPlainString()
        textFieldValue = TextFieldValue(
            text = formatted,
            selection = TextRange(formatted.length),
        )
    }

    fun clear() {
        textFieldValue = TextFieldValue("")
    }

    companion object {
        val Saver: Saver<SendAmountState, String> = Saver(
            save = { it.value },
            restore = { saved ->
                SendAmountState().also {
                    it.textFieldValue = TextFieldValue(
                        text = saved,
                        selection = TextRange(saved.length),
                    )
                }
            },
        )
    }
}

@Composable
fun rememberSendAmountState(decimals: Int = Coins.DEFAULT_DECIMALS): SendAmountState {
    val state = rememberSaveable(saver = SendAmountState.Saver) { SendAmountState() }
    state.decimals = decimals
    return state
}
