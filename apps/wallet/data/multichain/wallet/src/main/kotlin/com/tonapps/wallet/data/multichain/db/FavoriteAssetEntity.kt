package com.tonapps.wallet.data.multichain.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "favorite_asset",
    primaryKeys = ["wallet_id", "asset_id"],
    indices = [
        Index(
            value = ["wallet_id", "asset_id"],
            name = "idx_favorite_asset_wallet_id_asset_id",
        ),
    ],
)
data class FavoriteAssetEntity(
    @ColumnInfo("wallet_id")
    val walletId: String,

    @ColumnInfo("asset_id")
    val assetId: String,

    @ColumnInfo("created_at")
    val createdAt: Long,
)
