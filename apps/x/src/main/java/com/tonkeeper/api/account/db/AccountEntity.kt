package com.tonkeeper.api.account.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tonkeeper.api.toJSON
import io.tonapi.models.Account

@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey val accountId: String,
    val data: String
)