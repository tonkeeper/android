package com.tonkeeper.extensions

import com.tonkeeper.App
import com.tonkeeper.api.Tonapi
import io.tonapi.models.MessageConsequences
import org.ton.api.pk.PrivateKey
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer
import ton.extensions.base64
import ton.wallet.Wallet


fun Wallet.isRecoveryPhraseBackup(): Boolean {
    return App.settings.isRecoveryPhraseBackup(accountId)
}

fun Wallet.setRecoveryPhraseBackup(isBackup: Boolean) {
    App.settings.setRecoveryPhraseBackup(accountId, isBackup)
}

suspend fun Wallet.buildBody(
    vararg gifts: WalletTransfer
): Pair<Cell, Int> {
    val seqno = Tonapi.getAccountSeqnoOrZero(accountId, testnet)
    val cell = contract.createTransferUnsignedBody(seqno = seqno, gifts = gifts)
    return Pair(cell, seqno)
}

suspend fun Wallet.sign(
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

suspend fun Wallet.emulate(
    boc: String
): MessageConsequences {
    return Tonapi.emulate(boc, testnet)
}

suspend fun Wallet.emulate(
    cell: Cell
) = emulate(cell.base64())

suspend fun Wallet.emulate(
    vararg gifts: WalletTransfer
): MessageConsequences {
    val cell = sign(gifts = gifts)
    return emulate(cell)
}

suspend fun Wallet.emulate(
    gifts: List<WalletTransfer>
): MessageConsequences {
    val cell = sign(gifts = gifts.toTypedArray())
    return emulate(cell)
}

suspend fun Wallet.sendToBlockchain(
    boc: String
): Boolean {
    return Tonapi.sendToBlockchain(boc, testnet)
}

suspend fun Wallet.sendToBlockchain(
    cell: Cell
) = sendToBlockchain(cell.base64())

suspend fun Wallet.sendToBlockchain(
    privateKey: PrivateKeyEd25519,
    vararg gifts: WalletTransfer
): Cell? {
    val cell = sign(privateKey = privateKey, gifts = gifts)
    if (sendToBlockchain(cell)) {
        return cell
    }
    return null
}

suspend fun Wallet.sendToBlockchain(
    privateKey: PrivateKeyEd25519,
    gifts: List<WalletTransfer>
): Cell? {
    return sendToBlockchain(privateKey = privateKey, gifts = gifts.toTypedArray())
}

suspend fun Wallet.getSeqno(): Int {
    return try {
        Tonapi.getAccountSeqnoOrZero(accountId, testnet)
    } catch (e: Throwable) {
        0
    }
}
