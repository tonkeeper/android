package com.tonapps.wallet.data.contacts

import android.content.Context
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.wallet.data.contacts.entities.ContactEntity
import com.tonapps.wallet.data.contacts.source.DatabaseSource
import com.tonapps.wallet.data.rn.RNLegacy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsRepository(
    context: Context,
    scope: CoroutineScope,
    rnLegacy: RNLegacy,
) {

    private val database = DatabaseSource(context)

    private val _contactsFlow = MutableStateFlow<List<ContactEntity>?>(null)
    val contactsFlow = _contactsFlow.stateIn(scope, SharingStarted.Lazily, null).filterNotNull()

    private val _hiddenFlow = MutableEffectFlow<Unit>()
    val hiddenFlow = _hiddenFlow.asSharedFlow()

    init {
        scope.launch {
            val contacts = database.getContacts()
            if (contacts.isEmpty()) {
                val legacyContacts = rnLegacy.getFavorites()
                for (legacyContact in legacyContacts) {
                    database.addContact(legacyContact.name, legacyContact.address, false)
                }
                val legacyHiddenAddresses = rnLegacy.getHiddenAddresses()
                for (legacyHiddenAddress in legacyHiddenAddresses) {
                    database.setHidden(legacyHiddenAddress, testnet = false, hidden = true)
                }
                _contactsFlow.value = database.getContacts()
            } else {
                _contactsFlow.value = contacts
            }

            _hiddenFlow.tryEmit(Unit)
        }
    }

    fun isHidden(accountId: String, testnet: Boolean) = database.isHidden(accountId, testnet)

    fun hide(accountId: String, testnet: Boolean) {
        database.setHidden(accountId, testnet, true)
        _hiddenFlow.tryEmit(Unit)
    }

    suspend fun add(name: String, address: String, testnet: Boolean): ContactEntity = withContext(Dispatchers.IO) {
        val contact = database.addContact(name, address, testnet)
        _contactsFlow.value = _contactsFlow.value.orEmpty().toMutableList().apply {
            add(contact)
        }
        contact
    }

    suspend fun deleteContact(id: Long) = withContext(Dispatchers.IO) {
        database.deleteContact(id)
        _contactsFlow.value = _contactsFlow.value.orEmpty().toMutableList().apply {
            removeAll { it.id == id }
        }
    }

    suspend fun editContact(id: Long, name: String) = withContext(Dispatchers.IO) {
        database.editContact(id, name)
        val oldContacts = _contactsFlow.value.orEmpty().toMutableList()
        val index = oldContacts.indexOfFirst { it.id == id }
        if (index == -1) return@withContext
        oldContacts[index] = oldContacts[index].copy(name = name)
        _contactsFlow.value = oldContacts
    }
}