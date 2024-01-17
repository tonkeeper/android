package com.tonkeeper.api.jetton.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tonkeeper.api.address
import com.tonkeeper.api.symbol
import com.tonkeeper.api.toJSON
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

        fun map(accountId: String, list: List<JettonBalance>): List<JettonEntity> {
            return list.map { JettonEntity(
                accountId = accountId,
                jetton = it
            ) }
        }
    }

    constructor(
        accountId: String,
        jetton: JettonBalance
    ) : this(
        id = createId(accountId, jetton.symbol),
        accountId = accountId,
        jettonAddress = jetton.address,
        data = toJSON(jetton)
    )
}