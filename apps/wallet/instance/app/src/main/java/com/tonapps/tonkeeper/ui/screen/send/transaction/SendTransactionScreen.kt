package com.tonapps.tonkeeper.ui.screen.send.transaction

import android.content.Context
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.deposit.multicoin.analytics.asDepositFlowFrom
import com.tonapps.deposit.screens.send.state.SendFee
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.core.history.list.HistoryAdapter
import com.tonapps.tonkeeper.extensions.addFeeItem
import com.tonapps.tonkeeper.extensions.getTitle
import com.tonapps.tonkeeper.extensions.id
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.manager.tonconnect.bridge.BridgeException
import com.tonapps.tonkeeper.popup.ActionSheet
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.screen.send.InsufficientFundsDialog
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentOrangeColor
import com.tonapps.uikit.color.resolveColor
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.uikit.icon.UIKitIcon
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
import uikit.extensions.dp
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.isMaxScrollReached
import uikit.extensions.setRightDrawable
import uikit.extensions.topScrolled
import uikit.extensions.withAlpha
import uikit.widget.LoaderView
import uikit.widget.ProcessTaskView
import uikit.widget.SimpleRecyclerView
import uikit.widget.SlideActionView
import java.util.concurrent.CancellationException
import uikit.extensions.setMoonOnClickListener

class SendTransactionScreen(wallet: WalletEntity) :
    WalletContextScreen(R.layout.fragment_send_transaction, wallet), BaseFragment.Modal,
    BaseFragment.SingleTask {

    override val fragmentName: String = "SendTransactionScreen"

    private val args: SendTransactionArgs by lazy { SendTransactionArgs(requireArguments()) }

    private val insufficientFundsDialog: InsufficientFundsDialog by lazy {
        InsufficientFundsDialog(this, topUpFrom())
    }

    override val viewModel: SendTransactionViewModel by walletViewModel {
        parametersOf(
            args.request,
            args.batteryTransactionType,
            args.forceRelayer,
            args.broadcastVia,
            SendTransactionAnalyticsContext(
                sendNativeFrom = args.sendNativeFrom,
                transactionSentDetail = args.transactionSentDetail,
                utm = args.utm,
            ),
            args.mode,
        )
    }

    private val feeMethodSelector: ActionSheet by lazy {
        ActionSheet(requireContext())
    }

    private val historyAdapter = object : HistoryAdapter(
        disableOpenAction = true,
        shouldShowFeeToggle = {
            viewModel.feeOptions.size > 1
        },
        showFeeMethods = ::showFeeMethods,
    ) {
        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
            recyclerView.isNestedScrollingEnabled = true
        }
    }

    private val headerDrawable: HeaderDrawable by lazy { HeaderDrawable(requireContext()) }
    private val footerDrawable: FooterDrawable by lazy { FooterDrawable(requireContext()) }
    private val totalDialog: SendTransactionTotalDialog by lazy {
        SendTransactionTotalDialog(
            requireContext()
        )
    }

    private lateinit var headerView: View
    private lateinit var walletView: AppCompatTextView
    private lateinit var emulatedView: View
    private lateinit var slideView: SlideActionView
    private lateinit var totalView: AppCompatTextView
    private lateinit var loaderView: LoaderView
    private lateinit var listView: SimpleRecyclerView
    private lateinit var actionView: View
    private lateinit var scrollAllView: View
    private lateinit var taskView: ProcessTaskView
    private lateinit var bodyView: View
    private lateinit var warningView: View
    private lateinit var signMessageNoticeView: View
    private lateinit var signMessageNoticeTextView: AppCompatTextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.background = headerDrawable

        walletView = view.findViewById(R.id.wallet)
        view.setMoonOnClickListener(R.id.close) { finish() }

        emulatedView = view.findViewById(R.id.emulated)

        loaderView = view.findViewById(R.id.loader)

        listView = view.findViewById(R.id.list)
        listView.adapter = historyAdapter

        actionView = view.findViewById(R.id.action)
        actionView.background = footerDrawable
        actionView.applyNavBottomMargin(requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium))

        slideView = view.findViewById(R.id.slide)
        slideView.isEnabled = false
        slideView.setText(buildConfirmText(confirmTitleRes()))

        bodyView = view.findViewById(R.id.body)
        taskView = view.findViewById(R.id.task)
        totalView = view.findViewById(R.id.total)
        scrollAllView = view.findViewById(R.id.scroll_all)

        warningView = view.findViewById(R.id.warning)
        warningView.background.setTint(requireContext().accentOrangeColor.withAlpha(0.24f))

        signMessageNoticeView = view.findViewById(R.id.sign_message_notice)
        signMessageNoticeTextView = view.findViewById(R.id.sign_message_notice_text)
        applyMode()

        applyWallet()

        collectFlow(viewModel.stateFlow, ::applyState)
        collectFlow(listView.topScrolled, headerDrawable::setDivider)
        collectFlow(listView.bottomScrolled) {
            footerDrawable.setDivider(it)
            checkSlideButton()
        }
    }

    private fun topUpFrom(): DepositFlowFrom {
        val sendNativeFrom = args.sendNativeFrom
        return when {
            sendNativeFrom != null -> sendNativeFrom.asDepositFlowFrom()
            args.batteryTransactionType == BatteryTransaction.SWAP -> DepositFlowFrom.WalletScreen
            else -> DepositFlowFrom.Other
        }
    }

    private fun applyMode() {
        val mode = args.mode
        if (mode is SendTransactionMode.SignMessage) {
            signMessageNoticeTextView.text = getString(Localization.sign_message_not_sent_warning, mode.appName)
            signMessageNoticeView.visibility = View.VISIBLE
            taskView.setSuccessLabel(getString(Localization.signed))
        }
    }

    @StringRes
    private fun confirmTitleRes(): Int {
        return when (args.mode) {
            is SendTransactionMode.SendTransaction -> Localization.confirm
            is SendTransactionMode.SignMessage -> Localization.sign
        }
    }

    private fun confirm() {
        when (args.mode) {
            is SendTransactionMode.SendTransaction -> send()
            is SendTransactionMode.SignMessage -> signMessage()
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
        }.onEach { bocList ->
            if (bocList.isNotEmpty()) {
                setSuccessTask(bocList.first())
            }
        }.launchIn(lifecycleScope)
    }

    private fun signMessage() {
        setProgressTask()
        viewModel.signMessage().catch {
            if (it is CancellationException) {
                setDefaultTask()
            } else {
                setErrorTask(BridgeException(cause = it))
            }
        }.onEach { internalBoc ->
            setSuccessTask(internalBoc)
        }.launchIn(lifecycleScope)
    }

    private fun buildConfirmText(@StringRes titleRes: Int): SpannableStringBuilder {
        val secondLineText = getString(Localization.swipe_right)
        val slideTextBuilder = SpannableStringBuilder()
        slideTextBuilder.append(getString(titleRes))
        slideTextBuilder.append("\n")
        slideTextBuilder.append(SpannableString(secondLineText).apply {
            setSpan(
                RelativeSizeSpan(0.8f), 0, secondLineText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                ForegroundColorSpan(
                    requireContext().resolveColor(com.tonapps.ui.uikit.color.R.attr.textTertiaryColor)
                        .withAlpha(0.7f)
                ), 0, secondLineText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        })
        return slideTextBuilder
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
        try {
            setResult(Bundle().apply {
                putParcelable(ERROR, error)
            })
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance()
                .recordException(Throwable("Error: $error\nAppUrl: ${args.request.appUri}", e))
        }
    }

    private fun setSuccessTask(boc: String) {
        setActiveTask()
        taskView.state = ProcessTaskView.State.SUCCESS
        postDelayed(2000) { setSuccessResult(boc) }
    }

    private fun setSuccessResult(boc: String) {
        try {
            setResult(Bundle().apply {
                putString(BOC, boc)
            })
        } catch (_: Throwable) {
            finish()
        }
    }

    private fun applyState(state: SendTransactionState) {
        when (state) {
            is SendTransactionState.Details -> applyDetails(state)
            is SendTransactionState.Failed -> setErrorTask(BridgeException(message = "Failed to send transaction in client"))
            is SendTransactionState.FailedEmulation -> setErrorTask(BridgeException(message = "Transaction emulation failed. Verify 'payload' and 'stateInit' field validity. Invalid message assembly detected or base64 decoding error."))
            is SendTransactionState.InsufficientBalance -> {
                insufficientFundsDialog.show(
                    state.wallet,
                    state.balance,
                    state.required,
                    state.withRechargeBattery,
                    state.singleWallet,
                    state.type
                )
                finish()
            }

            else -> {}
        }
    }

    private fun applyDetails(state: SendTransactionState.Details) {
        historyAdapter.submitList(state.uiItems) {
            loaderView.visibility = View.GONE
            listView.visibility = View.VISIBLE
            listView.doOnNextLayout { checkScrollable() }
        }
        if (state.failed) {
            showFailedEmulate()
        } else {
            applyTotal(state)
        }
    }

    private fun showFailedEmulate() {
        slideView.setTint(requireContext().accentOrangeColor)
        totalView.visibility = View.GONE
        emulatedView.visibility = View.GONE
        warningView.visibility = View.VISIBLE

        slideView.isEnabled = true
        slideView.doOnDone = { confirm() }
    }

    private fun checkScrollable() {
        listView.postOnAnimation {
            val isScrollable = listView.computeVerticalScrollRange() > listView.height
            if (isScrollable) {
                scrollAllView.visibility = View.VISIBLE
            } else {
                slideView.isEnabled = true
                slideView.doOnDone = { confirm() }
            }
        }
    }

    private fun checkSlideButton() {
        if (listView.visibility != View.VISIBLE || slideView.isEnabled) {
            return
        } else if (scrollAllView.isVisible && listView.isMaxScrollReached) {
            scrollAllView.visibility = View.GONE
            slideView.isEnabled = true
            slideView.doOnDone = { confirm() }
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
        totalView.visibility = View.VISIBLE
        totalView.setTextColor(color)
        totalView.setRightDrawable(drawable)
        totalView.setMoonOnClickListener { showTotalDialog(state) }
    }

    private fun showTotalDialog(state: SendTransactionState.Details) {
        val description = if (state.nftCount == 1) {
            getString(Localization.send_transaction_detail_nft)
        } else if (state.nftCount > 0) {
            getString(Localization.send_transaction_detail_nfts)
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

    private fun showFeeMethods(currentFee: SendFee, targetView: View) {
        feeMethodSelector.width = 264.dp

        if (feeMethodSelector.isShowing) {
            return
        }

        feeMethodSelector.clearItems()

        viewModel.feeOptions.forEach { fee ->
            feeMethodSelector.addFeeItem(fee, fee.id == currentFee?.id) {
                viewModel.setFeeMethod(fee)
            }
        }
        feeMethodSelector.showPopupAboveRight(targetView)
    }

    companion object {

        const val ERROR = "error"
        const val BOC = "boc"

        fun newInstance(
            wallet: WalletEntity,
            request: SignRequestEntity,
            batteryTransactionType: BatteryTransaction = BatteryTransaction.UNKNOWN,
            forceRelayer: Boolean = false,
            broadcastVia: BroadcastVia = BroadcastVia.Default,
            sendNativeFrom: Events.SendNative.SendNativeFrom? = null,
            transactionSentDetail: Events.TransactionSent.TransactionSentCategoryDetail? = null,
            utm: UtmParameters = UtmParameters.Empty,
            mode: SendTransactionMode = SendTransactionMode.SendTransaction,
        ): SendTransactionScreen {
            val screen = SendTransactionScreen(wallet)
            screen.setArgs(
                SendTransactionArgs(
                    request = request,
                    batteryTransactionType = batteryTransactionType,
                    forceRelayer = forceRelayer,
                    broadcastVia = broadcastVia,
                    sendNativeFrom = sendNativeFrom,
                    transactionSentDetail = transactionSentDetail,
                    utm = utm,
                    mode = mode,
                )
            )
            return screen
        }

        /**
         * TON Connect `signMessage`: shows the request as a transaction preview, signs it as an
         * internal message for the relayer behind [appName] and returns that message's base64 BOC
         * without broadcasting anything. Throws [CancellationException] when the user declines.
         */
        suspend fun runSignMessage(
            context: Context,
            wallet: WalletEntity,
            request: SignRequestEntity,
            appName: String,
        ): String {
            return run(
                context = context,
                wallet = wallet,
                request = request,
                mode = SendTransactionMode.SignMessage(appName),
            )
        }

        suspend fun run(
            context: Context,
            wallet: WalletEntity,
            request: SignRequestEntity,
            batteryTxType: BatteryTransaction = BatteryTransaction.UNKNOWN,
            forceRelayer: Boolean = false,
            broadcastVia: BroadcastVia = BroadcastVia.Default,
            sendNativeFrom: Events.SendNative.SendNativeFrom? = null,
            transactionSentDetail: Events.TransactionSent.TransactionSentCategoryDetail? = null,
            utm: UtmParameters = UtmParameters.Empty,
            mode: SendTransactionMode = SendTransactionMode.SendTransaction,
        ): String {
            val activity = context.activity ?: throw IllegalArgumentException("Context must be an Activity")
            val fragment = newInstance(
                wallet,
                request,
                batteryTxType,
                forceRelayer,
                broadcastVia,
                sendNativeFrom,
                transactionSentDetail,
                utm,
                mode,
            )
            val result = activity.addForResult(fragment)
            if (result.containsKey(ERROR)) {
                // Type must stay in sync with `setErrorResult(BridgeException)` at line 216 —
                // a previous mismatch (consumer read BridgeError, producer wrote BridgeException)
                // made Bundle.getParcelable throw ClassCastException, which getParcelableCompat
                // swallows to null and then `!!` blows up as an NPE downstream of the toast.
                throw result.getParcelableCompat<BridgeException>(ERROR)
                    ?: BridgeException(message = "Unknown bridge error")
            }
            val boc = result.getString(BOC)
            if (!boc.isNullOrBlank()) {
                return boc
            }
            throw CancellationException()
        }
    }
}
