package com.tonapps.deposit.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.tonapps.deposit.screens.provider.ProviderItem
import com.tonapps.icu.Coins

sealed interface AmountError {
    data class BelowMin(val minAmount: Coins) : AmountError
    data class AboveMax(val maxAmount: Coins) : AmountError
}

@Stable
class ProviderAmountState internal constructor(
    private var provider: ProviderItem? = null,
) {
    var value by mutableStateOf(TextFieldValue())
        private set

    val text: String
        get() = value.text

    var coins: Coins by mutableStateOf(Coins.ZERO)
        private set

    var error: AmountError? by mutableStateOf(null)
        private set

    val hasAmount: Boolean
        get() = coins.isPositive

    val isValid: Boolean
        get() = hasAmount && error == null

    fun onValueChange(newValue: TextFieldValue) {
        val sanitized = sanitizeAmount(newValue.text)
        val selection = if (sanitized.length != newValue.text.length) {
            TextRange(sanitized.length)
        } else {
            newValue.selection
        }
        value = TextFieldValue(text = sanitized, selection = selection)
        coins = parseAmount(sanitized)
        error = validate(coins, provider)
    }

    fun onTextChange(newText: String) {
        onValueChange(TextFieldValue(text = newText, selection = TextRange(newText.length)))
    }

    internal fun updateProvider(newProvider: ProviderItem?) {
        provider = newProvider
        error = validate(coins, newProvider)
    }

    companion object {
        val Saver: Saver<ProviderAmountState, String> = Saver(
            save = { it.text },
            restore = { saved -> ProviderAmountState().also { it.onTextChange(saved) } },
        )

        private fun sanitizeAmount(input: String): String {
            val filtered = input.filter { it.isDigit() || it == '.' || it == ',' }
            if (filtered.isEmpty()) return ""

            val sb = StringBuilder()
            var hasSeparator = false
            for (c in filtered) {
                if (c == '.' || c == ',') {
                    if (!hasSeparator) {
                        hasSeparator = true
                        sb.append(c)
                    }
                } else {
                    sb.append(c)
                }
            }

            // prepend 0 if starts with separator: ".123" -> "0.123"
            if (sb.isNotEmpty() && (sb[0] == '.' || sb[0] == ',')) {
                sb.insert(0, '0')
            }

            return sb.toString()
        }

        private fun parseAmount(text: String): Coins {
            return try {
                val coins = Coins.of(text)
                if (coins.isPositive) coins else Coins.ZERO
            } catch (_: Throwable) {
                Coins.ZERO
            }
        }

        private fun validate(coins: Coins, provider: ProviderItem?): AmountError? {
            if (!coins.isPositive || provider == null) return null
            return when {
                provider.minAmount.isPositive && coins < provider.minAmount -> AmountError.BelowMin(provider.minAmount)
                provider.maxAmount != null && coins > provider.maxAmount -> AmountError.AboveMax(provider.maxAmount)
                else -> null
            }
        }
    }
}

@Composable
fun rememberProviderAmountState(
    provider: ProviderItem?,
): ProviderAmountState {
    val state = rememberSaveable(saver = ProviderAmountState.Saver) { ProviderAmountState(provider) }
    LaunchedEffect(provider) {
        state.updateProvider(provider)
    }
    return state
}
