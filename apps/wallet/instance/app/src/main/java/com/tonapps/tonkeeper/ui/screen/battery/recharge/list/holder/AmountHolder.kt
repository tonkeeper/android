package com.tonapps.tonkeeper.ui.screen.battery.recharge.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.component.coin.CoinEditText
import com.tonapps.tonkeeper.ui.screen.battery.recharge.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentRedColor
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.wallet.localization.Localization

class AmountHolder(
    parent: ViewGroup,
    private val onValueChange: (Double) -> Unit
) : InputHolder<Item.Amount>(parent, R.layout.fragment_recharge_amount) {

    private val amountView = itemView.findViewById<CoinEditText>(R.id.amount)
    private val currencyView = itemView.findViewById<AppCompatTextView>(R.id.currency)
    private val availableView = itemView.findViewById<AppCompatTextView>(R.id.available)

    override val inputFieldView: View
        get() = amountView

    override fun onBind(item: Item.Amount) {
        amountView.doOnValueChange = { it ->
            if (it != item.value) {
                onValueChange(it)
            }
        }
        amountView.suffix = item.symbol

        if (amountView.decimals != item.decimals) {
            amountView.setDecimals(item.decimals)
        }

        currencyView.text = item.formattedCharges
        applyAvailable(item.formattedRemaining, item.formattedMinAmount, item.isInsufficientBalance, item.isLessThanMin)
        amountView.requestFocus()
        amountView.postOnAnimation { setValue(item.value) }
    }

    private fun setValue(value: Double) {
        if (value > 0 && value != amountView.getValue()) {
            amountView.setValue(value)
        }
    }

    private fun applyAvailable(
        formattedRemaining: CharSequence,
        formattedMinAmount: CharSequence,
        isInsufficientBalance: Boolean,
        isLessThanMin: Boolean
    ) {
        if (isInsufficientBalance) {
            availableView.setText(Localization.insufficient_balance)
            availableView.setTextColor(context.accentRedColor)
        } else if (isLessThanMin) {
            availableView.text = context.getString(Localization.minimum_amount, formattedMinAmount)
            availableView.setTextColor(context.accentRedColor)
        } else {
            availableView.text =
                context.getString(Localization.remaining_balance, formattedRemaining)
            availableView.setTextColor(context.textSecondaryColor)
        }
    }

}