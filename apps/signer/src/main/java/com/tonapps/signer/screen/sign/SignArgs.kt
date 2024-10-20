package com.tonapps.signer.screen.sign

import android.os.Bundle
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.blockchain.ton.extensions.parseCell
import com.tonapps.signer.Key
import com.tonapps.signer.deeplink.entities.ReturnResultEntity
import com.tonapps.signer.extensions.getEnum
import com.tonapps.signer.extensions.getObject
import com.tonapps.signer.extensions.putEnum
import org.ton.cell.Cell

data class SignArgs(private val args: Bundle) {

    companion object {

        fun bundle(
            id: Long,
            body: Cell,
            v: String,
            returnResult: ReturnResultEntity,
            seqno: Int,
            network: TonNetwork,
        ) = Bundle().apply {
            putLong(Key.ID, id)
            putString(Key.V, v)
            putString(Key.BODY, body.hex())
            putParcelable(Key.RETURN, returnResult)
            putInt(Key.SEQNO, seqno)
            putEnum(Key.NETWORK, network)
        }
    }

    val id = args.getLong(Key.ID)
    val body: Cell = args.getString(Key.BODY)!!.parseCell()
    val v: String = args.getString(Key.V)!!
    val returnResult = args.getObject<ReturnResultEntity>(Key.RETURN)
    val seqno = args.getInt(Key.SEQNO)
    val network = args.getEnum(Key.NETWORK, TonNetwork.MAINNET)

    val bodyHex: String by lazy { body.hex() }
}
