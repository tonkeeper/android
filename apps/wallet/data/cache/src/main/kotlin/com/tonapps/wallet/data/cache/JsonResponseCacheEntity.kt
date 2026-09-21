package com.tonapps.wallet.data.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "json_response_cache",
    primaryKeys = ["scope", "wallet_id", "cache_key"],
    indices = [
        Index(
            value = ["wallet_id"],
            name = "idx_json_response_cache_wallet_id",
        ),
    ],
)
data class JsonResponseCacheEntity(
    @ColumnInfo("scope")
    val scope: String,
    @ColumnInfo("wallet_id")
    val walletId: String,
    @ColumnInfo("cache_key")
    val cacheKey: String,
    @ColumnInfo("json")
    val json: String,
    @ColumnInfo("updated_at")
    val updatedAt: Long,
)

const val GLOBAL_JSON_CACHE_WALLET_ID = ""
