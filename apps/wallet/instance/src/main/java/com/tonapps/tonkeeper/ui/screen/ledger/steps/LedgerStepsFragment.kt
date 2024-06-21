package com.tonapps.tonkeeper.ui.screen.ledger.steps


import android.animation.ObjectAnimator
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.screen.ledger.steps.list.Adapter
import com.tonapps.tonkeeperx.R
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow

class LedgerStepsFragment() : BaseFragment(R.layout.fragment_ledger_steps) {
    companion object {

        private const val WITH_CONFIRM_TX = "WITH_CONFIRM_TX"

        fun newInstance(showConfirmTxStep: Boolean): LedgerStepsFragment {
            val fragment = LedgerStepsFragment()
            fragment.arguments = Bundle().apply {
                putBoolean(WITH_CONFIRM_TX, showConfirmTxStep)
            }
            return fragment
        }
    }

    private val showConfirmTxStep: Boolean by lazy { requireArguments().getBoolean(WITH_CONFIRM_TX) }

    private val stepsViewModel: LedgerStepsViewModel by viewModel { parametersOf(showConfirmTxStep) }
    private val adapter = Adapter()

    private lateinit var listView: RecyclerView
    private lateinit var bluetoothIconView: AppCompatImageView
    private lateinit var ledgerView: RelativeLayout
    private lateinit var ledgerDisplayView: LinearLayout
    private lateinit var ledgerDisplayText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        collectFlow(stepsViewModel.uiItemsFlow, adapter::submitList)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bluetoothIconView = view.findViewById(R.id.bluetooth_icon)
        ledgerView = view.findViewById(R.id.ledger_picture)
        ledgerDisplayView = view.findViewById(R.id.ledger_display)
        ledgerDisplayText = view.findViewById(R.id.ledger_display_text)

        listView = view.findViewById(R.id.list)
        listView.adapter = adapter

        collectFlow(stepsViewModel.currentStepFlow, ::animateView)
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
    }

    private fun animateView(currentStep: LedgerStep) {
        val interpolator = AccelerateDecelerateInterpolator()

        if (currentStep == LedgerStep.CONNECT) {
            val blFadeAnim =
                ObjectAnimator.ofFloat(bluetoothIconView, "alpha", bluetoothIconView.alpha, 1f)
            blFadeAnim.duration = 300
            blFadeAnim.startDelay = 200
            blFadeAnim.interpolator = interpolator
            blFadeAnim.start()

            val displayFadeAnim =
                ObjectAnimator.ofFloat(ledgerDisplayView, "alpha", ledgerDisplayView.alpha, 0f)
            displayFadeAnim.duration = 300
            displayFadeAnim.interpolator = interpolator
            displayFadeAnim.start()

            val translationX = ObjectAnimator.ofFloat(
                ledgerView, "translationX", ledgerView.translationX, dpToPx(24f)
            )
            translationX.duration = 300
            translationX.startDelay = 200
            translationX.interpolator = interpolator
            translationX.start()
        } else {
            val blFadeAnim =
                ObjectAnimator.ofFloat(bluetoothIconView, "alpha", bluetoothIconView.alpha, 0f)
            blFadeAnim.duration = 300
            blFadeAnim.interpolator = interpolator
            blFadeAnim.start()

            val displayFadeAnim =
                ObjectAnimator.ofFloat(ledgerDisplayView, "alpha", ledgerDisplayView.alpha, 1f)
            displayFadeAnim.duration = 300
            displayFadeAnim.startDelay = 150
            displayFadeAnim.interpolator = interpolator
            displayFadeAnim.start()

            val translationX = ObjectAnimator.ofFloat(
                ledgerView, "translationX", ledgerView.translationX, dpToPx(-42f)
            )
            translationX.duration = 350
            translationX.interpolator = interpolator
            translationX.start()
        }

        ledgerDisplayText.text = when {
            showConfirmTxStep && (currentStep == LedgerStep.CONFIRM_TX || currentStep == LedgerStep.DONE) -> "Review"
            else -> "TON ready"
        }
    }

    fun setCurrentStep(step: LedgerStep) {
        stepsViewModel.setCurrentStep(step)
    }
}
