package com.tonapps.wallet.data.multichain.wallet

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tonapps.blockchain.model.legacy.WalletColor
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.extensions.generateUuid

@Entity(
    tableName = "wallet",
    indices = [
        Index(
            value = ["credential_id"],
            name = "idx_wallet_credential_id",
            unique = true,
        )
    ]
)
class McWalletEntity(
    @ColumnInfo("credential_id")
    val credentialId: String,

    @ColumnInfo("name")
    val name: String,

    @ColumnInfo("emoji")
    val emoji: String = "",

    @PrimaryKey
    @ColumnInfo("id")
    val id: String,

    @ColumnInfo("color")
    val color: Int = WalletColor.all.first(),

    @ColumnInfo("type")
    val type: McWalletType,

    // The TON wallet contract version the user picked as primary (W5 by default). Both TON variants
    // are still created and synced; this only selects which one getTonAccount resolves to.
    @ColumnInfo("ton_wallet_type")
    val tonWalletType: Address.Type = Address.Type.TonV5R1,

    @ColumnInfo("backup_type")
    val backupType: McBackupSource? = null,

    @ColumnInfo("backup_time")
    val backupTime: Long? = null,
)


enum class McWalletType {
    Multicoin;

    val legacy: WalletType get() = WalletType.Multichain
}

enum class McBackupSource {
    LOCAL,
}
