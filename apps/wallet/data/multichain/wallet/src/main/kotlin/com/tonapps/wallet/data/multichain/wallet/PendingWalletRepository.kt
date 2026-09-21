@file:Suppress("ArrayInDataClass")
package com.tonapps.wallet.data.multichain.wallet

import com.tonapps.chainkit.core.secure.mnemonic.Mnemonic
import com.tonapps.extensions.generateUuid

sealed interface PendingAction {
    val mnemonic: Mnemonic

    data class Create(
        val name: String,
        override val mnemonic: Mnemonic,
    ) : PendingAction

    data class Verify(
        val walletId: String,
        override val mnemonic: Mnemonic
    ) : PendingAction
}

class PendingWalletRepository {

    private val requests = mutableMapOf<String, PendingAction>()

    fun putPendingCreation(name: String, mnemonic: Mnemonic): String {
        val id = generateUuid()
        requests[id] = PendingAction.Create(name, mnemonic)
        return id
    }

    fun putPendingVerification(walletId: String, mnemonic: Mnemonic): String {
        val id = generateUuid()
        requests[id] = PendingAction.Verify(walletId, mnemonic)
        return id
    }

    fun getPending(id: String): PendingAction? {
        return requests[id]
    }

    fun clear(id: String) {
        requests.remove(id)
            ?.mnemonic
            ?.close()
    }

    fun clearAll() {
        requests.forEach { it.value.mnemonic.close() }
        requests.clear()
    }
}
