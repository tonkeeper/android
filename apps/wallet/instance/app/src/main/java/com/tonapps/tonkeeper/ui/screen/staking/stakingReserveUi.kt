package com.tonapps.tonkeeper.ui.screen.staking

import android.view.View
import androidx.core.view.isVisible
import com.tonapps.tonkeeper.view.TransactionDetailView
import uikit.extensions.dp
import uikit.extensions.setMoonOnClickListener
import uikit.widget.BalloonTooltip

private val TOOLTIP_OFFSET = 4.dp

fun showStakingTooltip(anchor: View, message: String) {
    var tooltip: BalloonTooltip? = null
    tooltip = BalloonTooltip.show(
        anchorView = anchor,
        messageText = message,
        placement = BalloonTooltip.Placement.BOTTOM,
        style = BalloonTooltip.Style.PLAIN,
        offset = TOOLTIP_OFFSET,
        onClickListener = { tooltip?.dismiss() },
    )
}

fun TransactionDetailView.applyStakingReserve(state: StakingReserveUiState?) {
    isVisible = state != null
    if (state == null) {
        return
    }
    value = state.amountFormat
    description = state.fiatFormat
    titleInfoView.isVisible = true
    titleInfoView.setMoonOnClickListener { showStakingTooltip(it, state.hint) }
}
