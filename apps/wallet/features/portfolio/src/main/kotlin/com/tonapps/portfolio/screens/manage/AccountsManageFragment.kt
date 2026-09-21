package com.tonapps.portfolio.screens.manage

import android.os.Bundle
import android.view.View
import com.tonapps.core.ComposableFragment
import org.koin.androidx.compose.koinViewModel
import uikit.base.BaseFragment

class AccountsManageFragment : ComposableFragment(), BaseFragment.BottomSheet {

    override val fragmentName: String = "ManageFragment"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setContent {
            val feature = koinViewModel<AccountsManageFeature>()
            AccountsManageScreen(
                feature = feature,
                onBack = { finish() },
            )
        }
    }

    companion object {
        fun newInstance(): AccountsManageFragment = AccountsManageFragment()
    }
}