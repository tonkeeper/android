package com.tonapps.tonkeeper.ui.screen.action

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.HistoryAdapter
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.sign.SignRequestEntity
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.ProcessTaskView
import uikit.widget.SimpleRecyclerView

class ActionScreen: BaseFragment(R.layout.fragment_action), BaseFragment.Modal {

    private val args: ActionArgs by lazy {
        ActionArgs(requireArguments())
    }

    private val actionViewModel: ActionViewModel by viewModel { parametersOf(args) }
    private val adapter = HistoryAdapter()

    private lateinit var walletView: AppCompatTextView
    private lateinit var closeView: View
    private lateinit var actionsView: SimpleRecyclerView
    private lateinit var feeView: AppCompatTextView
    private lateinit var buttonsView: View
    private lateinit var confirmButton: Button
    private lateinit var cancelButton: Button
    private lateinit var processView: ProcessTaskView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        walletView = view.findViewById(R.id.action_wallet)
        closeView = view.findViewById(R.id.action_close)
        closeView.setOnClickListener { finish() }

        actionsView = view.findViewById(R.id.actions)
        actionsView.adapter = adapter
        adapter.submitList(args.historyItems)

        feeView = view.findViewById(R.id.fee)
        feeView.text = String.format("≈ %s · %s", args.feeFormat, args.feeFiatFormat)

        processView = view.findViewById(R.id.process)
        buttonsView = view.findViewById(R.id.buttons)

        confirmButton = view.findViewById(R.id.confirm)
        confirmButton.setOnClickListener { confirm() }

        cancelButton = view.findViewById(R.id.cancel)
        cancelButton.setOnClickListener { finish() }

        collectFlow(actionViewModel.walletFlow, ::applyWallet)
    }

    private fun confirm() {
        buttonsView.visibility = View.GONE
        processView.visibility = View.VISIBLE
        processView.state = ProcessTaskView.State.LOADING

        actionViewModel.sign(requireContext()).catch {
            setFailed()
        }.onEach(::setSuccess).launchIn(lifecycleScope)
    }

    private suspend fun setFailed() = withContext(Dispatchers.Main) {
        processView.state = ProcessTaskView.State.FAILED
        delay(2500)
        finish()
    }

    private suspend fun setSuccess(boc: String) = withContext(Dispatchers.Main) {
        processView.state = ProcessTaskView.State.SUCCESS
        delay(2500)
        navigation?.setFragmentResult(args.resultKey, Bundle().apply {
            putString(BOC_KEY, boc)
        })
        super.finish()
    }

    override fun finish() {
        super.finish()
        navigation?.setFragmentResult(args.resultKey)
    }

    private fun applyWallet(wallet: WalletEntity) {
        walletView.text = String.format("%s: %s", getString(Localization.wallet), wallet.label.title)
    }

    companion object {

        private const val BOC_KEY = "boc"

        fun parseResult(bundle: Bundle): String? {
            return bundle.getString(BOC_KEY)
        }

        fun newInstance(
            details: HistoryHelper.Details,
            walletId: String,
            request: SignRequestEntity,
            requestKey: String
        ) = newInstance(
            accountId = details.accountId,
            walletId = walletId,
            request = request,
            historyItems = details.items,
            feeFormat = details.feeFormat,
            feeFiatFormat = details.feeFiatFormat,
            requestKey = requestKey
        )

        fun newInstance(
            accountId: String,
            walletId: String,
            request: SignRequestEntity,
            historyItems: List<HistoryItem>,
            feeFormat: CharSequence,
            feeFiatFormat: CharSequence,
            requestKey: String
        ): ActionScreen {
            val args = ActionArgs(accountId, walletId, request, historyItems, feeFormat, feeFiatFormat, requestKey)
            val screen = ActionScreen()
            screen.setArgs(args)
            return screen
        }
    }
}