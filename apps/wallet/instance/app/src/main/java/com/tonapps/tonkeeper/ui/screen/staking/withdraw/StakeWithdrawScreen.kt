package com.tonapps.tonkeeper.ui.screen.staking.withdraw

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.extensions.getTitle
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.screen.send.main.SendException
import com.tonapps.tonkeeper.ui.screen.staking.unstake.UnStakeScreen
import com.tonapps.tonkeeper.ui.screen.staking.viewer.StakeViewerScreen
import com.tonapps.tonkeeper.view.TransactionDetailView
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.entities.WalletEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf
import uikit.extensions.collectFlow
import uikit.widget.FrescoView
import uikit.widget.HeaderView
import uikit.widget.ProcessTaskView

class StakeWithdrawScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_stake_withdraw, wallet) {

    override val viewModel: StakeWithdrawViewModel by walletViewModel {
        parametersOf(requireArguments().getString(ARG_POOL_ADDRESS))
    }

    private lateinit var iconView: FrescoView
    private lateinit var walletView: TransactionDetailView
    private lateinit var recipientView: TransactionDetailView
    private lateinit var amountView: TransactionDetailView
    private lateinit var feeView: TransactionDetailView
    private lateinit var taskView: ProcessTaskView
    private lateinit var confirmButton: Button
    private lateinit var buttonsView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val headerView = view.findViewById<HeaderView>(R.id.header)
        headerView.doOnActionClick = { finish() }

        iconView = view.findViewById(R.id.icon)

        walletView = view.findViewById(R.id.line_wallet)
        walletView.value = wallet.label.getTitle(requireContext(), walletView.valueView, 12)

        recipientView = view.findViewById(R.id.line_recipient)

        amountView = view.findViewById(R.id.line_amount)
        feeView = view.findViewById(R.id.line_fee)

        taskView = view.findViewById(R.id.task)

        val cancelButton = view.findViewById<Button>(R.id.cancel)
        cancelButton.setOnClickListener { finish() }

        confirmButton = view.findViewById(R.id.confirm)
        confirmButton.setOnClickListener { send() }
        confirmButton.isEnabled = false

        buttonsView = view.findViewById(R.id.buttons)

        collectFlow(viewModel.taskStateFlow, ::setTaskState)

        collectFlow(viewModel.poolDetailsFlow) { poolDetails ->
            iconView.setImageURI(poolDetails.url, null)
            recipientView.value = poolDetails.name
        }

        collectFlow(viewModel.amountFormatFlow) { (amount, fiat) ->
            amountView.value = amount.withCustomSymbol(requireContext())
            amountView.description = "≈ " + fiat.withCustomSymbol(requireContext())
        }

        collectFlow(viewModel.requestFee()) { (feeFormat, feeFiatFormat) ->
            feeView.setDefault()
            feeView.value = "≈ " + feeFormat.withCustomSymbol(requireContext())
            feeView.description = "≈ " + feeFiatFormat.withCustomSymbol(requireContext())
            confirmButton.isEnabled = true
        }
    }

    private fun send() {
        setTaskState(ProcessTaskView.State.LOADING)
        viewModel.send(requireContext()).catch { e ->
            val state = if (e is SendException.Cancelled) ProcessTaskView.State.DEFAULT else ProcessTaskView.State.FAILED
            setTaskState(state)
        }.onEach {
            setTaskState(ProcessTaskView.State.SUCCESS)
            navigation?.openURL("tonkeeper://activity")
            delay(2000)
            finish()
        }.launchIn(lifecycleScope)
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
        buttonsView.visibility = View.VISIBLE
    }

    private fun setLoadingState() {
        taskView.visibility = View.VISIBLE
        buttonsView.visibility = View.GONE
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

    companion object {

        private const val ARG_POOL_ADDRESS = "pool_address"

        fun newInstance(wallet: WalletEntity, poolAddress: String): StakeWithdrawScreen {
            val fragment = StakeWithdrawScreen(wallet)
            fragment.putStringArg(ARG_POOL_ADDRESS, poolAddress)
            return fragment
        }
    }
}