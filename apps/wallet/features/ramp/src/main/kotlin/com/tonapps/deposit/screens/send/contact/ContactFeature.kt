package com.tonapps.deposit.screens.send.contact

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.extensions.short8
import com.tonapps.legacy.enteties.WalletExtendedEntity
import com.tonapps.log.L
import com.tonapps.mvi.MviFeature
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviState
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.props.MviProperty
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.contacts.ContactsRepository
import com.tonapps.wallet.data.contacts.entities.ContactEntity
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ContactAction : MviAction {
    data object Init : ContactAction
    data class HideContact(val address: String) : ContactAction
    data class DeleteContact(val contact: ContactEntity) : ContactAction
}

sealed interface ContactState : MviState {
    data object Loading : ContactState
    data class Data(val items: List<ContactItem>) : ContactState
}

class ContactViewState(
    val global: MviProperty<ContactState>,
) : MviViewState

sealed interface ContactEvent {
    data class ContactSelected(val result: SendContactResult) : ContactEvent
}

class ContactFeature(
    private val wallet: WalletEntity,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val contactsRepository: ContactsRepository,
    private val eventsRepository: EventsRepository,
    private val batteryRepository: BatteryRepository,
) : MviFeature<ContactAction, ContactState, ContactViewState>(
    initState = ContactState.Loading,
    initAction = ContactAction.Init,
) {

    private val relay = MviRelay<ContactEvent>()
    val events: Flow<ContactEvent> = relay.events

    override fun createViewState(): ContactViewState {
        return buildViewState {
            ContactViewState(mviProperty { it })
        }
    }

    override suspend fun executeAction(action: ContactAction) {
        when (action) {
            is ContactAction.Init -> loadContacts()
            is ContactAction.HideContact -> hideContact(action.address)
            is ContactAction.DeleteContact -> deleteContact(action.contact)
        }
    }

    fun selectContact(item: ContactItem.AddressItem) {
        val type = when (item) {
            is ContactItem.MyWallet -> SendContactResult.MY_WALLET_TYPE
            is ContactItem.LatestContact -> SendContactResult.CONTACT_TYPE
            is ContactItem.SavedContact -> SendContactResult.CONTACT_TYPE
        }
        relay.emit(ContactEvent.ContactSelected(SendContactResult(type, item.address)))
    }

    private suspend fun loadContacts() = withContext(Dispatchers.IO) {
        try {
            val myWallets = getMyWallets()

            val savedContactsFlow = contactsRepository.contactsFlow
                .map { contacts ->
                    contacts.filter { it.testnet == wallet.testnet }
                        .mapIndexed { index, contact ->
                            val position = ListCell.getPosition(contacts.size, index)
                            ContactItem.SavedContact(position, contact, wallet.testnet)
                        }
                }

            val latestContactsFlow = flow<List<ContactItem.LatestContact>> {
                emit(emptyList())
                emitAll(getLatestContactsFlow())
            }

            stateScope.launch {
                combine(savedContactsFlow, latestContactsFlow) { saved, latest ->
                    buildItems(myWallets, saved, latest)
                }.collect { items ->
                    setState { ContactState.Data(items) }
                }
            }
        } catch (e: Throwable) {
            L.e(e)
            setState { ContactState.Data(emptyList()) }
        }
    }

    private fun buildItems(
        myWallets: List<ContactItem.MyWallet>,
        savedContacts: List<ContactItem.SavedContact>,
        latestContacts: List<ContactItem.LatestContact>,
    ): List<ContactItem> {
        val items = mutableListOf<ContactItem>()
        if (myWallets.isNotEmpty()) {
            items.addAll(myWallets)
            items.add(ContactItem.Space)
        }
        if (savedContacts.isNotEmpty()) {
            items.addAll(savedContacts)
            items.add(ContactItem.Space)
        }
        if (latestContacts.isNotEmpty()) {
            items.addAll(latestContacts)
            items.add(ContactItem.Space)
        }
        return items
    }

    private fun hideContact(address: String) {
        contactsRepository.hide(address.toRawAddress(), wallet.network)
    }

    private suspend fun deleteContact(contact: ContactEntity) {
        withContext(Dispatchers.IO) {
            contactsRepository.deleteContact(contact.id)
        }
    }

    private suspend fun getMyWallets(): List<ContactItem.MyWallet> {
        val wallets = accountRepository.getWallets()
            .filter { it.type != WalletType.Watch && it.network == wallet.network && it.address != wallet.address }
            .map { WalletExtendedEntity(it, settingsRepository.getWalletPrefs(it.id)) }
            .sortedBy { it.index }
            .map { it.raw }

        return wallets.mapIndexed { index, w ->
            val position = ListCell.getPosition(wallets.size, index)
            ContactItem.MyWallet(position, w)
        }
    }

    private val tronEnabled: Boolean
        get() = settingsRepository.getTronUsdtEnabled(wallet.id)

    private val tronLatestTransactionsFlow = flow {
        if (!tronEnabled) {
            emit(emptyList())
            return@flow
        }
        val tronAddress = if (wallet.hasPrivateKey && !wallet.testnet && tronEnabled) {
            accountRepository.getTronAddress(wallet.id)
        } else null
        val tonProofToken = accountRepository.requestTonProofToken(wallet)

        if (tronAddress != null && tonProofToken != null) {
            emit(eventsRepository.tronLatestSentTransactions(tronAddress, tonProofToken))
        } else {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    private val latestTronContactsFlow = combine(
        contactsRepository.hiddenFlow,
        tronLatestTransactionsFlow,
    ) { _, events ->
        events.filter {
            !contactsRepository.isHidden(it.to, wallet.network)
        }.mapIndexed { index, event ->
            val position = ListCell.getPosition(events.size, index)
            ContactItem.LatestContact(position, event.to, event.to.short8, event.timestamp.value)
        }
    }

    private fun getLatestContactsFlow() = combine(
        contactsRepository.hiddenFlow,
        eventsRepository.latestRecipientsFlow(
            accountId = wallet.accountId,
            network = wallet.network,
        ),
        latestTronContactsFlow,
    ) { _, recipients, tronContacts ->
        val gasProxyAddresses = batteryRepository.getConfig(wallet.network).gasProxy
        val tonContacts = recipients.filter {
            !contactsRepository.isHidden(it.account.address.toRawAddress(), wallet.network)
        }.mapIndexed { index, recipient ->
            val position = ListCell.getPosition(recipients.size, index)
            ContactItem.LatestContact(
                position = position,
                address = recipient.account.address.toUserFriendly(testnet = wallet.testnet),
                name = recipient.account.name
                    ?: recipient.account.address.toUserFriendly(testnet = wallet.testnet).short8,
                timestamp = recipient.timestamp,
            )
        }.filter {
            it.address !in gasProxyAddresses
        }

        val contacts = (tonContacts + tronContacts).sortedByDescending {
            it.timestamp
        }.take(6)

        contacts.mapIndexed { index, item ->
            item.copy(position = ListCell.getPosition(contacts.size, index))
        }
    }
}
