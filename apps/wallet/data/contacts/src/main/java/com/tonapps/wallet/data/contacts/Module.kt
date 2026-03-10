package com.tonapps.wallet.data.contacts

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val contactsModule = module {
    singleOf(::ContactsRepository)
}