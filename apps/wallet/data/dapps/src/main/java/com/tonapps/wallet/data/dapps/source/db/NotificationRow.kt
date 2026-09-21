package com.tonapps.wallet.data.dapps.source.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["app_url"]),
        Index(value = ["account_id"]),
    ]
)
data class NotificationRow(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo("id")
    val id: Long = 0,

    @ColumnInfo("app_url")
    val appUrl: String?,

    @ColumnInfo("account_id")
    val accountId: String?,

    @ColumnInfo("body")
    val body: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NotificationRow) return false
        return id == other.id &&
            appUrl == other.appUrl &&
            accountId == other.accountId &&
            body.contentEqualsOrBothNull(other.body)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (appUrl?.hashCode() ?: 0)
        result = 31 * result + (accountId?.hashCode() ?: 0)
        result = 31 * result + (body?.contentHashCode() ?: 0)
        return result
    }

    private fun ByteArray?.contentEqualsOrBothNull(other: ByteArray?): Boolean {
        if (this == null && other == null) return true
        if (this == null || other == null) return false
        return contentEquals(other)
    }
}
