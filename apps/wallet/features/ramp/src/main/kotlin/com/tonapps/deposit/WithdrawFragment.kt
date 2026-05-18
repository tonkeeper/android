package com.tonapps.deposit

import android.os.Bundle
import android.view.View
import com.tonapps.core.ComposableFragment
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uikit.base.BaseFragment
import uikit.extensions.activity
import uikit.navigation.Navigation.Companion.navigation

class WithdrawFragment private constructor(): ComposableFragment(), BaseFragment.BottomSheet {

    interface Delegate {
        fun onOpenProvider(url: String)
        fun onShowError(message: String)
        fun onOpenAddressBook(onResult: (String) -> Unit)
        fun onBuyTon() {}
        fun onGetTrx() {}
        fun onRechargeBattery() {}
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val delegate = context?.activity as? Delegate

        val startRoute = requireArguments().getString(ARG_INITIAL)
            ?.let { Json.decodeFromString<WithdrawRoutes>(it) }
            ?: WithdrawRoutes.Ramp

        setContent {
            WithdrawRouter(
                initial = startRoute,
                onBack = { finish() },
                onSendSuccess = {
                    navigation?.openURL("tonkeeper://activity?from=send")
                    finish()
                },
                openProvider = {
                    finish()
                    delegate?.onOpenProvider(it)
                },
                onAddressBook = delegate?.let { d ->
                    { onResult -> d.onOpenAddressBook(onResult) }
                },
                onShowError = {
                    delegate?.onShowError(it)
                },
                onBuyTon = { delegate?.onBuyTon() },
                onGetTrx = { delegate?.onGetTrx() },
                onRechargeBattery = { delegate?.onRechargeBattery() },
            )
        }
    }

    companion object {
        private const val ARG_INITIAL = "arg_initial"

        fun create(
            initial: WithdrawRoutes? = null,
        ): WithdrawFragment {
            return WithdrawFragment().apply {
                arguments = Bundle().apply {
                    initial?.let {
                        putString(ARG_INITIAL, Json.encodeToString(initial))
                    }
                }
            }
        }
    }
}
