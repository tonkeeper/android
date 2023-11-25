package com.tonkeeper.fragment.jetton

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.facebook.drawee.view.SimpleDraweeView
import com.tonkeeper.R
import com.tonkeeper.api.parsedBalance
import com.tonkeeper.core.history.list.HistoryAdapter
import com.tonkeeper.core.history.list.HistoryItemDecoration
import io.tonapi.models.JettonBalance
import uikit.base.fragment.BaseFragment
import uikit.extensions.withAlpha
import uikit.extensions.withAnimation
import uikit.mvi.AsyncState
import uikit.mvi.UiScreen
import uikit.widget.HeaderView

class JettonScreen : UiScreen<JettonScreenState, JettonScreenEffect, JettonScreenFeature>(R.layout.fragment_jetton), BaseFragment.SwipeBack {

    companion object {
        private const val JETTON_ADDRESS_KEY = "JETTON_ADDRESS_KEY"
        private const val JETTON_NAME_KEY = "JETTON_NAME_KEY"

        fun newInstance(jettonAddress: String, jettonName: String): JettonScreen {
            val screen = JettonScreen()
            screen.arguments = Bundle().apply {
                putString(JETTON_ADDRESS_KEY, jettonAddress)
                putString(JETTON_NAME_KEY, jettonName)
            }
            return screen
        }

    }

    private val jettonAddress: String by lazy {
        arguments?.getString(JETTON_ADDRESS_KEY)!!
    }

    private val jettonName: String by lazy {
        arguments?.getString(JETTON_NAME_KEY) ?: ""
    }

    override val feature: JettonScreenFeature by viewModels()
    override var doOnDragging: ((Boolean) -> Unit)? = null
    override var doOnDraggingProgress: ((Float) -> Unit)? = null

    private lateinit var headerView: HeaderView
    private lateinit var shimmerView: View
    private lateinit var bodyView: View
    private lateinit var iconView: SimpleDraweeView
    private lateinit var balanceView: AppCompatTextView
    private lateinit var currencyBalanceView: AppCompatTextView
    private lateinit var rateView: AppCompatTextView
    private lateinit var listView: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.title = jettonName
        headerView.doOnCloseClick = { finish() }

        shimmerView = view.findViewById(R.id.shimmer)
        bodyView = view.findViewById(R.id.body)
        iconView = view.findViewById(R.id.icon)
        balanceView = view.findViewById(R.id.balance)
        currencyBalanceView = view.findViewById(R.id.currency_balance)
        rateView = view.findViewById(R.id.rate)
        listView = view.findViewById(R.id.list)
        listView.addItemDecoration(HistoryItemDecoration(view.context))
    }

    override fun onEndShowingAnimation() {
        super.onEndShowingAnimation()
        feature.load(jettonAddress)
    }

    override fun newUiState(state: JettonScreenState) {
        updateAsyncState(state.asyncState)
        currencyBalanceView.text = state.currencyBalance
        rateView.text = createRate(state.rateFormat, state.rate24h)
        listView.adapter = HistoryAdapter(state.historyItems)

        state.jetton?.let { setJetton(it) }
    }

    private fun setJetton(jetton: JettonBalance) {
        iconView.setImageURI(jetton.jetton.image)
        balanceView.text = "${jetton.parsedBalance} ${jetton.jetton.symbol}"
    }

    private fun updateAsyncState(state: AsyncState) {
        if (state == AsyncState.Loading) {
            shimmerView.visibility = View.VISIBLE
            bodyView.visibility = View.GONE
        } else {
            shimmerView.visibility = View.GONE
            bodyView.visibility = View.VISIBLE
        }
    }

    private fun createRate(rate: String, diff24h: String): SpannableString {
        val period = requireContext().getString(R.string.period_24h)
        val span = SpannableString("$rate $diff24h $period")
        span.setSpan(
            ForegroundColorSpan(getDiffColor(diff24h)),
            rate.length,
            span.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return span
    }

    @ColorInt
    private fun getDiffColor(diff: String): Int {
        val context = requireContext()
        val resId = when {
            diff.startsWith("-") -> uikit.R.color.accentRed
            diff.startsWith("+") -> uikit.R.color.accentGreen
            else -> uikit.R.color.textSecondary
        }
        return context.getColor(resId).withAlpha(.64f)
    }

}