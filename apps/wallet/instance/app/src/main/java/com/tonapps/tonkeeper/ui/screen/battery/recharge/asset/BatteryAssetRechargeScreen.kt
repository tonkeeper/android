package com.tonapps.tonkeeper.ui.screen.battery.recharge.asset

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.inflate
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.extensions.hideKeyboard
import com.tonapps.tonkeeper.extensions.showToast
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.battery.BatteryScreen
import com.tonapps.tonkeeper.ui.screen.battery.recharge.list.Adapter
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.localization.Localization
import org.koin.android.ext.android.inject
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import com.tonapps.tonkeeper.extensions.failureToast
import uikit.extensions.collectFlow
import uikit.extensions.setPaddingBottom
import uikit.widget.AsyncImageView

/** The recharge screen for an asset on a chain other than TON; the purchase is confirmed on [BatteryPurchaseConfirmSheet]. */
class BatteryAssetRechargeScreen(wallet: WalletEntity) :
    BaseListWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)),
    BaseFragment.BottomSheet {

    override val fragmentName: String = "BatteryAssetRechargeScreen"

    override val hasApplyWindowInsets: Boolean = false

    private val environment: Environment by inject()
    private val args: BatteryAssetRechargeArgs by lazy { BatteryAssetRechargeArgs(requireArguments()) }

    // Stable across recreation: a confirm sheet restored after process death still reaches this screen.
    private val confirmRequestKey: String by lazy { "battery_purchase_${args.assetId}" }

    override val viewModel: BatteryAssetRechargeViewModel by walletViewModel {
        parametersOf(args)
    }

    private val adapter by lazy {
        Adapter(
            onAddressChange = {},
            openAddressBook = {},
            onAmountChange = { viewModel.updateAmount(it) },
            onPackSelect = {
                hideKeyboard()
                viewModel.setSelectedPack(it)
            },
            onCustomAmountSelect = { viewModel.onCustomAmountSelect() },
            onContinue = ::onContinue,
            onSubmitPromo = {
                hideKeyboard()
                viewModel.applyPromo(it)
            },
            environment = environment,
        )
    }

    private lateinit var listContainer: View
    private lateinit var tokenIconView: AsyncImageView
    private lateinit var tokenTitleView: AppCompatTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(viewModel.uiItemsFlow, adapter::submitList)

        navigation?.setFragmentResultListener(confirmRequestKey) {
            viewModel.onPurchaseSent()
            onSuccess()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listContainer = view.findViewById(uikit.R.id.list_container)

        setAdapter(adapter)

        val rightContentView = inflate(context, R.layout.view_battery_recharge_token, null)
        tokenIconView = rightContentView.findViewById(R.id.token_icon)
        tokenIconView.setCircular()
        tokenTitleView = rightContentView.findViewById(R.id.token_title)
        // The asset is picked on the battery screen: nothing to switch here.
        rightContentView.findViewById<View>(R.id.token_switch).visibility = View.GONE

        headerView.hideCloseIcon()
        headerView.setRightContent(rightContentView)
        headerView.setAction(UIKitIcon.ic_close_16)
        headerView.doOnActionClick = { finish() }
        headerView.setTitleGravity(Gravity.START)
        headerView.title = getString(Localization.battery_recharge_title)

        ViewCompat.setOnApplyWindowInsetsListener(listContainer) { _, insets ->
            val offset = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            listContainer.setPaddingBottom(offset)
            insets
        }

        collectFlow(viewModel.accountFlow) { account ->
            tokenTitleView.text = account.asset.symbol
            account.asset.imageUrl.takeIf { it.isNotBlank() }?.let { tokenIconView.setImageURI(it.toUri(), this) }
        }
        collectFlow(viewModel.eventFlow, ::onEvent)
    }

    override fun finish() {
        hideKeyboard()
        super.finish()
    }

    override fun onDragging() {
        super.onDragging()
        hideKeyboard()
    }

    private fun onContinue() {
        hideKeyboard()
        viewModel.onContinue()
    }

    private fun onSuccess() {
        requireContext().showToast(Localization.battery_please_wait)
        navigation?.openURL("tonkeeper://activity?from=battery")
        navigation?.removeByClass({
            finish()
        }, BatteryScreen::class.java)
    }

    private fun onEvent(event: BatteryAssetRechargeEvent) {
        when (event) {
            is BatteryAssetRechargeEvent.Confirm -> navigation?.add(BatteryPurchaseConfirmSheet.newInstance(event.request, confirmRequestKey))
            is BatteryAssetRechargeEvent.Error -> navigation?.failureToast(Localization.sending_error)
            is BatteryAssetRechargeEvent.MaxAmountError -> {
                val message = requireContext().getString(
                    Localization.battery_max_input_amount,
                    CurrencyFormatter.format(currency = event.currency, value = event.maxAmount)
                )
                navigation?.failureToast(message)
            }
        }
    }

    companion object {

        fun newInstance(
            wallet: WalletEntity,
            from: BatteryNativeFrom,
            assetId: String,
            utm: UtmParameters = UtmParameters.Empty,
        ): BatteryAssetRechargeScreen {
            val fragment = BatteryAssetRechargeScreen(wallet)
            fragment.setArgs(BatteryAssetRechargeArgs(assetId, from, utm))
            return fragment
        }
    }
}
