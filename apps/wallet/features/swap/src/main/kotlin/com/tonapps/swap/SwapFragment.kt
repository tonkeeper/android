package com.tonapps.swap

import android.os.Bundle
import android.view.View
import com.tonapps.core.ComposableFragment
import com.tonapps.core.helper.navigationDelegate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uikit.base.BaseFragment

class SwapFragment : ComposableFragment(), BaseFragment.BottomSheet {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val startRoute = arguments?.getString(ARG_INITIAL)
            ?.let { Json.decodeFromString<SwapRoutes>(it) }
            ?: SwapRoutes.Swap()

        setContent {
            SwapRouter(
                initial = startRoute,
                onBack = { finish() },
                onContinue = { request ->
                    context?.navigationDelegate?.onOpenConfirm(request)
                },
                onOpenLink = { url ->
                    context?.navigationDelegate?.onOpenLink(url)
                },
            )
        }
    }

    companion object {
        private const val ARG_INITIAL = "arg_initial"

        fun create(
            initial: SwapRoutes? = null,
        ): SwapFragment {
            return SwapFragment().apply {
                arguments = Bundle().apply {
                    initial?.let {
                        putString(ARG_INITIAL, Json.encodeToString(initial))
                    }
                }
            }
        }
    }
}
