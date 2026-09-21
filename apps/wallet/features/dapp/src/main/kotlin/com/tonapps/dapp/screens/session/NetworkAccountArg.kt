package com.tonapps.dapp.screens.session

import android.os.Parcelable
import com.tonapps.wallet.data.multichain.account.AccountEntity
import kotlinx.parcelize.Parcelize

@Parcelize
data class NetworkAccountArg(
    val id: String,
    val walletId: String,
    val network: String,
    val mode: String,
    val displayAddress: String,
    val publicKey: String,
    val segwitPublicKey: String,
) : Parcelable {
    fun toAccountEntity(): AccountEntity = AccountEntity(
        walletId = walletId,
        network = network,
        mode = mode,
        displayAddress = displayAddress,
        publicKey = publicKey,
        segwitPublicKey = segwitPublicKey,
        id = id,
    )

    companion object {
        fun from(account: AccountEntity) = NetworkAccountArg(
            id = account.id,
            walletId = account.walletId,
            network = account.network,
            mode = account.mode,
            displayAddress = account.displayAddress,
            publicKey = account.publicKey,
            segwitPublicKey = account.segwitPublicKey,
        )
    }
}
