package com.tonapps.tonkeeper.ui.screen.send.contacts.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.extensions.filterList
import com.tonapps.tonkeeper.RemoteConfig
import com.tonapps.tonkeeper.core.entities.WalletExtendedEntity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.internal.filterList

class SendContactsViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val contactsRepository: ContactsRepository,
    private val eventsRepository: EventsRepository,
    private val remoteConfig: RemoteConfig,
) : BaseWalletVM(app) {

    private val _myWalletsFlow = MutableStateFlow<List<Item.MyWallet>>(emptyList())
    private val myWalletsFlow = _myWalletsFlow.asStateFlow()

    private val latestContactsFlow = flow {
        emit(listOf(Item.Loading))
        emitAll(getLatestContactsFlow())
    }

    private val savedContactsFlow =
        contactsRepository.contactsFlow.filterList { it.testnet == wallet.testnet }.map {
            it.mapIndexed { index, contact ->
                val position = ListCell.getPosition(it.size, index)
                Item.SavedContact(position, contact, wallet.testnet)
            }
        }

    val uiItemsFlow = combine(
        myWalletsFlow,
        savedContactsFlow,
        latestContactsFlow,
    ) { myWallets, savedContacts, latestContacts ->
        val uiItems = mutableListOf<Item>()
        if (myWallets.isNotEmpty()) {
            uiItems.addAll(myWallets)
            uiItems.add(Item.Space)
        }

        if (savedContacts.isNotEmpty()) {
            uiItems.addAll(savedContacts)
            uiItems.add(Item.Space)
        }

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
    }

    fun hideContact(address: String) {
        contactsRepository.hide(address.toRawAddress(), wallet.testnet)
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
            WalletExtendedEntity(it, settingsRepository.getWalletPrefs(it.id))
        }.sortedBy { it.index }.map { it.raw }.toList()

        return wallets.mapIndexed { index, wallet ->
            val position = ListCell.getPosition(wallets.size, index)
            Item.MyWallet(position, wallet)
        }
    }

    private val tronLatestTransactionsFlow = flow {
        val tronEnabled = settingsRepository.getTronUsdtEnabled(wallet.id)
        if (!tronEnabled) {
            emit(emptyList())
            return@flow
        }

        val tronAddress = if (wallet.hasPrivateKey && !wallet.testnet && !remoteConfig.isTronDisabled) {
            accountRepository.getTronAddress(wallet.id)
        } else null
        val tonProofToken = accountRepository.requestTonProofToken(wallet)

        if (tronAddress != null && tonProofToken != null) {
            val events = eventsRepository.tronLatestSentTransactions(tronAddress, tonProofToken)
            emit(events)
        } else {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    private val latestTronContactsFlow = combine(
        contactsRepository.hiddenFlow,
        tronLatestTransactionsFlow,
    ) { _, events ->
        events.filter {
            !contactsRepository.isHidden(it.to, wallet.testnet)
        }.mapIndexed { index, event ->
            val position = ListCell.getPosition(events.size, index)
            Item.LatestContact(position, event.to, event.timestamp)
        }
    }

    private fun getLatestContactsFlow() = combine(
        contactsRepository.hiddenFlow,
        eventsRepository.latestRecipientsFlow(
            accountId = wallet.accountId,
            testnet = wallet.testnet
        ),
        latestTronContactsFlow,
    ) { _, recipients, tronContacts ->
        val tonContacts = recipients.filter {
            !contactsRepository.isHidden(it.account.address.toRawAddress(), wallet.testnet)
        }.mapIndexed { index, recipient ->
            val position = ListCell.getPosition(recipients.size, index)
            Item.LatestContact(position, recipient.account, recipient.timestamp, wallet.testnet)
        }

        Log.d("SendContactsViewModel", "tronContacts: $tronContacts")

        val contacts = (tonContacts + tronContacts).sortedByDescending { it.timestamp }.take(6)

        contacts.mapIndexed { index, item ->
            item.copy(
                position = ListCell.getPosition(contacts.size, index)
            )
        }
    }
}