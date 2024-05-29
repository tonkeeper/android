package com.tonapps.wallet.data.account.entities

import org.ton.cell.Cell

data class MessageBodyEntity(
    val seqno: Int,
    val body: Cell,
    val validUntil: Long,
)