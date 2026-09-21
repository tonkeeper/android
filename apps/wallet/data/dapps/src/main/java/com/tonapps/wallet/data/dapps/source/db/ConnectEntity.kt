package com.tonapps.wallet.data.dapps.source.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tonapps.wallet.data.dapps.entities.DappProvider
import com.tonapps.wc.models.WcConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(
    tableName = "connect",
    indices = [
        Index(value = ["topic"], unique = true),
        Index(value = ["account_id", "mode"]),
        Index(value = ["type", "app_url"]),
        Index(value = ["provider", "app_url"]),
    ]
)
data class ConnectEntity(
    @PrimaryKey
    @ColumnInfo("id")
    val id: String,

    @ColumnInfo("provider", defaultValue = "'ton_connect'")
    val provider: DappProvider,

    @ColumnInfo("wallet_id")
    val walletId: String?,

    @ColumnInfo("app_url")
    val appUrl: String?,

    @ColumnInfo("timestamp")
    val createdAt: Long?,

    @ColumnInfo("account_id")
    val accountId: String?,

    @ColumnInfo("mode", defaultValue = "1")
    val mode: Int?,

    @ColumnInfo("type")
    val type: Int?,

    @ColumnInfo("topic")
    val topic: String?,

    @ColumnInfo("source")
    val source: WcConnection?,

    @ColumnInfo("key_pair")
    val keyPair: ByteArray?,
) {

    // `createdAt` is stored in seconds; format on first access only.
    @get:Ignore
    val createdAtFormatted: String? by lazy(LazyThreadSafetyMode.NONE) {
        createdAt?.let { seconds ->
            SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(seconds * 1000L))
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConnectEntity) return false
        return id == other.id &&
            provider == other.provider &&
            walletId == other.walletId &&
            appUrl == other.appUrl &&
            createdAt == other.createdAt &&
            accountId == other.accountId &&
            mode == other.mode &&
            type == other.type &&
            topic == other.topic &&
            source == other.source &&
            keyPair.contentEqualsOrBothNull(other.keyPair)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + provider.hashCode()
        result = 31 * result + (walletId?.hashCode() ?: 0)
        result = 31 * result + (appUrl?.hashCode() ?: 0)
        result = 31 * result + (createdAt?.hashCode() ?: 0)
        result = 31 * result + (accountId?.hashCode() ?: 0)
        result = 31 * result + (mode ?: 0)
        result = 31 * result + (type ?: 0)
        result = 31 * result + (topic?.hashCode() ?: 0)
        result = 31 * result + (source?.hashCode() ?: 0)
        result = 31 * result + (keyPair?.contentHashCode() ?: 0)
        return result
    }

    private fun ByteArray?.contentEqualsOrBothNull(other: ByteArray?): Boolean {
        if (this == null && other == null) return true
        if (this == null || other == null) return false
        return contentEquals(other)
    }
}
