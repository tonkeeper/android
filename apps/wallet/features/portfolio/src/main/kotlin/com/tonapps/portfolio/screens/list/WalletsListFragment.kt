package com.tonapps.portfolio.screens.list

import android.os.Bundle
import android.view.View
import com.tonapps.core.ComposableFragment
import com.tonapps.core.navigation.NavigationDelegate
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.activity

class WalletsListFragment : ComposableFragment(), BaseFragment.Modal {

    override val fragmentName: String = "WalletsListFragment"

    private val feature: WalletsListFeature by viewModel()

    private var pendingWalletId: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val delegate = context?.activity as? NavigationDelegate
        setContent {
            WalletsListScreen(
                feature = feature,
                onAddWalletClick = {
                    delegate?.onOpenAddWallet()
                    finish()
                },
                onSelectWallet = { walletId ->
                    pendingWalletId = walletId
                    finish()
                },
                onOpenLink = { url -> delegate?.onOpenLink(url) },
                onEditWallet = { walletId ->
                    delegate?.onOpenWalletLabelEdit(walletId)
                },
                onClose = { finish() },
            )
        }
    }

    override fun onDestroyView() {
        pendingWalletId?.let { id ->
            feature.selectWallet(id)
            pendingWalletId = null
        }
        super.onDestroyView()
    }
}
