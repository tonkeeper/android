package com.tonapps.deposit.multicoin.domain

import java.util.concurrent.ConcurrentHashMap

// Saved cards the customer removed on MoonPay in this run of the app, kept out of every load from the moment the
// removal starts: a list asked for while the removal still runs may list the card, as on iOS (`deletedCardIDs`).
class MoonPayRemovedCards {

    private val ids: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun add(id: String) {
        ids += id
    }

    fun remove(id: String) {
        ids -= id
    }

    operator fun contains(id: String): Boolean = id in ids
}
