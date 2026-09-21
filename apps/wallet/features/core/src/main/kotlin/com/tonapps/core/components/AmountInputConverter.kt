package com.tonapps.core.components

import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.tonapps.chainkit.core.chain.model.num.BaseUnit
import com.tonapps.chainkit.core.chain.model.num.CoinValue
import com.tonapps.chainkit.core.chain.model.num.toFiatAmount
import com.tonapps.wallet.data.multichain.account.AccountWithDetails

/**
 * Converts an entered amount between the token and its fiat equivalent so both views stay in sync.
 *
 * The field can be entered in either currency (`inFiat`):
 *  - [tokenUnit] gives the full-precision token [BaseUnit] to actually send.
 *  - [swapText] re-expresses the entered text in the other currency for the field.
 *  - [maxText] gives a maximum amount as field text in the current currency.
 *
 * Text conversions are rounded for display only — sending always uses full precision.
 * [formatDecimals] caps the decimals of the token text produced from fiat input. Fiat text is
 * always rounded down, so it never parses back above the amount it was produced from.
 */
class AmountInputConverter(
    account: AccountWithDetails,
    private val formatDecimals: Int,
) {

    private val asset = account.asset.value
    private val rate = account.rate?.value

    val hasRate: Boolean get() = rate != null

    fun tokenUnit(input: String, inFiat: Boolean): BaseUnit? = runCatching {
        val normalized = input.replace(',', '.')
        if (inFiat) {
            asset.fiatToToken(normalized, rate ?: return@runCatching null)
        } else {
            asset.decimals.displayUnit(normalized).toBaseUnit()
        }
    }.getOrNull()

    fun positiveTokenUnit(input: String, inFiat: Boolean): BaseUnit? {
        return tokenUnit(input, inFiat)?.takeIf { it.isPositive }
    }

    fun swapText(input: String, toFiat: Boolean): String {
        if (input.isBlank()) {
            return ""
        }
        val rate = rate ?: return ""
        val normalized = input.replace(',', '.')
        return runCatching {
            if (toFiat) {
                fiatText(asset.decimals.displayUnit(normalized)).orEmpty()
            } else {
                asset.fiatToToken(normalized, rate).amountText(formatDecimals)
            }
        }.getOrDefault("")
    }

    fun maxText(max: BaseUnit, inFiat: Boolean): String? {
        return if (inFiat) {
            fiatText(max)
        } else {
            max.toDisplayUnit().fmt()
        }
    }

    private fun fiatText(value: CoinValue): String? {
        val rate = rate ?: return null
        return value.toFiatAmount(rate, RoundingMode.FLOOR)
    }
}
