package com.tonapps.deposit.multicoin.screens.moonpay

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import com.tonapps.core.ComposableFragment
import com.tonapps.uikit.color.backgroundContentTintColor
import com.tonapps.wallet.localization.Localization
import kotlinx.serialization.json.Json
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.ModalView

class MoonPayPaymentFragment : ComposableFragment(), BaseFragment.Modal {

    private val data: MoonPayPaymentData by lazy {
        Json.decodeFromString<MoonPayPaymentData>(requireNotNull(requireArguments().getString(ARG_DATA)))
    }

    private val resultKey: String by lazy { requireNotNull(requireArguments().getString(ARG_RESULT_KEY)) }

    private var answered = false

    private val lazyFeature = viewModel<MoonPayPaymentFeature> { parametersOf(data) }
    private val feature: MoonPayPaymentFeature by lazyFeature

    // A screen opened by a deeplink or a push does not cut off a payment MoonPay is taking. A tapped buy button's
    // payment, before MoonPay names its transaction, is not held: the sheet closes like a failure, as on iOS.
    override val dismissedByScreens: Boolean
        get() = lazyFeature.isInitialized() && feature.dismissal.value != MoonPayPaymentDismissal.Blocked

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        answered = savedInstanceState?.getBoolean(STATE_ANSWERED) ?: false
        setContent {
            MoonPayPaymentScreen(
                feature = feature,
                onClose = { close(feature.dismissal.value) },
                onRetry = { retry -> answer(bundleOf(RESULT_RETRY to retry.name)) },
                onCancelled = { answer(Bundle()) },
                onDismissalChange = {
                    (view as? ModalView)?.setClosable(it != MoonPayPaymentDismissal.Blocked)
                },
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_ANSWERED, answered)
    }

    override fun onDestroy() {
        if (isRemoving && !answered) {
            answered = true
            // Created on first composition; creating it here would start the payment flow again.
            val dismissal = if (lazyFeature.isInitialized()) {
                feature.dismissal.value
            } else {
                MoonPayPaymentDismissal.CloseAll
            }
            navigation?.setFragmentResult(resultKey, closed(dismissal))
            tellIfStillProcessing(dismissal)
            if (lazyFeature.isInitialized()) {
                feature.closedBeforeOutcome()
            }
        } else if (activity?.isFinishing == true && lazyFeature.isInitialized()) {
            // The app is closed under the sheet: no answer is due, but the payment's outcome is still reported.
            feature.closedBeforeOutcome()
        }
        super.onDestroy()
    }

    private fun close(dismissal: MoonPayPaymentDismissal) {
        if (!answered) {
            tellIfStillProcessing(dismissal)
            feature.closedBeforeOutcome()
        }
        answer(closed(dismissal))
    }

    private fun tellIfStillProcessing(dismissal: MoonPayPaymentDismissal) {
        if (dismissal == MoonPayPaymentDismissal.Leave) {
            navigation?.toast(
                getString(Localization.moonpay_payment_still_processing_subtitle),
                loading = false,
                color = requireContext().backgroundContentTintColor,
            )
        }
    }

    // A sheet restored while it was closing has answered already: it only closes again.
    private fun answer(result: Bundle) {
        if (!answered) {
            answered = true
            navigation?.setFragmentResult(resultKey, result)
        }
        finish()
    }

    // Removed mid-payment, the outcome is unknown: the buying screen must not offer the same purchase again.
    private fun closed(dismissal: MoonPayPaymentDismissal): Bundle {
        return when (dismissal) {
            MoonPayPaymentDismissal.Back -> Bundle()
            MoonPayPaymentDismissal.Blocked,
            MoonPayPaymentDismissal.CloseAll,
            MoonPayPaymentDismissal.Leave,
            -> bundleOf(RESULT_CLOSE to true)
        }
    }

    companion object {
        const val RESULT_CLOSE = "close"
        const val RESULT_RETRY = "retry"
        private const val ARG_DATA = "data"
        private const val ARG_RESULT_KEY = "result_key"
        private const val STATE_ANSWERED = "answered"

        fun newInstance(data: MoonPayPaymentData, resultKey: String): MoonPayPaymentFragment {
            return MoonPayPaymentFragment().apply {
                arguments = bundleOf(ARG_DATA to Json.encodeToString(data), ARG_RESULT_KEY to resultKey)
            }
        }
    }
}
