package com.tonapps.tonkeeper.extensions

import com.tonapps.tonkeeper.api.ApiHelper
import com.tonapps.wallet.data.account.entities.WalletLabel
import io.tonapi.models.MessageConsequences
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer
import ton.extensions.base64
import com.tonapps.wallet.data.account.legacy.WalletLegacy


fun WalletLegacy.isRecoveryPhraseBackup(): Boolean {
    // return com.tonapps.tonkeeper.App.settings.isRecoveryPhraseBackup(accountId)
    return false
}

fun WalletLegacy.setRecoveryPhraseBackup(isBackup: Boolean) {
    // com.tonapps.tonkeeper.App.settings.setRecoveryPhraseBackup(accountId, isBackup)
}

val WalletLegacy.label: WalletLabel
    get() = WalletLabel(
        name = name,
        emoji = emoji,
        color = color,
    )

suspend fun WalletLegacy.buildBody(
    vararg gifts: WalletTransfer
): Pair<Cell, Int> {
    val seqno = ApiHelper.getAccountSeqnoOrZero(accountId, testnet)
    val cell = contract.createTransferUnsignedBody(seqno = seqno, gifts = gifts)
    return Pair(cell, seqno)
}

suspend fun WalletLegacy.sign(
    privateKey: PrivateKeyEd25519 = PrivateKeyEd25519(),
    vararg gifts: WalletTransfer
): Cell {
    val (unsignedBody, seqno) = buildBody(*gifts)
    return sign(
        seqno = seqno,
        privateKey = privateKey,
        unsignedBody = unsignedBody,
    )
}

suspend fun WalletLegacy.emulate(
    boc: String
): MessageConsequences {
    return ApiHelper.emulate(boc, testnet)
}

suspend fun WalletLegacy.emulate(
    cell: Cell
) = emulate(cell.base64())

suspend fun WalletLegacy.emulate(
    vararg gifts: WalletTransfer
): MessageConsequences {
    val cell = sign(gifts = gifts)
    return emulate(cell)
}

suspend fun WalletLegacy.emulate(
    gifts: List<WalletTransfer>
): MessageConsequences {
    val cell = sign(gifts = gifts.toTypedArray())
    return emulate(cell)
}

suspend fun WalletLegacy.sendToBlockchain(
    boc: String
): Boolean {
    return ApiHelper.sendToBlockchain(boc, testnet)
}

suspend fun WalletLegacy.sendToBlockchain(
    cell: Cell
) = sendToBlockchain(cell.base64())

suspend fun WalletLegacy.sendToBlockchain(
    privateKey: PrivateKeyEd25519,
    vararg gifts: WalletTransfer
): Cell? {
    val cell = sign(privateKey = privateKey, gifts = gifts)
    if (sendToBlockchain(cell)) {
        return cell
    }
    return null
}

suspend fun WalletLegacy.sendToBlockchain(
    privateKey: PrivateKeyEd25519,
    gifts: List<WalletTransfer>
): Cell? {
    return sendToBlockchain(privateKey = privateKey, gifts = gifts.toTypedArray())
}

suspend fun WalletLegacy.getSeqno(): Int {
    return try {
        ApiHelper.getAccountSeqnoOrZero(accountId, testnet)
    } catch (e: Throwable) {
        0
    }
}
