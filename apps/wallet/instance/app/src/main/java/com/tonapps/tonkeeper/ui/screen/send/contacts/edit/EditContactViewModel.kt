package com.tonapps.tonkeeper.ui.screen.send.contacts.edit

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.contacts.ContactsRepository
import com.tonapps.wallet.data.contacts.entities.ContactEntity
import kotlinx.coroutines.launch

class EditContactViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val contact: ContactEntity,
    private val api: API,
    private val contactsRepository: ContactsRepository
): BaseWalletVM(app) {


    fun delete(callback: () -> Unit) {
        viewModelScope.launch {
            contactsRepository.deleteContact(contact.id)
            callback()
        }

    }

    fun save(name: String, callback: () -> Unit) {
        viewModelScope.launch {
            contactsRepository.editContact(contact.id, name)
            callback()
        }
    }
}