package com.tonapps.tonkeeper.ui.screen.onramp.picker.currency

import android.content.Context
import android.os.Bundle
import com.tonapps.log.L
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.extensions.hideKeyboard
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.base.picker.QueryReceiver
import com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.fiat.OnRampFiatScreen
import com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.main.OnRampCurrencyPickerScreen
import com.tonapps.tonkeeper.ui.screen.swap.picker.SwapPickerArgs
import com.tonapps.tonkeeper.ui.screen.swap.picker.SwapPickerScreen
import com.tonapps.tonkeeper.ui.screen.swap.picker.SwapPickerViewModel
import com.tonapps.tonkeeperx.R
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.wallet.localization.Localization
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.activity
import uikit.extensions.addForResult
import uikit.extensions.collectFlow
import uikit.extensions.commitChildAsSlide
import uikit.widget.HeaderView
import uikit.widget.ModalHeader
import uikit.widget.SearchInput
import kotlin.coroutines.cancellation.CancellationException

class OnRampPickerScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_onramp_picker, wallet), BaseFragment.BottomSheet {

    private val args: OnRampPickerArgs by lazy { OnRampPickerArgs(requireArguments()) }

    private val defaultTitle: Int
        get() = if (args.send) Localization.send else Localization.receive

    override val viewModel: OnRampPickerViewModel by walletViewModel {
        parametersOf(args)
    }

    private val currentFragment: Fragment?
        get() = childFragmentManager.fragments.lastOrNull()

    private lateinit var modalHeaderView: ModalHeader
    private lateinit var headerView: HeaderView
    private lateinit var searchInput: SearchInput

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        modalHeaderView = view.findViewById(R.id.modal_header)
        modalHeaderView.setOnClickListener { hideKeyboard() }
        modalHeaderView.onCloseClick = { finish() }

        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { back() }
        headerView.doOnActionClick = { finish() }

        searchInput = view.findViewById(R.id.search)
        searchInput.doOnTextChanged = { text -> query(text.toString()) }

        collectFlow(viewModel.uiCommandFlow, ::onCommand)
        showModalHeader()
    }

    override fun onDragging() {
        super.onDragging()
        hideKeyboard()
    }

    private fun query(value: String) {
        (currentFragment as? QueryReceiver)?.onQuery(value)
    }

    private fun onCommand(command: OnRampPickerCommand) {
        when (command) {
            is OnRampPickerCommand.OpenCurrencyPicker -> setCurrencies(command.localization,command.currencies, command.extras)
            is OnRampPickerCommand.Main -> setFragment(OnRampCurrencyPickerScreen.newInstance(wallet))
            is OnRampPickerCommand.Finish -> finish()
            is OnRampPickerCommand.SetCurrency -> setResult(command.currency)
        }
    }

    private fun setCurrencies(localization: Int, currencies: List<WalletCurrency>, extras: List<String>) {
        setFragment(OnRampFiatScreen.newInstance(currencies, extras))
        showHeader(localization)
    }

    private fun showHeader(localization: Int) {
        headerView.title = getString(localization)
        headerView.visibility = View.VISIBLE
        modalHeaderView.visibility = View.GONE
    }

    private fun showModalHeader() {
        headerView.visibility = View.GONE
        modalHeaderView.visibility = View.VISIBLE
        modalHeaderView.setTitle(defaultTitle)
    }

    private fun setFragment(fragment: Fragment) {
        searchInput.cancel()

        childFragmentManager.commitChildAsSlide {
            replace(R.id.fragment, fragment)
            addToBackStack(fragment.javaClass.name)
        }
    }

    override fun onBackPressed(): Boolean {
        val lastFragment = currentFragment as? BaseFragment
        if (lastFragment == null || lastFragment is OnRampCurrencyPickerScreen) {
            return super.onBackPressed()
        }
        back()
        return false
    }
    
    private fun back() {
        showModalHeader()
        childFragmentManager.popBackStack()
        searchInput.cancel()
    }

    private fun setResult(currency: WalletCurrency) {
        setResult(Bundle().apply {
            putParcelable(ARG_CURRENCY, currency)
        })
        finish()
    }

    companion object {

        private const val ARG_CURRENCY = "currency"

        fun parentViewModel(screen: Fragment): OnRampPickerViewModel {
            return (screen as OnRampPickerScreen).viewModel
        }

        private fun newInstance(wallet: WalletEntity, currency: WalletCurrency, send: Boolean): OnRampPickerScreen {
            val args = OnRampPickerArgs(currency, send)
            val screen = OnRampPickerScreen(wallet)
            screen.setArgs(args)
            return screen
        }

        suspend fun run(
            context: Context,
            wallet: WalletEntity,
            currency: WalletCurrency,
            send: Boolean
        ): WalletCurrency {
            val activity = context.activity ?: throw IllegalArgumentException("Context must be an Activity")
            val screen = newInstance(wallet, currency, send)
            val result = activity.addForResult(screen)
            return result.getParcelableCompat<WalletCurrency>(ARG_CURRENCY) ?: throw CancellationException()
        }
    }
}