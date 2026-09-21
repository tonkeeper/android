package com.tonapps.dapp.screens.sessions

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tonapps.core.ComposableFragment
import ui.theme.UIKit
import uikit.base.BaseFragment

class DisconnectDappFragment : ComposableFragment(), BaseFragment.Modal {

    override val fragmentName: String = "DisconnectDappFragment"

    private val name: String
        get() = requireArguments().getString(ARG_NAME).orEmpty()

    private val iconUrl: String?
        get() = requireArguments().getString(ARG_ICON_URL)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setContent {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(UIKit.colorScheme.background.page)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DisconnectDappContent(
                    name = name,
                    iconUrl = iconUrl,
                    onDisconnect = {
                        setResult(Bundle().apply { putBoolean(RESULT_CONFIRMED, true) })
                    },
                    onClose = { finish() },
                )
            }
        }
    }

    companion object {
        private const val ARG_NAME = "name"
        private const val ARG_ICON_URL = "icon_url"
        const val RESULT_CONFIRMED = "confirmed"

        fun newInstance(name: String, iconUrl: String?): BaseFragment {
            val fragment = DisconnectDappFragment()
            fragment.putStringArg(ARG_NAME, name)
            fragment.putStringArg(ARG_ICON_URL, iconUrl)
            return fragment
        }
    }
}
