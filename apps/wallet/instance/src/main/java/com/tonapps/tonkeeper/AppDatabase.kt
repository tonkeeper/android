package com.tonapps.tonkeeper

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tonapps.tonkeeper.api.account.db.AccountDao
import com.tonapps.tonkeeper.api.account.db.AccountEntity
import com.tonapps.tonkeeper.api.history.db.HistoryDao
import com.tonapps.tonkeeper.api.history.db.HistoryEntity
import com.tonapps.tonkeeper.api.jetton.db.JettonDao
import com.tonapps.tonkeeper.api.jetton.db.JettonEntity
import com.tonapps.tonkeeper.api.collectibles.db.CollectiblesDao
import com.tonapps.tonkeeper.api.collectibles.db.CollectiblesEntity
import com.tonapps.tonkeeper.api.nft.db.NftDao
import com.tonapps.tonkeeper.api.nft.db.NftEntity
import com.tonapps.tonkeeper.core.tonconnect.db.AppDao
import com.tonapps.tonkeeper.core.tonconnect.db.AppEntity
import com.tonapps.tonkeeper.core.tonconnect.db.ManifestDao
import com.tonapps.tonkeeper.core.tonconnect.db.ManifestEntity

@Database(entities = [
    JettonEntity::class,
    HistoryEntity::class,
    CollectiblesEntity::class,
    AccountEntity::class,
    NftEntity::class,
    ManifestEntity::class,
    AppEntity::class
], version = 22, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun createInstance(context: Context): AppDatabase {
            val builder = Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "app"
            )
            builder.addCallback(object : Callback() {

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.enableWriteAheadLogging()
                    db.setMaxSqlCacheSize(SQLiteDatabase.MAX_SQL_CACHE_SIZE)
                    db.query("PRAGMA temp_store = MEMORY").close()
                    db.query("PRAGMA secure_delete = TRUE").close()
                }
            })
            builder.fallbackToDestructiveMigration()
            return builder.build()
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = createInstance(context)
                INSTANCE = instance
                instance
            }
        }
    }


    abstract fun jettonDao(): JettonDao

    abstract fun historyDao(): HistoryDao

    abstract fun collectiblesDao(): CollectiblesDao

    abstract fun accountDao(): AccountDao

    abstract fun nftDao(): NftDao

    abstract fun tonConnectManifestDao(): ManifestDao

    abstract fun tonConnectAppDao(): AppDao

}