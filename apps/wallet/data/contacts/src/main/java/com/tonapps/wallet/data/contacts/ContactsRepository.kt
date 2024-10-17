package com.tonapps.wallet.data.contacts

import android.content.Context
import com.tonapps.wallet.data.contacts.entities.ContactEntity
import com.tonapps.wallet.data.contacts.source.DatabaseSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsRepository(context: Context, scope: CoroutineScope) {

    private val database = DatabaseSource(context)

    private val _contactsFlow = MutableStateFlow<List<ContactEntity>?>(null)
    val contactsFlow = _contactsFlow.stateIn(scope, SharingStarted.Lazily, null).filterNotNull()

    init {
        scope.launch {
            _contactsFlow.value = database.getContacts()
        }
    }

    fun isHidden(accountId: String, testnet: Boolean) = database.isHidden(accountId, testnet)

    fun hide(accountId: String, testnet: Boolean) = database.setHidden(accountId, testnet, true)

    suspend fun add(name: String, address: String): ContactEntity = withContext(Dispatchers.IO) {
        val contact = database.addContact(name, address)
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