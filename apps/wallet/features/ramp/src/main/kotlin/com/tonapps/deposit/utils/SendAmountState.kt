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
        val sanitized = sanitizeAmount(newValue.text, decimals)
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

        private fun sanitizeAmount(input: String, maxDecimals: Int): String {
            val filtered = input.filter { it.isDigit() || it == '.' || it == ',' }
            if (filtered.isEmpty()) return ""

            val sb = StringBuilder()
            var hasSeparator = false
            var decimalCount = 0
            for (c in filtered) {
                if (c == '.' || c == ',') {
                    if (!hasSeparator) {
                        hasSeparator = true
                        sb.append(c)
                    }
                } else {
                    if (hasSeparator) {
                        if (decimalCount >= maxDecimals) continue
                        decimalCount++
                    }
                    sb.append(c)
                }
            }

            if (sb.isNotEmpty() && (sb[0] == '.' || sb[0] == ',')) {
                sb.insert(0, '0')
            }

            return sb.toString()
        }
    }
}

@Composable
fun rememberSendAmountState(decimals: Int = Coins.DEFAULT_DECIMALS): SendAmountState {
    val state = rememberSaveable(saver = SendAmountState.Saver) { SendAmountState() }
    state.decimals = decimals
    return state
}
