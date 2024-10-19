package com.tonapps.tonkeeper.ui.screen.send.contacts.main

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.screen.send.contacts.ContactDialogHelper
import com.tonapps.tonkeeper.ui.screen.send.contacts.add.AddContactScreen
import com.tonapps.tonkeeper.ui.screen.send.contacts.edit.EditContactScreen
import com.tonapps.tonkeeper.ui.screen.send.contacts.main.list.Adapter
import com.tonapps.tonkeeper.ui.screen.send.contacts.main.list.Item
import com.tonapps.tonkeeper.ui.screen.send.contacts.main.list.holder.ContactHolder
import com.tonapps.tonkeeper.ui.screen.send.main.SendContact
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.topScrolled
import uikit.widget.HeaderView
import uikit.widget.SimpleRecyclerView

class SendContactsScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_send_contacts, wallet), BaseFragment.BottomSheet {

    private val requestKey: String by lazy { arguments?.getString(ARG_REQUEST_KEY)!! }

    private val adapter = Adapter( { selectContact(it) }, { item, actionId ->
        action(item, actionId)
    })

    override val viewModel: SendContactsViewModel by walletViewModel()

    private lateinit var listView: SimpleRecyclerView
    private lateinit var headerView: HeaderView
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(viewModel.uiItemsFlow, adapter::submitList)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.title = getString(Localization.contacts)
        headerView.doOnActionClick = { finish() }

        listView = view.findViewById(R.id.list)
        listView.adapter = adapter
        listView.insideBottomSheet = true

        button = view.findViewById(R.id.button)
        button.setOnClickListener {
            navigation?.add(AddContactScreen.newInstance(wallet))
        }

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.content)) { _, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            listView.updatePadding(bottom = navInsets.bottom + listView.paddingBottom)
            button.translationY = -navInsets.bottom.toFloat()
            insets
        }

        collectFlow(listView.topScrolled, headerView::setDivider)
    }

    private fun selectContact(item: Item) {
        val contact = when (item) {
            is Item.LatestContact -> SendContact(SendContact.CONTACT_TYPE, item.userFriendlyAddress)
            is Item.MyWallet -> SendContact(SendContact.MY_WALLET_TYPE, item.userFriendlyAddress)
            is Item.SavedContact -> SendContact(SendContact.CONTACT_TYPE, item.userFriendlyAddress)
            else -> return
        }

        val bundle = Bundle()
        bundle.putParcelable("contact", contact)
        navigation?.setFragmentResult(requestKey, bundle)
        finish()
    }

    private fun action(item: Item, actionId: Long) {
        if (item is Item.SavedContact) {
            savedContactAction(item, actionId)
        } else if (item is Item.LatestContact) {
            latestContactAction(item, actionId)
        }
    }

    private fun savedContactAction(item: Item.SavedContact, actionId: Long) {
        if (actionId == ContactHolder.EDIT_ID) {
            navigation?.add(EditContactScreen.newInstance(wallet, item.contact))
        } else if (actionId == ContactHolder.DELETE_ID) {
            ContactDialogHelper.delete(requireContext(), item.contact) {
                viewModel.deleteContact(item.contact)
            }
        }
    }

    private fun latestContactAction(item: Item.LatestContact, actionId: Long) {
        if (actionId == ContactHolder.ADD_TO_CONTACTS_ID) {
            navigation?.add(AddContactScreen.newInstance(wallet, item.name, item.userFriendlyAddress))
        } else if (actionId == ContactHolder.HIDE_ID) {
            viewModel.hideContact(item.userFriendlyAddress)
        }
    }

    companion object {

        private const val ARG_REQUEST_KEY = "request_key"

        fun newInstance(wallet: WalletEntity, requestKey: String): SendContactsScreen {
            val screen = SendContactsScreen(wallet)
            screen.putStringArg(ARG_REQUEST_KEY, requestKey)
            return screen
        }
    }
}