package com.tonapps.wallet.data.events.tx.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tonapps.wallet.api.entity.value.Blockchain
import com.tonapps.wallet.api.entity.value.BlockchainAddress
import com.tonapps.wallet.api.entity.value.Timestamp
import com.tonapps.wallet.data.events.tx.model.TxEvent

@Entity(
    tableName = "tx_records",
    indices = [
        Index(
            value = ["account", "blockchain", "timestamp"],
            name = "idx_account_blockchain_timestamp"
        ),
        Index(
            value = ["timestamp"],
            name = "idx_timestamp",
            orders = [Index.Order.DESC]
        )
    ]
)
data class TxRecordEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "event") val event: TxEvent,
    @ColumnInfo(name = "account") val account: BlockchainAddress,
    @ColumnInfo(name = "timestamp") val timestamp: Timestamp,
    @ColumnInfo(name = "blockchain") val blockchain: Blockchain
) {

    constructor(account: BlockchainAddress, event: TxEvent) : this(
        id = event.id,
        event = event,
        account = account,
        timestamp = event.timestamp,
        blockchain = event.blockchain
    )
}