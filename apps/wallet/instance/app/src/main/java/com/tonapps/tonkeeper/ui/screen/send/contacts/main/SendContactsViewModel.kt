package com.tonapps.tonkeeper.ui.screen.send.contacts.main

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.tonkeeper.api.isOutTransfer
import com.tonapps.tonkeeper.api.isTransfer
import com.tonapps.tonkeeper.core.entities.WalletExtendedEntity
import com.tonapps.tonkeeper.core.history.recipient
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.send.contacts.main.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.contacts.ContactsRepository
import com.tonapps.wallet.data.contacts.entities.ContactEntity
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import io.tonapi.models.AccountAddress
import io.tonapi.models.AccountEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SendContactsViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val accountRepository: AccountRepository,
    private val eventsRepository: EventsRepository,
    private val settingsRepository: SettingsRepository,
    private val contactsRepository: ContactsRepository,
    private val api: API
): BaseWalletVM(app) {

    private val _myWalletsFlow = MutableStateFlow<List<Item.MyWallet>>(emptyList())
    private val myWalletsFlow = _myWalletsFlow.asStateFlow()

    private val _latestContactsFlow = MutableStateFlow<List<Item>>(listOf(Item.Loading))
    private val latestContactsFlow = _latestContactsFlow.asStateFlow()

    private val savedContactsFlow = contactsRepository.contactsFlow.map {
        it.mapIndexed { index, contact ->
            val position = ListCell.getPosition(it.size, index)
            Item.SavedContact(position, contact, wallet.testnet)
        }
    }

    val uiItemsFlow = combine(
        myWalletsFlow,
        savedContactsFlow,
        latestContactsFlow
    ) { myWallets, savedContacts, latestContacts ->
        val uiItems = mutableListOf<Item>()
        if (myWallets.isNotEmpty()) {
            uiItems.addAll(myWallets)
            uiItems.add(Item.Space)
        }

        /*if (savedContacts.isNotEmpty()) {
            uiItems.addAll(savedContacts)
            uiItems.add(Item.Space)
        }*/

        if (latestContacts.isNotEmpty()) {
            uiItems.addAll(latestContacts)
            uiItems.add(Item.Space)
        }

        uiItems.toList()
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _myWalletsFlow.value = getMyWallets()
        }

        viewModelScope.launch(Dispatchers.IO) {
            _latestContactsFlow.value = getLatestContacts()
        }
    }

    fun hideContact(address: String) {
        viewModelScope.launch(Dispatchers.IO) {
            contactsRepository.hide(address.toRawAddress(), wallet.testnet)
            val newLatestContactsFlow = _latestContactsFlow.value.filter {
                it is Item.LatestContact && !it.account.address.equalsAddress(address)
            }.map { it as Item.LatestContact }

            _latestContactsFlow.value = newLatestContactsFlow.mapIndexed { index, latestContact ->
                latestContact.copy(position = ListCell.getPosition(newLatestContactsFlow.size, index))
            }
        }
    }

    fun deleteContact(contact: ContactEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            contactsRepository.deleteContact(contact.id)
        }
    }

    private suspend fun getMyWallets(): List<Item.MyWallet> {
        val wallets = accountRepository.getWallets().filter {
            it.type != Wallet.Type.Watch && it.testnet == wallet.testnet && it.address != wallet.address
        }.map {
            WalletExtendedEntity( it, settingsRepository.getWalletPrefs(it.id))
        }.sortedBy { it.index }.map { it.raw }.toList()

        return wallets.mapIndexed { index, wallet ->
            val position = ListCell.getPosition(wallets.size, index)
            Item.MyWallet(position, wallet)
        }
    }

    private fun getLatestContacts(): List<Item.LatestContact> {
        val accounts = loadLatestRecipients()
        return accounts.mapIndexed { index, account ->
            val position = ListCell.getPosition(accounts.size, index)
            Item.LatestContact(position, account, wallet.testnet)
        }
    }

    private fun loadLatestRecipients(): List<AccountAddress> {
        val actions = api.getEvents(
            accountId = wallet.accountId,
            testnet = wallet.testnet,
            limit = 100
        )?.events?.map { it.actions }?.flatten() ?: return emptyList()

        val recipients = actions.filter { it.isOutTransfer(wallet) }.mapNotNull { it.recipient }
        return recipients.filter {
            !it.address.equalsAddress(wallet.address) && !it.isWallet && !contactsRepository.isHidden(it.address.toRawAddress(), wallet.testnet)
        }.distinctBy { it.address }.take(6)
    }

}