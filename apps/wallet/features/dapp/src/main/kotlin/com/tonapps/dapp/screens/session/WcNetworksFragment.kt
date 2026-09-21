package com.tonapps.dapp.screens.session

import android.os.Bundle
import android.view.View
import androidx.core.os.BundleCompat
import com.tonapps.core.ComposableFragment
import com.tonapps.wallet.data.multichain.account.AccountEntity
import uikit.base.BaseFragment

class WcNetworksFragment : ComposableFragment(), BaseFragment.BottomSheet {

    override val fragmentName: String = "WcNetworksFragment"

    private val accounts: List<NetworkAccountArg>
        get() = BundleCompat.getParcelableArrayList(
            requireArguments(),
            ARG_ACCOUNTS,
            NetworkAccountArg::class.java,
        ).orEmpty()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val accountEntities = accounts.map { it.toAccountEntity() }
        if (accountEntities.isEmpty()) {
            finish()
            return
        }

        setContent {
            WcNetworksScreen(
                accounts = accountEntities,
                onClose = { finish() },
            )
        }
    }

    companion object {
        private const val ARG_ACCOUNTS = "accounts"

        fun newInstance(accounts: List<AccountEntity>): BaseFragment {
            val fragment = WcNetworksFragment()
            fragment.putParcelableArrayListArg(
                ARG_ACCOUNTS,
                ArrayList(accounts.map(NetworkAccountArg::from)),
            )
            return fragment
        }
    }
}
