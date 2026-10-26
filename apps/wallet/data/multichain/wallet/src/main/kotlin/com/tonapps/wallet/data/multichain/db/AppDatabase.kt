package com.tonapps.wallet.data.multichain.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.tonapps.wallet.data.multichain.account.AccountDao
import kotlinx.coroutines.Dispatchers
import com.tonapps.wallet.data.multichain.account.AccountEntity
import com.tonapps.wallet.data.multichain.perps.PerpsFundingDao
import com.tonapps.wallet.data.multichain.perps.PerpsFundingEntity
import com.tonapps.wallet.data.multichain.perps.PerpsOperationDao
import com.tonapps.wallet.data.multichain.perps.PerpsOperationEntity
import com.tonapps.wallet.data.multichain.perps.PerpsOperationStepEntity
import com.tonapps.wallet.data.multichain.wallet.CredentialDao
import com.tonapps.wallet.data.multichain.wallet.CredentialEntity
import com.tonapps.wallet.data.multichain.wallet.WalletBundleDao
import com.tonapps.wallet.data.multichain.wallet.WalletDao
import com.tonapps.wallet.data.multichain.vault.VaultMetadataDao
import com.tonapps.wallet.data.multichain.vault.VaultMetadataEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity

// v1 → v2 is schema-identical (v2 only marks the session-key era: new vault_metadata rows and
// app_key credential rows, both data-level).
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) = Unit
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `perps_operation` (`operation_id` TEXT NOT NULL, `wallet_id` TEXT NOT NULL, `market_index` INTEGER NOT NULL, `side` TEXT NOT NULL, `margin_quote` TEXT NOT NULL, `leverage` INTEGER NOT NULL, `review_json` TEXT, `plan_digest` TEXT, `expected_base_size` TEXT, `status` TEXT NOT NULL, `error` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`operation_id`))")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_perps_operation_wallet_id` ON `perps_operation` (`wallet_id`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `perps_operation_step` (`operation_id` TEXT NOT NULL, `step_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `requires_step_id` TEXT, `attempt_id` TEXT NOT NULL, `api_key_index` INTEGER NOT NULL, `nonce` INTEGER, `account_index` INTEGER, `tx_type` INTEGER, `tx_hash` TEXT, `tx_info` TEXT, `client_order_index` INTEGER NOT NULL, `plan_id` TEXT, `tx_expiry` INTEGER, `order_refs` TEXT, `submitted_at` INTEGER NOT NULL, `status` TEXT NOT NULL, `error` TEXT, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`operation_id`, `step_id`))")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `perps_funding` (`operation_id` TEXT NOT NULL, `wallet_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `stage` TEXT NOT NULL, `route` TEXT NOT NULL, `beneficiary` TEXT NOT NULL, `source_asset_id` TEXT NOT NULL, `source_symbol` TEXT NOT NULL, `source_decimals` INTEGER NOT NULL, `source_amount` TEXT NOT NULL, `target_amount` TEXT NOT NULL, `quote_id` TEXT, `execution_id` TEXT, `source_tx_hash` TEXT, `l1_tx_hash` TEXT, `steps_json` TEXT NOT NULL, `link_signature` TEXT, `link_deadline` INTEGER, `registration_json` TEXT, `deposit_id` TEXT, `credited_amount` TEXT, `account_index` INTEGER, `failure_reason` TEXT, `revision` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`operation_id`))")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_perps_funding_wallet_id` ON `perps_funding` (`wallet_id`)")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `perps_operation` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'OPEN'")
        connection.execSQL("ALTER TABLE `perps_operation` ADD COLUMN `base_before` TEXT")
        connection.execSQL("ALTER TABLE `perps_operation` ADD COLUMN `margin_before` TEXT")
    }
}

@Database(
    entities = [
        AccountEntity::class,
        McWalletEntity::class,
        CredentialEntity::class,
        VaultMetadataEntity::class,
        FavoriteAssetEntity::class,
        PerpsOperationEntity::class,
        PerpsOperationStepEntity::class,
        PerpsFundingEntity::class,
    ],
    version = 5
)
internal abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun walletDao(): WalletDao
    abstract fun credentialDao(): CredentialDao
    abstract fun walletBundleDao(): WalletBundleDao
    abstract fun vaultMetadataDao(): VaultMetadataDao
    abstract fun favoriteAssetDao(): FavoriteAssetDao
    abstract fun perpsOperationDao(): PerpsOperationDao
    abstract fun perpsFundingDao(): PerpsFundingDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun instance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .setDriver(BundledSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
