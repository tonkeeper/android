package com.tonapps.tonkeeper.ui.screen.send.contacts

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.ui.screen.send.contacts.list.Adapter
import com.tonapps.tonkeeper.ui.screen.send.contacts.list.Item
import com.tonapps.tonkeeper.ui.screen.send.main.SendContact
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.base.BaseListFragment
import uikit.extensions.collectFlow
import uikit.navigation.Navigation.Companion.navigation

class SendContactsScreen: BaseListFragment(), BaseFragment.BottomSheet {

    private val requestKey: String by lazy { arguments?.getString(ARG_REQUEST_KEY)!! }

    private val adapter = Adapter { selectContact(it) }

    private val sendContactsViewModel: SendContactsViewModel by viewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(getString(Localization.contacts))
        setAdapter(adapter)
        collectFlow(sendContactsViewModel.uiItemsFlow, adapter::submitList)
    }

    private fun selectContact(item: Item) {
        val contact = when (item) {
            is Item.LatestContact -> SendContact(SendContact.CONTACT_TYPE, item.userFriendlyAddress)
            is Item.MyWallet -> SendContact(SendContact.MY_WALLET_TYPE, item.userFriendlyAddress)
            else -> return
        }

        val bundle = Bundle()
        bundle.putParcelable("contact", contact)
        navigation?.setFragmentResult(requestKey, bundle)
        finish()
    }

    companion object {

        private const val ARG_REQUEST_KEY = "request_key"

        fun newInstance(requestKey: String): SendContactsScreen {
            val screen = SendContactsScreen()
            screen.arguments = Bundle().apply {
                putString(ARG_REQUEST_KEY, requestKey)
            }
            return screen
        }
    }
}