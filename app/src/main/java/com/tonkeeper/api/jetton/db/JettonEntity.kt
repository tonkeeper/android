package com.tonkeeper.api.jetton.cache

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tonkeeper.api.symbol
import com.tonkeeper.api.toJSON
import io.tonapi.models.JettonBalance

@Entity(tableName = "jetton")
data class JettonEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val data: String
) {

    companion object {

        private fun createId(accountId: String, symbol: String): String {
            return "$accountId-$symbol"
        }

        fun map(accountId: String, list: List<JettonBalance>): List<JettonEntity> {
            return list.map { JettonEntity(
                id = createId(accountId, it.symbol),
                accountId = accountId,
                data = toJSON(it)
            ) }
        }
    }

    constructor(
        accountId: String,
        jetton: JettonBalance
    ) : this(
        id = createId(accountId, jetton.symbol),
        accountId = accountId,
        data = toJSON(jetton)
    )
}