package com.tonapps.deposit

import android.os.Bundle
import android.view.View
import com.tonapps.core.ComposableFragment
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uikit.base.BaseFragment
import uikit.extensions.activity

class DepositFragment : ComposableFragment(), BaseFragment.BottomSheet {

    interface Delegate {
        fun onOpenProvider(url: String)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val delegate = context?.activity as? Delegate

        val startRoute = arguments?.getString(ARG_INITIAL)
            ?.let { Json.decodeFromString<DepositRoutes>(it) }
            ?: DepositRoutes.Ramp

        setContent {
            DepositRouter(
                initial = startRoute,
                onBack = { finish() },
                openProvider = {
                    finish()
                    delegate?.onOpenProvider(it)
                },
            )
        }
    }

    companion object {
        private const val ARG_INITIAL = "arg_initial"

        fun create(
            initial: DepositRoutes? = null,
        ): DepositFragment {
            return DepositFragment().apply {
                arguments = Bundle().apply {
                    initial?.let {
                        putString(ARG_INITIAL, Json.encodeToString(initial))
                    }
                }
            }
        }
    }
}
