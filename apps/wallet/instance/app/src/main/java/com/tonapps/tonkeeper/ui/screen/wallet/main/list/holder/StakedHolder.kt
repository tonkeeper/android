package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.doOnDetach
import androidx.core.view.doOnLayout
import androidx.lifecycle.viewModelScope
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.helper.DateHelper
import com.tonapps.tonkeeper.ui.screen.staking.viewer.StakeViewerScreen
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.extensions.drawable
import uikit.navigation.Navigation
import uikit.widget.FrescoView
import kotlin.time.Duration.Companion.seconds

class StakedHolder(parent: ViewGroup): Holder<Item.Stake>(parent, R.layout.view_wallet_staked) {

    private var tickerJob: Job? = null

    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val nameView = findViewById<AppCompatTextView>(R.id.name)
    private val balanceView = findViewById<AppCompatTextView>(R.id.balance)
    private val balanceFiatView = findViewById<AppCompatTextView>(R.id.balance_fiat)
    private val messageView = findViewById<AppCompatTextView>(R.id.message)

    init {
        messageView.doOnDetach { stopTicker() }
    }

    override fun onBind(item: Item.Stake) {
        stopTicker()

        itemView.background = item.position.drawable(context)
        iconView.setImageURI(item.iconUri, null)
        nameView.text = item.poolName

        balanceView.text = if (item.hiddenBalance) {
            HIDDEN_BALANCE
        } else {
            item.balanceFormat.withCustomSymbol(context)
        }

        balanceFiatView.text = if (item.hiddenBalance) {
            HIDDEN_BALANCE
        } else {
            item.fiatFormat.withCustomSymbol(context)
        }

        itemView.setOnClickListener {
            Navigation.from(context)?.add(StakeViewerScreen.newInstance(item.wallet, item.poolAddress, item.poolName))
        }

        if (item.readyWithdraw > Coins.ZERO) {
            messageView.visibility = View.VISIBLE
            messageView.text = context.getString(Localization.staking_ready_withdraw, item.readyWithdrawFormat)
        } else if (item.pendingDeposit > Coins.ZERO) {
            startTicker(Localization.staking_pending_deposit, item.pendingDepositFormat, item.cycleEnd)
        } else if (item.pendingWithdraw > Coins.ZERO) {
            startTicker(Localization.staking_pending_withdraw, item.pendingWithdrawFormat, item.cycleEnd)
        } else {
            messageView.visibility = View.GONE
        }
    }

    private fun startTicker(resId: Int, format: CharSequence, timestamp: Long) {
        setMessageTimer(resId, format, DateHelper.formatCycleEnd(timestamp))
        messageView.visibility = View.VISIBLE

        if (lifecycleScope == null) {
            messageView.doOnLayout { startTicker(resId, format, timestamp) }
            return
        }

        tickerJob = lifecycleScope?.launch(Dispatchers.IO) {
            while (true) {
                withContext(Dispatchers.Main) {
                    setMessageTimer(resId, format, DateHelper.formatCycleEnd(timestamp))
                }
                delay(1.seconds)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private  fun setMessageTimer(
        resId: Int,
        format: CharSequence,
        date: String
    ) {
        messageView.text = context.getString(resId, format, date)
    }

    override fun onUnbind() {
        stopTicker()
        super.onUnbind()
    }

}