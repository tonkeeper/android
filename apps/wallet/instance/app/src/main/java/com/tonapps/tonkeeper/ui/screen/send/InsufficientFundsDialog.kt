package com.tonapps.tonkeeper.ui.screen.send

import android.content.Context
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.Button
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.extensions.getTitle
import com.tonapps.tonkeeper.ui.screen.battery.BatteryScreen
import com.tonapps.tonkeeper.ui.screen.purchase.PurchaseScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import uikit.dialog.modal.ModalDialog
import uikit.navigation.Navigation
import uikit.widget.TextHeaderView

class InsufficientFundsDialog(context: Context): ModalDialog(context, R.layout.dialog_insufficient_funds) {

    private val navigation: Navigation?
        get() = Navigation.from(context)

    private val textView = findViewById<TextHeaderView>(R.id.text)!!
    private val batteryButton = findViewById<Button>(R.id.battery)!!
    private val tonButton = findViewById<Button>(R.id.ton)!!

    init {
        findViewById<View>(R.id.close)!!.setOnClickListener { dismiss() }
    }

    fun show(wallet: WalletEntity, balance: Coins, required: Coins, withRechargeBattery: Boolean, singleWallet: Boolean) {
        super.show()
        applyWalletTitle(wallet.label, singleWallet)
        applyDescription(balance, required, withRechargeBattery)
        batteryButton.visibility = if (withRechargeBattery) View.VISIBLE else View.GONE

        tonButton.setOnClickListener {
            navigation?.add(PurchaseScreen.newInstance(wallet))
            dismiss()
        }

        batteryButton.setOnClickListener {
            navigation?.add(BatteryScreen.newInstance(wallet))
            dismiss()
        }
    }

    private fun applyWalletTitle(label: Wallet.Label, singleWallet: Boolean) {
        if (!singleWallet) {
            val walletTitle = label.getTitle(context, textView.titleView, 16)
            val spannable = SpannableStringBuilder(context.getString(Localization.insufficient_balance_in_wallet))
            spannable.append(" ")
            spannable.append(walletTitle)

            textView.titleView.text = spannable
        } else {
            textView.titleView.setText(Localization.insufficient_balance_title)
        }
    }

    private fun applyDescription(balance: Coins, required: Coins, withRechargeBattery: Boolean) {
        val balanceFormat = CurrencyFormatter.format("TON", balance, 9).withCustomSymbol(context)
        val requiredFormat = CurrencyFormatter.format("TON", required, 9).withCustomSymbol(context)

        val resId = if (withRechargeBattery) Localization.insufficient_balance_fees else Localization.insufficient_balance_default
        textView.descriptionView.text = context.getString(resId, requiredFormat, balanceFormat)
    }
}