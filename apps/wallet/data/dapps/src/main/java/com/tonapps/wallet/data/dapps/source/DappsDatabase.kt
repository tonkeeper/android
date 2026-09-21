package com.tonapps.wallet.data.dapps.source

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.tonapps.async.Async
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.extensions.AppContext
import com.tonapps.wallet.data.core.DatabaseCorruptionHandler
import com.tonapps.wallet.data.core.recordException
import com.tonapps.wallet.data.dapps.source.db.AppDao
import com.tonapps.wallet.data.dapps.source.db.AppRow
import com.tonapps.wallet.data.dapps.source.db.ConnectDao
import com.tonapps.wallet.data.dapps.source.db.ConnectEntity
import com.tonapps.wallet.data.dapps.source.db.NotificationDao
import com.tonapps.wallet.data.dapps.source.db.NotificationRow
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@Database(
    entities = [
        AppRow::class,
        ConnectEntity::class,
        NotificationRow::class,
    ],
    version = 4,
)
@TypeConverters(DappConnectionConverters::class)
abstract class DappsDatabase : RoomDatabase() {

    abstract fun appDao(): AppDao
    abstract fun connectDao(): ConnectDao
    abstract fun notificationDao(): NotificationDao

    companion object {

        private const val DATABASE_NAME = "dapps"

        @Volatile
        private var INSTANCE: DappsDatabase? = null

        @Volatile
        private var validationTask: Deferred<Boolean>? = null

        fun validate() {
            validationTask = DatabaseCorruptionHandler
                .deleteIfCorrupted(AppContext.value, DATABASE_NAME)
        }

        fun instance(context: Context): DappsDatabase {
            runBlocking {
                runCatching { validationTask?.await() }
            }

            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: createDatabase(context).also { INSTANCE = it }
            }
        }

        private fun createDatabase(context: Context): DappsDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                DappsDatabase::class.java,
                DATABASE_NAME,
            )
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .addCallback(destructiveMigrationCallback)
                .build()
        }

        private val destructiveMigrationCallback = object : RoomDatabase.Callback() {
            override fun onDestructiveMigration(connection: SQLiteConnection) {
                recordException(IllegalStateException("$DATABASE_NAME recreated, migration failed"))
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notifications` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT,
                        `app_url` TEXT,
                        `account_id` TEXT,
                        `body` TEXT
                    )
                    """.trimIndent()
                )
                connection.execSQL("CREATE INDEX IF NOT EXISTS `idx_notifications_app_url` ON `notifications` (`app_url`)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS `idx_notifications_account_id` ON `notifications` (`account_id`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `connect` ADD COLUMN `network` INTEGER DEFAULT ${TonNetwork.MAINNET.value}")
                connection.execSQL(
                    """
                    UPDATE `connect` SET `network` =
                    CASE WHEN `testnet` = 1 THEN ${TonNetwork.TESTNET.value} ELSE ${TonNetwork.MAINNET.value} END
                    """.trimIndent()
                )
            }
        }

        // Take over the legacy `dapps` SQLite file (previously managed by SQLiteHelper at v3) and
        // extend `connect` to host both TonConnect and WalletConnect rows distinguished by
        // `provider`.
        //
        // Every table is recreated and copied rather than patched with ALTER, because the legacy
        // shapes diverge in ways ALTER cannot repair, and Room validates the result column by
        // column on open:
        //   - `url TEXT PRIMARY KEY` / `client_id TEXT PRIMARY KEY` are nullable in SQLite (only
        //     INTEGER PRIMARY KEY implies NOT NULL), while Room expects NOT NULL;
        //   - `network` carries DEFAULT 1 only in files upgraded from v2, not in ones created at v3;
        //   - `testnet` exists only in files upgraded from v2;
        //   - `notifications.body` was declared TEXT and must become BLOB.
        // Values are preserved: SQLite keeps stored value types regardless of declared affinity, so
        // rows copy verbatim. Rows with a NULL primary key are dropped — Room cannot represent them.
        //
        // Legacy state at v3:
        //   - app(url TEXT PK, name TEXT, icon_url TEXT) + idx_app_url
        //   - connect(client_id TEXT PK, account_id TEXT, network INTEGER, type INTEGER,
        //             app_url TEXT, timestamp INTEGER[, testnet INTEGER]) + idx_connect_*
        //   - notifications(id INTEGER PK AUTOINCREMENT, app_url TEXT, account_id TEXT, body TEXT)
        //             + idx_notifications_*
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                migrateAppTable(connection)
                migrateConnectTable(connection)
                migrateNotificationsTable(connection)
            }

            private fun migrateAppTable(db: SQLiteConnection) {
                db.execSQL("DROP INDEX IF EXISTS `idx_app_url`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_new` (
                        `url` TEXT NOT NULL,
                        `name` TEXT,
                        `icon_url` TEXT,
                        PRIMARY KEY(`url`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO `app_new` (`url`, `name`, `icon_url`)
                    SELECT `url`, `name`, `icon_url` FROM `app` WHERE `url` IS NOT NULL
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `app`")
                db.execSQL("ALTER TABLE `app_new` RENAME TO `app`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_app_url` ON `app` (`url`)")
            }

            private fun migrateConnectTable(db: SQLiteConnection) {
                db.execSQL("DROP INDEX IF EXISTS `idx_connect_client_id`")
                db.execSQL("DROP INDEX IF EXISTS `idx_connect_account_id_network`")
                db.execSQL("DROP INDEX IF EXISTS `idx_connect_app_url`")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `connect_new` (
                        `id` TEXT NOT NULL,
                        `provider` TEXT NOT NULL DEFAULT 'ton_connect',
                        `wallet_id` TEXT,
                        `app_url` TEXT,
                        `timestamp` INTEGER,
                        `account_id` TEXT,
                        `mode` INTEGER DEFAULT 1,
                        `type` INTEGER,
                        `topic` TEXT,
                        `source` TEXT,
                        `key_pair` BLOB,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                // `provider` is left out of the column list so every legacy row picks up the
                // 'ton_connect' default; WalletConnect rows only ever appear from v4 onwards.
                // `mode` holds a TonNetwork value, hence the MAINNET fallback — the column-level
                // DEFAULT 1 above is not a valid network, it only mirrors what ConnectEntity
                // declares and what Room validates against.
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO `connect_new` (`id`, `app_url`, `timestamp`, `account_id`, `mode`, `type`)
                    SELECT `client_id`, `app_url`, `timestamp`, `account_id`,
                           COALESCE(`network`, ${TonNetwork.MAINNET.value}), `type`
                    FROM `connect` WHERE `client_id` IS NOT NULL
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `connect`")
                db.execSQL("ALTER TABLE `connect_new` RENAME TO `connect`")

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_connect_topic` ON `connect` (`topic`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_connect_account_id_mode` ON `connect` (`account_id`, `mode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_connect_type_app_url` ON `connect` (`type`, `app_url`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_connect_provider_app_url` ON `connect` (`provider`, `app_url`)")
            }

            private fun migrateNotificationsTable(db: SQLiteConnection) {
                db.execSQL("DROP INDEX IF EXISTS `idx_notifications_app_url`")
                db.execSQL("DROP INDEX IF EXISTS `idx_notifications_account_id`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notifications_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `app_url` TEXT,
                        `account_id` TEXT,
                        `body` BLOB
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `notifications_new` (`id`, `app_url`, `account_id`, `body`)
                    SELECT `id`, `app_url`, `account_id`, `body` FROM `notifications`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `notifications`")
                db.execSQL("ALTER TABLE `notifications_new` RENAME TO `notifications`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_app_url` ON `notifications` (`app_url`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_account_id` ON `notifications` (`account_id`)")
            }
        }
    }
}
