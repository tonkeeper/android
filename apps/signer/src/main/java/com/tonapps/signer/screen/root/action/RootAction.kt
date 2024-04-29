package com.tonapps.signer.screen.root.action

import com.tonapps.signer.deeplink.entities.ReturnResultEntity
import org.ton.api.pub.PublicKeyEd25519
import org.ton.cell.Cell

sealed class RootAction {
    data class RequestBodySign(
        val id: Long,
        val body: Cell,
        val v: String,
        val returnResult: ReturnResultEntity
    ): RootAction()

    data class ResponseBoc(val boc: String): RootAction()

    data class ResponseKey(val publicKey: PublicKeyEd25519, val name: String): RootAction()
}