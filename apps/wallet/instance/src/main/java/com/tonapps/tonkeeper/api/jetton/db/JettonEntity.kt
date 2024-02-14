package com.tonapps.tonkeeper.api.jetton.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tonapps.tonkeeper.api.getAddress
import com.tonapps.tonkeeper.api.symbol
import com.tonapps.tonkeeper.api.toJSON
import io.tonapi.models.JettonBalance

@Entity(
    tableName = "jetton",
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["jettonAddress"]),
    ]
)
data class JettonEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val jettonAddress: String,
    val data: String
) {

    companion object {

        private fun createId(accountId: String, symbol: String): String {
            return "$accountId-$symbol"
        }

        fun map(accountId: String, testnet: Boolean,list: List<JettonBalance>): List<JettonEntity> {
            return list.map { JettonEntity(
                testnet = testnet,
                accountId = accountId,
                jetton = it
            ) }
        }
    }

    constructor(
        accountId: String,
        testnet: Boolean,
        jetton: JettonBalance
    ) : this(
        id = createId(accountId, jetton.symbol),
        accountId = accountId,
        jettonAddress = jetton.getAddress(testnet),
        data = toJSON(jetton)
    )
}