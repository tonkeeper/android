package com.tonapps.wallet.data.events.tx

import com.tonapps.wallet.api.entity.value.BlockchainAddress
import com.tonapps.wallet.api.entity.value.Timestamp

data class TxFetchQuery(
    val tonAddress: BlockchainAddress,
    val tronAddress: BlockchainAddress?,
    val tonProofToken: String?,
    val beforeTimestamp: Timestamp?,
    val afterTimestamp: Timestamp?,
    val limit: Int
)