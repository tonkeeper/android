package com.tonapps.tonkeeper.ui.screen.onramp.main

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.lifecycleScope
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.extensions.toUriOrNull
import com.tonapps.tonkeeper.extensions.hideKeyboard
import com.tonapps.tonkeeper.extensions.isOverlapping
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.screen.onramp.main.entities.ProviderEntity
import com.tonapps.tonkeeper.ui.screen.onramp.main.state.UiState
import com.tonapps.tonkeeper.ui.screen.onramp.main.view.ReviewInputView
import com.tonapps.tonkeeper.ui.screen.onramp.picker.provider.OnRampProviderPickerScreen
import com.tonapps.tonkeeper.ui.screen.purchase.PurchaseConfirmDialog
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.backgroundPageColor
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.purchase.entity.OnRamp
import com.tonapps.wallet.localization.Links
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import uikit.base.BaseFragment
import uikit.drawable.FooterDrawable
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.span.ClickableSpanCompat
import uikit.widget.HeaderView
import uikit.widget.LoadableButton
import uikit.widget.SlideBetweenView

open class BaseOnRampScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_onramp, wallet), BaseFragment.BottomSheet {

    private val confirmDialog: PurchaseConfirmDialog by lazy {
        PurchaseConfirmDialog(requireContext())
    }

    override val viewModel: OnRampViewModel by walletViewModel()

    lateinit var slidesView: SlideBetweenView
    lateinit var reviewReceive: ReviewInputView
    lateinit var reviewSend: ReviewInputView

    private lateinit var headerView: HeaderView
    private lateinit var nextContainerView: ViewGroup
    private lateinit var nextContainerBodyView: View
    lateinit var button: LoadableButton
    private lateinit var pairNotAvailableView: AppCompatTextView
    private lateinit var providerContainerView: ViewGroup
    private lateinit var providerTitleView: AppCompatTextView
    private lateinit var disclaimerView: AppCompatTextView
    private lateinit var actionContainerDrawable: FooterDrawable
    lateinit var minMaxView: AppCompatTextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        slidesView = view.findViewById(R.id.slides)

        headerView = view.findViewById(R.id.header)
        headerView.doOnActionClick = { finish() }

        reviewReceive = view.findViewById(R.id.review_receive)
        reviewSend = view.findViewById(R.id.review_send)

        actionContainerDrawable = FooterDrawable(requireContext())
        actionContainerDrawable.setColor(requireContext().backgroundPageColor)

        nextContainerView = view.findViewById(R.id.next_container)
        nextContainerBodyView = view.findViewById(R.id.next_container_body)
        nextContainerBodyView.background = actionContainerDrawable

        button = view.findViewById(R.id.next_button)

        minMaxView = view.findViewById(R.id.min_max)
        pairNotAvailableView = view.findViewById(R.id.pair_not_available)
        providerContainerView = view.findViewById(R.id.provider_container)

        providerTitleView = view.findViewById(R.id.provider_title)
        providerTitleView.text = getString(Localization.provider)



        disclaimerView = view.findViewById(R.id.disclaimer)
        disclaimerView.movementMethod = LinkMovementMethod.getInstance()
        applyDisclaimer()

        view.doKeyboardAnimation { offset, progress, isShowing ->
            onKeyboardAnimation(offset, progress, isShowing)
            updateDivider()
        }

        collectFlow(viewModel.isChangellyFlow, ::applyIsChangelly)
        collectFlow(viewModel.openWidgetFlow, ::openWidget)
        collectFlow(viewModel.allowedPairFlow, ::applyAllowedPair)
        collectFlow(viewModel.stepFlow, ::applyStep)
        collectFlow(viewModel.buttonUiStateFlow, button::applyUiState)
        collectFlow(viewModel.selectedProviderUiStateFlow, ::applySelectedProvider)
        collectFlow(viewModel.minAmountFlow.map { it?.formatted }) {
            if (it == null) {
                minMaxView.visibility = View.GONE
            } else {
                minMaxView.visibility = View.VISIBLE
                minMaxView.text = getString(Localization.min_amount, it)
            }
        }
    }

    fun updateDivider() {
        if (disclaimerView.visibility != View.VISIBLE) {
            actionContainerDrawable.setDivider(false)
            return
        }
        val priceView = view?.findViewById<View>(R.id.price) ?: return
        val priceReversedView = view?.findViewById<View>(R.id.price_reversed) ?: return
        val needDivider = nextContainerBodyView.isOverlapping(priceView) || nextContainerBodyView.isOverlapping(priceReversedView)
        // actionContainerDrawable.setDivider(needDivider)
    }

    private fun applyDisclaimer() {
        val textSecondaryColor = requireContext().textSecondaryColor
        val provider = getString(Localization.changelly)
        val termsOfUse = getString(Localization.terms_of_use)
        val privacyPolicy = getString(Localization.privacy_policy)
        val text = requireContext().getString(Localization.swap_disclaimer, provider, termsOfUse, privacyPolicy)
        val builder = SpannableStringBuilder(text)

        val providerStart = text.indexOf(provider)
        val providerEnd = providerStart + provider.length
        builder.setSpan(ClickableSpanCompat(textSecondaryColor) {
            BrowserHelper.open(requireContext(), Links.Changelly)
        }, providerStart, providerEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val termsOfUseStart = text.indexOf(termsOfUse)
        val termsOfUseEnd = termsOfUseStart + termsOfUse.length
        builder.setSpan(ClickableSpanCompat(textSecondaryColor) {
            BrowserHelper.open(requireContext(), Links.ChangellyTerms)
        }, termsOfUseStart, termsOfUseEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val privacyPolicyStart = text.indexOf(privacyPolicy)
        val privacyPolicyEnd = privacyPolicyStart + privacyPolicy.length
        builder.setSpan(ClickableSpanCompat(textSecondaryColor) {
            BrowserHelper.open(requireContext(), Links.ChangellyPrivacy)
        }, privacyPolicyStart, privacyPolicyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        disclaimerView.text = builder
    }

    override fun finish() {
        super.finish()
        hideKeyboard()
    }

    override fun onDragging() {
        super.onDragging()
        hideKeyboard()
    }

    private fun applyStep(step: UiState.Step) {
        if (step == UiState.Step.Input) {
            inputsUiState()
            slidesView.prev()
        } else if (step == UiState.Step.Confirm) {
            confirmUiState()
            slidesView.next()
        }
    }

    private fun applyIsChangelly(isChangelly: Boolean) {
        disclaimerView.visibility = if (isChangelly) View.VISIBLE else View.GONE
        updateDivider()
    }

    private fun applySelectedProvider(state: UiState.SelectedProvider) {
        reviewSend.setValue(state.sendFormat)
        reviewReceive.setValue(state.receiveFormat)
        providerTitleView.text = state.getPreviewText(requireContext())
        providerContainerView.setOnClickListener { openProviderPicker(state) }
        button.setOnClickListener {
            if (slidesView.isFirst) {
                requestAvailableProviders()
            } else {
                openWidget(state.selectedProvider)
            }
        }
    }

    private fun applyAllowedPair(allowedPair: OnRamp.AllowedPair?) {
        pairNotAvailableView.visibility = if (allowedPair == null) View.VISIBLE else View.GONE
    }

    private fun openProviderPicker(state: UiState.SelectedProvider) {
        hideKeyboard()
        lifecycleScope.launch {
            runCatching {
                OnRampProviderPickerScreen.run(requireContext(), wallet, state)
            }.onSuccess(viewModel::setSelectedProviderId)
        }
    }

    open fun onKeyboardAnimation(offset: Int, progress: Float, isShowing: Boolean) {
        nextContainerView.translationY = -offset.toFloat()
    }

    private fun inputsUiState() {
        headerInputs()
        providerContainerView.visibility = View.GONE
        minMaxView.visibility = View.VISIBLE
        button.setOnClickListener { requestAvailableProviders() }
    }

    private fun confirmUiState() {
        providerContainerView.visibility = View.VISIBLE
        minMaxView.visibility = View.GONE
        headerConfirm()
        hideKeyboard()
    }

    private fun headerInputs() {
        headerView.setIcon(0)
        headerView.doOnCloseClick = null
    }

    private fun headerConfirm() {
        headerView.setIcon(UIKitIcon.ic_chevron_left_16)
        headerView.doOnCloseClick = { viewModel.reset() }
    }

    override fun onBackPressed(): Boolean {
        if (slidesView.isFirst) {
            return super.onBackPressed()
        } else {
            viewModel.reset()
            return false
        }
    }

    fun requestAvailableProviders() {
        hideKeyboard()
        viewModel.requestAvailableProviders()

        analytics?.onRampEnterAmount(
            type = viewModel.purchaseType,
            sellAsset = viewModel.fromForAnalytics,
            buyAsset = viewModel.toForAnalytics,
            countryCode = viewModel.country
        )
    }

    private fun openWidget(provider: ProviderEntity?) {
        if (provider == null) {
            viewModel.reset()
            return
        }
        if (viewModel.isPurchaseOpenConfirm(provider.id)) {
            confirmDialog.show(provider) { showAgain ->
                openWebView(provider.widgetUrl, provider.id, provider.merchantTxId)
                if (!showAgain) {
                    viewModel.disableConfirmDialog(screenContext.wallet, provider.id)
                }
            }
        } else {
            openWebView(provider.widgetUrl, provider.id, provider.merchantTxId)
        }
    }

    private fun openWebView(url: String, selectedProvider: String, merchantTxId: String?) {
        button.isLoading = true

        BrowserHelper.open(requireContext(), url)
        lifecycleScope.launch {
            delay(3000)
            button.isLoading = false
        }

        val providerDomain = url.toUriOrNull()?.host ?: "unknown"

        viewModel.trackContinueToProvider(
            providerName = selectedProvider,
            providerDomain = providerDomain,
            txId = merchantTxId
        )

        analytics?.onRampOpenWebview(
            type = viewModel.purchaseType,
            sellAsset = viewModel.fromForAnalytics,
            buyAsset = viewModel.toForAnalytics,
            countryCode = viewModel.country,
            paymentMethod = viewModel.paymentMethod,
            providerName = selectedProvider,
            providerDomain = providerDomain
        )
    }
}