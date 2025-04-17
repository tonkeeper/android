package com.tonapps.tonkeeper.ui.screen.sign

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.text.color
import androidx.lifecycle.lifecycleScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.blockchain.ton.connect.TONProof
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.extensions.copyToClipboard
import com.tonapps.tonkeeper.extensions.getTitle
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.manager.tonconnect.bridge.BridgeException
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.SignDataRequestPayload
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.send.transaction.SendTransactionScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.resolveColor
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.activity
import uikit.extensions.addForResult
import uikit.extensions.withAlpha
import uikit.widget.ProcessTaskView
import uikit.widget.SlideActionView
import java.util.concurrent.CancellationException

class SignDataScreen(wallet: WalletEntity): BaseWalletScreen<ScreenContext.Wallet>(R.layout.fragment_signdata, ScreenContext.Wallet(wallet)), BaseFragment.Modal, BaseFragment.SingleTask {

    private val args: SignDataArgs by lazy { SignDataArgs(requireArguments()) }

    override val viewModel: SignDataViewModel by walletViewModel { parametersOf(args.appUrl) }

    private lateinit var textLayoutView: View
    private lateinit var binaryLayoutView: View
    private lateinit var cellLayoutView: View
    private lateinit var slideView: SlideActionView
    private lateinit var taskView: ProcessTaskView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        textLayoutView = view.findViewById(R.id.text_layout)
        binaryLayoutView = view.findViewById(R.id.binary_layout)
        cellLayoutView = view.findViewById(R.id.cell_layout)

        view.findViewById<View>(R.id.close).setOnClickListener { finish() }
        view.findViewById<AppCompatTextView>(R.id.title).text = args.appUrl.host

        slideView = view.findViewById(R.id.slide)
        slideView.setText(buildSignText())

        taskView = view.findViewById(R.id.task)

        applySubtitle(view.findViewById(R.id.subtitle))

        when(val payload = args.payload) {
            is SignDataRequestPayload.Text -> applyTextLayout(payload)
            is SignDataRequestPayload.Binary -> applyBinaryLayout(payload)
            is SignDataRequestPayload.Cell -> applyCellLayout(payload)
            else -> throw IllegalArgumentException("Unsupported payload type: $payload")
        }
    }

    private fun buildSignText(): SpannableStringBuilder {
        val secondLineText = getString(Localization.swipe_right)
        val slideTextBuilder = SpannableStringBuilder()
        slideTextBuilder.append(getString(Localization.sign))
        slideTextBuilder.append("\n")
        slideTextBuilder.append(SpannableString(secondLineText).apply {
            setSpan(RelativeSizeSpan(0.8f),0, secondLineText.length,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(requireContext().resolveColor(com.tonapps.uikit.color.R.attr.textTertiaryColor).withAlpha(0.7f)),0, secondLineText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        })
        return slideTextBuilder
    }

    private fun applyTextLayout(payload: SignDataRequestPayload.Text) {
        textLayoutView.visibility = View.VISIBLE
        textLayoutView.findViewById<AppCompatTextView>(R.id.text_value).apply {
            text = payload.text
            movementMethod = ScrollingMovementMethod()
        }
        textLayoutView.findViewById<View>(R.id.text_copy).setOnClickListener {
            context?.copyToClipboard(payload.text)
        }
        slideView.doOnDone = { signString(payload.text, "text") }
    }

    private fun applyBinaryLayout(payload: SignDataRequestPayload.Binary) {
        binaryLayoutView.visibility = View.VISIBLE
        slideView.doOnDone = { signString(payload.bytesBase64, "binary") }
    }

    private fun applyCellLayout(payload: SignDataRequestPayload.Cell) {
        cellLayoutView.visibility = View.VISIBLE
        cellLayoutView.findViewById<AppCompatTextView>(R.id.cell_value).apply {
            text = payload.print()
            movementMethod = ScrollingMovementMethod()
        }
        slideView.doOnDone = { signCell(payload) }
    }

    private fun applySubtitle(view: AppCompatTextView) {
        val builder = SpannableStringBuilder()
        builder.append(getString(Localization.sign_data))
        builder.append(" · ")
        builder.append(getString(Localization.wallet))
        builder.append(": ")
        builder.append(screenContext.wallet.label.getTitle(requireContext(), view))
        view.text = builder
    }

    private fun signString(content: String, type: String) {
        setProgressTask()

        lifecycleScope.launch {
            try {
                setSuccessTask(viewModel.signProof(content, type))
            } catch (e: Throwable) {
                slideView.reset()
                setErrorTask(BridgeException(cause = e))
            }
        }
    }

    private fun signCell(payload: SignDataRequestPayload.Cell) {
        setProgressTask()

        lifecycleScope.launch {
            try {
                setSuccessTask(viewModel.signCell(payload))
            } catch (e: Throwable) {
                slideView.reset()
                setErrorTask(BridgeException(cause = e))
            }
        }
    }

    private fun setActiveTask() {
        slideView.visibility = View.GONE
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
                putParcelable(SendTransactionScreen.ERROR, error)
            })
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(Throwable("Error: $error\nAppUrl: ${args.appUrl}", e))
        }
    }

    private fun setSuccessTask(proof: TONProof.Result) {
        setActiveTask()
        taskView.state = ProcessTaskView.State.SUCCESS
        postDelayed(2000) { setSuccessResult(proof) }
    }

    private fun setSuccessResult(proof: TONProof.Result) {
        setResult(Bundle().apply {
            putParcelable(PROOF, proof)
        })
    }

    companion object {

        private const val ERROR = "error"
        private const val PROOF = "proof"

        fun newInstance(wallet: WalletEntity, appUrl: Uri, payload: SignDataRequestPayload): SignDataScreen {
            val screen = SignDataScreen(wallet)
            screen.setArgs(SignDataArgs(appUrl, payload))
            return screen
        }

        suspend fun run(
            context: Context,
            wallet: WalletEntity,
            appUrl: Uri,
            payload: SignDataRequestPayload
        ): TONProof.Result {
            val activity = context.activity ?: throw IllegalArgumentException("Context must be an Activity")
            val fragment = newInstance(wallet, appUrl, payload)
            val result = activity.addForResult(fragment)
            if (result.containsKey(ERROR)) {
                val error = result.getParcelableCompat<BridgeError>(ERROR)!!
                throw BridgeException(message = error.message)
            }
            val proof = result.getParcelableCompat<TONProof.Result>(PROOF)
            if (proof != null) {
                return proof
            }
            throw CancellationException()
        }
    }
}