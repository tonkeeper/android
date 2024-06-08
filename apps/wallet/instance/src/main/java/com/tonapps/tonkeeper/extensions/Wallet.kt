package com.tonapps.tonkeeper.extensions

import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.wallet.data.account.entities.WalletLabel
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer
import com.tonapps.wallet.data.account.legacy.WalletLegacy

val WalletLegacy.label: WalletLabel
    get() = WalletLabel(
        accountName = name,
        emoji = emoji,
        color = color,
    )

fun WalletLegacy.buildBody(
    seqno: Int,
    validUntil: Long,
    vararg gifts: WalletTransfer
): Cell {
    return contract.createTransferUnsignedBody(seqno = seqno, gifts = gifts, validUntil = validUntil)
}

fun WalletLegacy.sign(
    seqno: Int,
    validUntil: Long,
    privateKey: PrivateKeyEd25519 = EmptyPrivateKeyEd25519,
    vararg gifts: WalletTransfer
): Cell {
    val unsignedBody = buildBody(seqno, validUntil, *gifts)
    return sign(
        privateKey = privateKey,
        seqno = seqno,
        unsignedBody = unsignedBody,
    )
}
