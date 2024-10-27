package com.tonapps.tonkeeper.ui.screen.send.transaction

import android.content.Context
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.doOnNextLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.core.history.list.HistoryAdapter
import com.tonapps.tonkeeper.extensions.getTitle
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.manager.tonconnect.bridge.BridgeException
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.screen.send.InsufficientFundsDialog
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentOrangeColor
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.settings.BatteryTransaction
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.drawable.FooterDrawable
import uikit.drawable.HeaderDrawable
import uikit.extensions.activity
import uikit.extensions.addForResult
import uikit.extensions.applyNavBottomMargin
import uikit.extensions.bottomScrolled
import uikit.extensions.collectFlow
import uikit.extensions.isMaxScrollReached
import uikit.extensions.setOnClickListener
import uikit.extensions.setRightDrawable
import uikit.extensions.topScrolled
import uikit.widget.LoaderView
import uikit.widget.ProcessTaskView
import uikit.widget.SimpleRecyclerView
import uikit.widget.SlideActionView
import java.util.concurrent.CancellationException

class SendTransactionScreen(wallet: WalletEntity) : WalletContextScreen(R.layout.fragment_send_transaction, wallet), BaseFragment.Modal, BaseFragment.SingleTask {

    private val args: SendTransactionArgs by lazy { SendTransactionArgs(requireArguments()) }

    private val insufficientFundsDialog: InsufficientFundsDialog by lazy {
        InsufficientFundsDialog(requireContext())
    }

    override val viewModel: SendTransactionViewModel by walletViewModel {
        parametersOf(args.request, args.batteryTransactionType, args.forceRelayer)
    }

    private val historyAdapter = object : HistoryAdapter(disableOpenAction = true) {
        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
            recyclerView.isNestedScrollingEnabled = true
        }
    }

    private val headerDrawable: HeaderDrawable by lazy { HeaderDrawable(requireContext()) }
    private val footerDrawable: FooterDrawable by lazy { FooterDrawable(requireContext()) }
    private val totalDialog: SendTransactionTotalDialog by lazy { SendTransactionTotalDialog(requireContext()) }

    private lateinit var headerView: View
    private lateinit var walletView: AppCompatTextView
    private lateinit var slideView: SlideActionView
    private lateinit var totalView: AppCompatTextView
    private lateinit var loaderView: LoaderView
    private lateinit var listView: SimpleRecyclerView
    private lateinit var actionView: View
    private lateinit var scrollAllView: View
    private lateinit var taskView: ProcessTaskView
    private lateinit var bodyView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.background = headerDrawable

        walletView = view.findViewById(R.id.wallet)
        view.setOnClickListener(R.id.close) { finish() }

        loaderView = view.findViewById(R.id.loader)

        listView = view.findViewById(R.id.list)
        listView.adapter = historyAdapter

        actionView = view.findViewById(R.id.action)
        actionView.background = footerDrawable
        actionView.applyNavBottomMargin()

        slideView = view.findViewById(R.id.slide)
        slideView.isEnabled = false

        bodyView = view.findViewById(R.id.body)
        taskView = view.findViewById(R.id.task)
        totalView = view.findViewById(R.id.total)
        scrollAllView = view.findViewById(R.id.scroll_all)

        applyWallet()

        collectFlow(viewModel.stateFlow, ::applyState)
        collectFlow(listView.topScrolled, headerDrawable::setDivider)
        collectFlow(listView.bottomScrolled) {
            footerDrawable.setDivider(it)
            checkSlideButton()
        }
    }

    private fun send() {
        setProgressTask()
        viewModel.send().catch {
            if (it is CancellationException) {
                setDefaultTask()
            } else {
                setErrorTask(BridgeException(cause = it))
            }
        }.onEach { boc ->
            setSuccessTask(boc)
        }.launchIn(lifecycleScope)
    }

    private fun setDefaultTask() {
        slideView.reset()
        bodyView.visibility = View.VISIBLE
        taskView.state = ProcessTaskView.State.DEFAULT
        taskView.visibility = View.GONE
    }

    private fun setActiveTask() {
        bodyView.visibility = View.GONE
        taskView.visibility = View.VISIBLE
    }

    private fun setProgressTask() {
        setActiveTask()
        taskView.state = ProcessTaskView.State.LOADING
    }

    private fun setErrorTask(error: BridgeException) {
        setActiveTask()
        taskView.state = ProcessTaskView.State.FAILED
        postDelayed(2000) { setErrorResult(error) }
    }

    private fun setErrorResult(error: BridgeException) {
        setResult(Bundle().apply {
            putParcelable(ERROR, error)
        })
    }

    private fun setSuccessTask(boc: String) {
        setActiveTask()
        taskView.state = ProcessTaskView.State.SUCCESS
        postDelayed(2000) { setSuccessResult(boc) }
    }

    private fun setSuccessResult(boc: String) {
        setResult(Bundle().apply {
            putString(BOC, boc)
        })
    }

    private fun applyState(state: SendTransactionState) {
        when (state) {
            is SendTransactionState.Details -> applyDetails(state)
            is SendTransactionState.Failed -> setErrorTask(BridgeException(message = "Failed to send transaction in client"))
            is SendTransactionState.FailedEmulation -> setErrorTask(BridgeException(message = "Transaction emulation failed. Verify 'payload' and 'stateInit' field validity. Invalid message assembly detected or base64 decoding error."))
            is SendTransactionState.InsufficientBalance -> {
                insufficientFundsDialog.show(state.wallet, state.balance, state.required, state.withRechargeBattery, state.singleWallet)
                finish()
            }
            else -> { }
        }
    }

    private fun applyDetails(state: SendTransactionState.Details) {
        applyTotal(state)
        historyAdapter.submitList(state.uiItems) {
            loaderView.visibility = View.GONE
            listView.visibility = View.VISIBLE
            listView.doOnNextLayout { checkScrollable() }
        }
    }

    private fun checkScrollable() {
        listView.postOnAnimation {
            val isScrollable = listView.computeVerticalScrollRange() > listView.height
            if (isScrollable) {
                scrollAllView.visibility = View.VISIBLE
            } else {
                slideView.isEnabled = true
                slideView.doOnDone = { send() }
            }
        }
    }

    private fun checkSlideButton() {
        if (listView.visibility != View.VISIBLE || slideView.isEnabled) {
            return
        } else if (scrollAllView.visibility == View.VISIBLE && listView.isMaxScrollReached) {
            scrollAllView.visibility = View.GONE
            slideView.isEnabled = true
            slideView.doOnDone = { send() }
        }
    }

    private fun applyTotal(state: SendTransactionState.Details) {
        val color = if (state.isDangerous) {
            requireContext().accentOrangeColor
        } else {
            requireContext().textSecondaryColor
        }
        val drawable = getDrawable(UIKitIcon.ic_information_circle_16, color)

        totalView.text = state.totalFormat
        totalView.setTextColor(color)
        totalView.setRightDrawable(drawable)
        totalView.setOnClickListener { showTotalDialog(state) }
    }

    private fun showTotalDialog(state: SendTransactionState.Details) {
        val description = if (state.nftCount > 0) {
            getString(Localization.send_transaction_detail_nft)
        } else {
            getString(Localization.send_transaction_detail)
        }
        totalDialog.show(state.totalFormat.toString(), description)
    }

    private fun applyWallet() {
        val builder = SpannableStringBuilder(getString(Localization.wallet))
        builder.append(": ")
        builder.append(wallet.label.getTitle(requireContext(), walletView, 16))
        walletView.text = builder
    }

    companion object {

        const val ERROR = "error"
        const val BOC = "boc"

        fun newInstance(
            wallet: WalletEntity,
            request: SignRequestEntity,
            batteryTransactionType: BatteryTransaction = BatteryTransaction.UNKNOWN,
            forceRelayer: Boolean = false
        ): SendTransactionScreen {
            val screen = SendTransactionScreen(wallet)
            screen.setArgs(SendTransactionArgs(request, batteryTransactionType, forceRelayer))
            return screen
        }

        suspend fun run(
            context: Context,
            wallet: WalletEntity,
            request: SignRequestEntity,
            batteryTxType: BatteryTransaction = BatteryTransaction.UNKNOWN,
            forceRelayer: Boolean = false,
        ): String {
            val activity = context.activity ?: throw IllegalArgumentException("Context must be an Activity")
            val fragment = newInstance(wallet, request, batteryTxType, forceRelayer)
            val result = activity.addForResult(fragment)
            if (result.containsKey(ERROR)) {
                val error = result.getParcelableCompat<BridgeError>(ERROR)!!
                throw BridgeException(message = error.message)
            }
            val boc = result.getString(BOC)
            if (!boc.isNullOrBlank()) {
                return boc
            }
            throw CancellationException()
        }
    }
}