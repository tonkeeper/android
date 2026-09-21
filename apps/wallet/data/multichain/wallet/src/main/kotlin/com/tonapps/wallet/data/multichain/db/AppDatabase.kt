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
import com.tonapps.wallet.data.multichain.db.FavoriteAssetEntity
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

@Database(
    entities = [
        AccountEntity::class,
        McWalletEntity::class,
        CredentialEntity::class,
        VaultMetadataEntity::class,
        FavoriteAssetEntity::class,
    ],
    version = 2
)
internal abstract class AppDatabase : RoomDatabase() {

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
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }

    abstract fun accountDao(): AccountDao
    abstract fun walletDao(): WalletDao
    abstract fun credentialDao(): CredentialDao
    abstract fun walletBundleDao(): WalletBundleDao
    abstract fun vaultMetadataDao(): VaultMetadataDao
    abstract fun favoriteAssetDao(): FavoriteAssetDao
}
