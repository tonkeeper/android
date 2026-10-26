package com.tonapps.tonkeeper.ui.screen.staking

import android.content.Context
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.deposit.DepositFragment
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import uikit.dialog.modal.ModalDialog
import uikit.extensions.setMoonOnClickListener
import uikit.navigation.Navigation
import uikit.widget.TextHeaderView

class StakingInsufficientFundsDialog(
    context: Context,
    private val walletId: String,
) : ModalDialog(context, R.layout.dialog_staking_insufficient_funds) {

    private val textView = findViewById<TextHeaderView>(R.id.text)!!
    private val noteView = findViewById<AppCompatTextView>(R.id.note)!!
    private val depositButton = findViewById<Button>(R.id.deposit)!!

    init {
        findViewById<View>(R.id.close)!!.setMoonOnClickListener { dismiss() }
    }

    fun show(state: StakingInsufficientFundsUiState) {
        super.show()
        textView.descriptionView.text = buildString {
            append(context.getString(Localization.staking_insufficient_required, state.requiredFormat))
            append("\n")
            append(context.getString(Localization.staking_insufficient_wallet_balance, state.balanceFormat))
        }
        noteView.text = state.reserveHint
        noteView.isVisible = state.reserveHint != null
        depositButton.setMoonOnClickListener {
            Navigation.from(context)?.add(DepositFragment.create(walletId = walletId, analyticsFrom = DepositFlowFrom.Staking))
            dismiss()
        }
    }
}
