package com.tonapps.tonkeeper.ui.screen.onramp.main

import android.content.Context
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.text.color
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.extensions.hideKeyboard
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.component.CountryFlagView
import com.tonapps.tonkeeper.ui.component.PaymentTypeView
import com.tonapps.tonkeeper.ui.screen.country.CountryPickerScreen
import com.tonapps.tonkeeper.ui.screen.onramp.main.state.CurrencyInputState
import com.tonapps.tonkeeper.ui.screen.onramp.main.state.OnRampConfirmState
import com.tonapps.tonkeeper.ui.screen.onramp.main.state.OnRampCurrencyInputs
import com.tonapps.tonkeeper.ui.screen.onramp.main.view.CurrencyInputView
import com.tonapps.tonkeeper.ui.screen.onramp.main.view.ReviewInputView
import com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.OnRampPickerScreen
import com.tonapps.tonkeeper.ui.screen.onramp.picker.provider.OnRampProviderPickerScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.purchase.entity.PurchaseMethodEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.launch
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.widget.HeaderView
import uikit.widget.LoadableButton
import uikit.widget.SlideBetweenView
import androidx.core.view.isVisible
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.koin.remoteConfig
import com.tonapps.tonkeeper.ui.screen.onramp.main.state.OnRampBalanceState
import com.tonapps.tonkeeper.ui.screen.purchase.PurchaseScreen
import com.tonapps.uikit.list.ListCell
import kotlinx.coroutines.flow.filter
import uikit.extensions.drawable

class OnRampScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_onramp, wallet), BaseFragment.BottomSheet {

    private val source: String by lazy {
        requireArguments().getString(ARG_SOURCE) ?: ""
    }

    private var checkButtonRunnable: Runnable? = null

    override val viewModel: OnRampViewModel by walletViewModel()

    private lateinit var slidesView: SlideBetweenView
    private lateinit var headerView: HeaderView
    private lateinit var countryView: CountryFlagView
    private lateinit var sellInput: CurrencyInputView
    private lateinit var buyInput: CurrencyInputView
    private lateinit var button: LoadableButton
    private lateinit var switchView: View
    private lateinit var priceView: AppCompatTextView
    private lateinit var reviewSend: ReviewInputView
    private lateinit var reviewReceive: ReviewInputView
    private lateinit var pairNotAvailableView: AppCompatTextView
    private lateinit var providerTitleView: AppCompatTextView
    private lateinit var paymentView: View
    private lateinit var paymentTypeViews: List<PaymentTypeView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnalyticsHelper.onRampOpen(viewModel.installId, source)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        slidesView = view.findViewById(R.id.slides)
        slidesView.doOnChange = { index ->
            if (index == 0) {
                viewModel.resetConfirm()
            }
            checkButtonDelay()
        }

        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { prev() }
        headerView.doOnActionClick = { finish() }

        countryView = view.findViewById(R.id.country)
        countryView.setOnClickListener { pickCountry() }

        sellInput = view.findViewById(R.id.sell_input)
        sellInput.focusWithKeyboard()
        sellInput.doOnValueChange = { value, byUser ->
            if (byUser) {
                viewModel.setFromAmount(value)
            }
            checkButtonDelay()
        }
        sellInput.doOnCurrencyClick = { openCurrencyPicker(true) }

        buyInput = view.findViewById(R.id.buy_input)
        buyInput.setValueScale(3)
        buyInput.setPrefix(CurrencyInputView.EQUALS_SIGN_PREFIX)
        buyInput.doOnValueChange = { value, byUser ->
            if (byUser) {
                viewModel.setToAmount(value)
            }
            checkButtonDelay()
        }
        buyInput.doOnCurrencyClick = { openCurrencyPicker(false) }

        priceView = view.findViewById(R.id.price)

        reviewSend = view.findViewById(R.id.review_send)
        reviewReceive = view.findViewById(R.id.review_receive)

        switchView = view.findViewById(R.id.switch_button)
        switchView.setOnClickListener { switch() }

        paymentView = view.findViewById(R.id.payment)

        button = view.findViewById(R.id.next_button)
        button.setOnClickListener {
            if (slidesView.isFirst) {
                next()
            } else {
                viewModel.openWeb()
            }
        }

        paymentTypeViews = paymentTypesViewIds.map { view.findViewById(it) }
        for (paymentTypeView in paymentTypeViews) {
            paymentTypeView.setOnClickListener {
                if (viewModel.setPaymentMethod(paymentTypeView.tag.toString()) && !slidesView.isFirst) {
                    viewModel.calculate()
                }
            }
        }

        view.findViewById<View>(R.id.edit).setOnClickListener { prev() }

        pairNotAvailableView = view.findViewById(R.id.pair_not_available)
        providerTitleView = view.findViewById(R.id.provider_title)

        val nextContainerView = view.findViewById<View>(R.id.next_container)

        view.doKeyboardAnimation { offset, _, _ ->
            nextContainerView.translationY = -offset.toFloat()
        }

        collectFlow(viewModel.currencyInputStateFlow, ::applyCurrencyInputs)
        collectFlow(viewModel.confirmStateFlow, ::applyConfirmState)

        collectFlow(viewModel.inputValuesFlow) { (sell, buy) ->
            buyInput.setValue(buy)
            sellInput.setValue(sell)
        }

        collectFlow(viewModel.balanceFlow, ::applyBalanceState)

        checkButtonDelay()
    }

    private fun openCurrencyPicker(send: Boolean) {
        hideKeyboard()
        navigation?.add(OnRampPickerScreen.newInstance(wallet, send))
    }

    private fun checkButtonDelay() {
        checkButtonRunnable?.let {
            slidesView.removeCallbacks(it)
        }
        checkButtonRunnable = Runnable { checkButton() }
        slidesView.postDelayed(checkButtonRunnable!!, 80)
    }

    private fun checkButton() {
        if (slidesView.isFirst) {
            button.isLoading = false
            button.isEnabled = viewModel.isFirstButtonEnabled
        } else {
            button.isEnabled = viewModel.isSecondButtonEnabled
        }
    }

    private fun next() {
        hideKeyboard()
        slidesView.next(true)
        countryView.visibility = View.GONE
        viewModel.calculate()

        AnalyticsHelper.onRampEnterAmount(
            installId = viewModel.installId,
            type = viewModel.currencyInputState.purchaseType,
            sellAsset = viewModel.currencyInputState.fromForAnalytics,
            buyAsset = viewModel.currencyInputState.toForAnalytics,
            countryCode = viewModel.currencyInputState.country
        )
    }

    private fun prev() {
        if (!slidesView.isFirst) {
            slidesView.prev(true)
            countryView.visibility = View.VISIBLE
        }
    }

    override fun onBackPressed(): Boolean {
        if (slidesView.isFirst) {
            return super.onBackPressed()
        } else {
            prev()
            return false
        }
    }

    private fun applyBalanceState(state: OnRampBalanceState) {
        if (state.insufficientBalance) {
            sellInput.setInsufficientBalance()
        } else {
            sellInput.setTokenBalance(state.balance, state.remainingFormat)
        }
    }

    private fun applyCurrencyInputs(inputs: OnRampCurrencyInputs) {
        applyRateFormat(inputs.rateFormat)
        countryView.setCountry(inputs.country)
        setCurrency(sellInput, inputs.sell)
        setCurrency(buyInput, inputs.buy)
        applyProvider(inputs.selectedProvider, inputs.providers)
        applyPaymentMethods(inputs.inputMerchantMethods, inputs.paymentType)

        checkButtonDelay()
    }

    private fun applyRateFormat(value: CharSequence?) {
        if (value.isNullOrBlank()) {
            priceView.visibility = View.GONE
        } else {
            priceView.visibility = View.VISIBLE
            priceView.text = value
        }
    }

    private fun applyConfirmState(state: OnRampConfirmState) {
        button.isLoading = state.loading
        reviewSend.setValue(state.toFormat)
        reviewReceive.setValue(state.fromFormat)
        checkButtonDelay()
    }

    private fun applyPaymentMethods(list: List<String>, selected: String?) {
        if (list.isEmpty()) {
            paymentView.visibility = View.GONE
            return
        }
        paymentView.visibility = View.VISIBLE
        hidePaymentMethodViews()
        list.forEach { showPaymentMethod(it) }
        val views = paymentTypeViews.filter { it.isVisible }
        for ((index, view) in views.withIndex()) {
            val position = ListCell.getPosition(views.size, index)
            view.background = position.drawable(requireContext())
            view.isChecked = selected.equals(view.tag.toString(), ignoreCase = true)
        }
    }

    private fun hidePaymentMethodViews() {
        paymentTypeViews.forEach { it.visibility = View.GONE }
    }

    private fun showPaymentMethod(id: String) {
        for (paymentTypeView in paymentTypeViews) {
            val type = paymentTypeView.tag.toString()
            if (type.equals(id, ignoreCase = true)) {
                paymentTypeView.visibility = View.VISIBLE
            }
        }
    }

    private fun applyProvider(provider: PurchaseMethodEntity?, supportedProviders: List<PurchaseMethodEntity>) {
        if (provider == null || supportedProviders.isEmpty()) {
            pairNotAvailableView.visibility = View.VISIBLE
            providerTitleView.visibility = View.GONE
            return
        }
        pairNotAvailableView.visibility = View.GONE
        providerTitleView.visibility = View.VISIBLE
        providerTitleView.text = createProviderText(provider.title)
        providerTitleView.setOnClickListener { pickProvider(provider, supportedProviders) }
    }

    private fun createProviderText(title: String): CharSequence {
        val prefix = getString(Localization.provider)
        val builder = SpannableStringBuilder(prefix)
        builder.append(" ")
        builder.color(requireContext().textSecondaryColor) {
            append(title)
        }
        return builder
    }

    private fun pickProvider(provider: PurchaseMethodEntity, supportedProviders: List<PurchaseMethodEntity>) {
        hideKeyboard()

        lifecycleScope.launch {
            try {
                val newProvider = OnRampProviderPickerScreen.run(requireContext(), wallet, provider, supportedProviders)
                if (viewModel.setProviderId(newProvider.id) && !slidesView.isFirst) {
                    viewModel.calculate()
                }
            } catch (ignored: Throwable) { }
        }
    }

    private fun setCurrency(view: CurrencyInputView, currency: CurrencyInputState) {
        view.setDecimals(currency.decimals)
        when (currency) {
            is CurrencyInputState.TONAsset -> view.setToken(currency.token)
            is CurrencyInputState.Fiat -> view.setCurrency(currency.currency)
            is CurrencyInputState.Crypto -> view.setCurrency(currency.currency)
        }
    }

    private fun pickCountry() {
        hideKeyboard()
        navigation?.add(CountryPickerScreen.newInstance())
    }

    private fun switch() {
        viewModel.switch()
        switchView.animate().rotationBy(180f).start()
    }

    companion object {

        private const val ARG_SOURCE = "source"

        private val paymentTypesViewIds = arrayOf(R.id.payment_cards, R.id.payment_google_pay, R.id.payment_revolut, R.id.payment_paypal)

        fun newInstance(context: Context, wallet: WalletEntity, source: String): BaseFragment {
            return if (context.remoteConfig?.nativeOnrmapEnabled == true) {
                OnRampScreen(wallet).apply {
                    putStringArg(ARG_SOURCE, source)
                }
            } else {
                PurchaseScreen.newInstance(wallet, source)
            }
        }
    }
}
