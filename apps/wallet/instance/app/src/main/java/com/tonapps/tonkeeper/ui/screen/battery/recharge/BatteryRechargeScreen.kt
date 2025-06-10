package com.tonapps.tonkeeper.ui.screen.battery.recharge

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.inflate
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.tonapps.extensions.bestMessage
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.extensions.hideKeyboard
import com.tonapps.tonkeeper.extensions.showToast
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.koin.installId
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.battery.BatteryScreen
import com.tonapps.tonkeeper.ui.screen.battery.recharge.entity.BatteryRechargeEvent
import com.tonapps.tonkeeper.ui.screen.battery.recharge.list.Adapter
import com.tonapps.tonkeeper.ui.screen.send.contacts.main.SendContactsScreen
import com.tonapps.tonkeeper.ui.screen.send.main.SendContact
import com.tonapps.tonkeeper.ui.screen.token.picker.TokenPickerScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.setPaddingBottom
import uikit.widget.FrescoView
import uikit.widget.InputView
import java.util.UUID

class BatteryRechargeScreen(wallet: WalletEntity) :
    BaseListWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)),
    BaseFragment.BottomSheet {

    override val fragmentName: String = "BatteryRechargeScreen"

    override val hasApplyWindowInsets: Boolean = false

    private val args: RechargeArgs by lazy { RechargeArgs(requireArguments()) }
    private val contractsRequestKey: String by lazy { "contacts_${UUID.randomUUID()}" }
    private val tokenRequestKey: String by lazy { "token_${UUID.randomUUID()}" }

    override val viewModel: BatteryRechargeViewModel by walletViewModel {
        parametersOf(args)
    }

    private val adapter = Adapter(
        onAddressChange = { viewModel.updateAddress(it) },
        openAddressBook = ::openAddressBook,
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
        }
    )

    private lateinit var listContainer: View
    private lateinit var tokenIconView: FrescoView
    private lateinit var tokenTitleView: AppCompatTextView

    private val addressInput: InputView?
        get() = findListItemView(0)?.findViewById(R.id.address)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(viewModel.uiItemsFlow, adapter::submitList)

        navigation?.setFragmentResultListener(contractsRequestKey) { bundle ->
            bundle.getParcelableCompat<SendContact>("contact")?.let {
                addressInput?.text = it.address
            }
        }

        navigation?.setFragmentResultListener(tokenRequestKey) { bundle ->
            bundle.getParcelableCompat<TokenEntity>(TokenPickerScreen.TOKEN)?.let {
                viewModel.setToken(it)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listContainer = view.findViewById(uikit.R.id.list_container)

        setAdapter(adapter)

        val rightContentView = inflate(context, R.layout.view_battery_recharge_token, null)
        tokenIconView = rightContentView.findViewById(R.id.token_icon)
        tokenTitleView = rightContentView.findViewById(R.id.token_title)
        rightContentView.findViewById<LinearLayoutCompat>(R.id.token)
            .setOnClickListener { openTokenSelector() }

        headerView.hideCloseIcon()
        headerView.setRightContent(rightContentView)
        headerView.setAction(UIKitIcon.ic_close_16)
        headerView.doOnActionClick = { finish() }
        headerView.setTitleGravity(Gravity.START)
        headerView.title = when (args.isGift) {
            true -> getString(Localization.battery_gift_title)
            false -> getString(Localization.battery_recharge_title)
        }


        ViewCompat.setOnApplyWindowInsetsListener(listContainer) { _, insets ->
            val offset = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            updateContainerOffset(offset)
            insets
        }

        collectFlow(viewModel.tokenFlow) { token ->
            tokenTitleView.text = token.symbol
            tokenIconView.setImageURI(token.imageUri, this)
        }
        collectFlow(viewModel.eventFlow, ::onEvent)
    }

    // Dirty hack because of bad design
    private fun updateContainerOffset(offset: Int) {
        listContainer.setPaddingBottom(offset)
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

    private fun openAddressBook() {
        navigation?.add(SendContactsScreen.newInstance(screenContext.wallet, contractsRequestKey))
        hideKeyboard()
    }

    private fun openTokenSelector() {
        combine(
            viewModel.supportedTokensFlow.take(1),
            viewModel.tokenFlow.take(1)
        ) { allowedTokens, selectedToken ->
            navigation?.add(
                TokenPickerScreen.newInstance(
                wallet = screenContext.wallet,
                requestKey = tokenRequestKey,
                selectedToken = selectedToken.balance.token,
                allowedTokens = allowedTokens.map { it.address }
            ))
        }.launchIn(lifecycleScope)
    }

    private fun showError(message: String? = null) {
        navigation?.toast(message ?: getString(Localization.sending_error))
    }

    private fun onSuccess() {
        requireContext().showToast(Localization.battery_please_wait)
        navigation?.openURL("tonkeeper://activity?from=battery")
        navigation?.removeByClass({
            finish()
        }, BatteryScreen::class.java)
    }

    private fun sign(request: SignRequestEntity, forceRelayer: Boolean) {
        viewModel.sign(request, forceRelayer).catch {
            showError(it.bestMessage)
        }.onEach {
            postDelayed(1000) {
                onSuccess()
            }
        }.launchIn(lifecycleScope)
    }

    private fun onEvent(event: BatteryRechargeEvent) {
        when (event) {
            is BatteryRechargeEvent.Sign -> sign(event.request, event.forceRelayer)
            is BatteryRechargeEvent.Error -> showError()
            is BatteryRechargeEvent.MaxAmountError -> {
                val message = requireContext().getString(
                    Localization.battery_max_input_amount,
                    CurrencyFormatter.format(currency = event.currency, value = event.maxAmount)
                )
                showError(message)
            }
        }
    }

    companion object {

        fun newInstance(
            wallet: WalletEntity,
            token: AccountTokenEntity? = null,
            isGift: Boolean = false
        ): BatteryRechargeScreen {
            val fragment = BatteryRechargeScreen(wallet)
            fragment.setArgs(RechargeArgs(token, isGift))
            return fragment
        }
    }
}