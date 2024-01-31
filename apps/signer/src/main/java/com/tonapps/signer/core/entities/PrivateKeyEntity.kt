package com.tonapps.signer.core.entities

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.bitstring.BitString
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import java.security.Signature
import javax.security.auth.Destroyable

data class PrivateKeyEntity(
    val id: Long,
    val privateKey: PrivateKeyEd25519,
    val mnemonic: List<String>
) {

    constructor() : this(
        0L,
        PrivateKeyEd25519(),
        emptyList()
    )

    suspend fun sign(body: Cell): Cell = withContext(Dispatchers.IO) {
        val signature = BitString(privateKey.sign(body.hash()))

        CellBuilder.createCell {
            storeBits(signature)
            storeBits(body.bits)
            storeRefs(body.refs)
        }
    }
}