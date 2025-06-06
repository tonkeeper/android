package com.tonapps.tonkeeper.ui.screen.onramp.picker.currency

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import com.tonapps.tonkeeper.extensions.hideKeyboard
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.base.picker.QueryReceiver
import com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.fiat.OnRampFiatScreen
import com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.main.OnRampCurrencyPickerScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency
import com.tonapps.wallet.localization.Localization
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.commitChildAsSlide
import uikit.widget.ModalHeader
import uikit.widget.SearchInput

class OnRampPickerScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_onramp_picker, wallet), BaseFragment.BottomSheet {

    private val send: Boolean by lazy { requireArguments().getBoolean(ARG_SEND) }

    override val viewModel: OnRampPickerViewModel by walletViewModel { parametersOf(send) }

    private val currentFragment: Fragment?
        get() = childFragmentManager.fragments.lastOrNull()

    private lateinit var headerView: ModalHeader
    private lateinit var searchInput: SearchInput

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.setOnClickListener { hideKeyboard() }
        headerView.onCloseClick = { finish() }

        searchInput = view.findViewById(R.id.search)
        searchInput.doOnTextChanged = { text -> query(text.toString()) }

        collectFlow(viewModel.uiCommandFlow, ::onCommand)
        applyDefaultTitle()
    }

    private fun applyDefaultTitle() {
        headerView.setTitle(if (send) Localization.sell else Localization.buy)
    }

    private fun query(value: String) {
        (currentFragment as? QueryReceiver)?.onQuery(value)
    }

    private fun onCommand(command: OnRampPickerCommand) {
        when (command) {
            is OnRampPickerCommand.OpenCurrencyPicker -> setCurrencies(command.currencies)
            is OnRampPickerCommand.Main -> setFragment(OnRampCurrencyPickerScreen.newInstance(wallet))
            is OnRampPickerCommand.Finish -> finish()
        }
    }

    private fun setCurrencies(currencies: List<WalletCurrency>) {
        setFragment(OnRampFiatScreen.newInstance(currencies))
        headerView.setTitle(Localization.currency)
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
        applyDefaultTitle()
        childFragmentManager.popBackStack()
        searchInput.cancel()
        return false
    }

    companion object {

        fun parentViewModel(screen: Fragment): OnRampPickerViewModel {
            return (screen as OnRampPickerScreen).viewModel
        }

        private const val ARG_SEND = "send"

        fun newInstance(wallet: WalletEntity, send: Boolean): OnRampPickerScreen {
            val fragment = OnRampPickerScreen(wallet)
            fragment.putBooleanArg(ARG_SEND, send)
            return fragment
        }
    }
}