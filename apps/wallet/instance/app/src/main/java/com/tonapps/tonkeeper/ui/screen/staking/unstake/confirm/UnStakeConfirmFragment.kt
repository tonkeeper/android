package com.tonapps.tonkeeper.ui.screen.staking.unstake.confirm

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import com.tonapps.extensions.short12
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.extensions.getTitle
import com.tonapps.tonkeeper.ui.screen.staking.unstake.UnStakeScreen
import com.tonapps.tonkeeper.view.TransactionDetailView
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.staking.StakingPool
import com.tonapps.wallet.data.staking.entities.PoolEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uikit.extensions.collectFlow
import uikit.widget.FrescoView
import uikit.widget.ProcessTaskView

class UnStakeConfirmFragment: UnStakeScreen.ChildFragment(R.layout.fragment_unstake_confirm) {

    private lateinit var iconView: FrescoView
    private lateinit var walletView: TransactionDetailView
    private lateinit var recipientView: TransactionDetailView
    private lateinit var amountView: TransactionDetailView
    private lateinit var feeView: TransactionDetailView
    private lateinit var apyView: TransactionDetailView
    private lateinit var button: Button
    private lateinit var taskView: ProcessTaskView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        iconView = view.findViewById(R.id.icon)
        iconView.setCircular()

        walletView = view.findViewById(R.id.review_wallet)
        recipientView = view.findViewById(R.id.review_recipient)
        amountView = view.findViewById(R.id.review_amount)

        feeView = view.findViewById(R.id.review_fee)
        feeView.setLoading()

        apyView = view.findViewById(R.id.review_apy)

        button = view.findViewById(R.id.button)
        taskView = view.findViewById(R.id.task)

        button.setOnClickListener { unStake() }

        collectFlow(unStakeViewModel.walletFlow) { wallet ->
            walletView.value = wallet.label.getTitle(requireContext(), walletView.valueView, 12)
        }

        collectFlow(unStakeViewModel.poolFlow, ::applyPool)
        collectFlow(unStakeViewModel.taskStateFlow, ::setTaskState)

        collectFlow(unStakeViewModel.amountFormatFlow) { amountFormat ->
            amountView.value = amountFormat.withCustomSymbol(requireContext())
        }

        collectFlow(unStakeViewModel.fiatFormatFlow) { fiatFormat ->
            amountView.description = fiatFormat.withCustomSymbol(requireContext())
        }

        collectFlow(unStakeViewModel.requestFeeFormat()) { (feeFormat, feeFiatFormat) ->
            feeView.setDefault()
            feeView.value = "≈ " + feeFormat.withCustomSymbol(requireContext())
            feeView.description = "≈ " + feeFiatFormat.withCustomSymbol(requireContext())
            button.isEnabled = true
        }
    }

    override fun onKeyboardAnimation(offset: Int, progress: Float, isShowing: Boolean) {
        super.onKeyboardAnimation(offset, progress, isShowing)
        button.translationY = -offset.toFloat()
        taskView.translationY = -offset.toFloat()
    }

    private fun unStake() {
        collectFlow(unStakeViewModel.unStake(requireContext())) {

        }
    }

    private fun applyPool(pool: PoolEntity) {
        iconView.setLocalRes(StakingPool.getIcon(pool.implementation))
        recipientView.value = pool.name.short12
    }

    private fun setTaskState(state: ProcessTaskView.State) {
        when(state) {
            ProcessTaskView.State.DEFAULT -> setDefaultState()
            ProcessTaskView.State.LOADING -> setLoadingState()
            ProcessTaskView.State.SUCCESS -> setSuccessState()
            ProcessTaskView.State.FAILED -> setFailedState()
        }
    }

    private fun setDefaultState() {
        taskView.visibility = View.GONE
        button.visibility = View.VISIBLE
    }

    private fun setLoadingState() {
        taskView.visibility = View.VISIBLE
        button.visibility = View.GONE
        taskView.state = ProcessTaskView.State.LOADING
    }

    private fun setFailedState() {
        taskView.state = ProcessTaskView.State.FAILED
        lifecycleScope.launch {
            delay(3000)
            setDefaultState()
        }
    }

    private fun setSuccessState() {
        taskView.state = ProcessTaskView.State.SUCCESS
    }

    override fun getTitle() = ""

    companion object {
        fun newInstance() = UnStakeConfirmFragment()
    }
}