package com.tonapps.tonkeeper.ui.screen.external.qr.keystone.sign

import android.os.Bundle
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.ur.registry.pathcomponent.IndexPathComponent
import com.tonapps.wallet.data.account.entities.WalletEntity
import uikit.base.BaseArgs

data class KeystoneSignArgs(
    val unsignedBody: String,
    val isTransaction: Boolean,
    val address: String,
    val keystone: WalletEntity.Keystone,
): BaseArgs() {

    private companion object {
        private const val ARG_UNSIGNED_BODY = "unsignedBody"
        private const val ARG_IS_TRANSACTION = "isTransaction"
        private const val ARG_ADDRESS = "address"
        private const val ARG_KEYSTONE = "keystone"
    }

    val path: List<IndexPathComponent>
        get() = keystone.path.split("/").drop(1).map {
            val hardened = it.endsWith("'")
            val index = if (hardened) it.dropLast(1).toInt() else it.toInt()
            IndexPathComponent(index, hardened)
        }

    constructor(bundle: Bundle) : this(
        unsignedBody = bundle.getString(ARG_UNSIGNED_BODY)!!,
        isTransaction = bundle.getBoolean(ARG_IS_TRANSACTION),
        address = bundle.getString(ARG_ADDRESS)!!,
        keystone = bundle.getParcelableCompat(ARG_KEYSTONE)!!
    )

    override fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putString(ARG_UNSIGNED_BODY, unsignedBody)
        bundle.putBoolean(ARG_IS_TRANSACTION, isTransaction)
        bundle.putString(ARG_ADDRESS, address)
        bundle.putParcelable(ARG_KEYSTONE, keystone)
        return bundle
    }

}
