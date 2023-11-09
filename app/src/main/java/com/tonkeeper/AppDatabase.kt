package com.tonkeeper

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tonkeeper.api.account.db.AccountDao
import com.tonkeeper.api.account.db.AccountEntity
import com.tonkeeper.api.event.db.EventDao
import com.tonkeeper.api.event.db.EventEntity
import com.tonkeeper.api.jetton.db.JettonDao
import com.tonkeeper.api.jetton.db.JettonEntity
import com.tonkeeper.api.collectibles.db.CollectiblesDao
import com.tonkeeper.api.collectibles.db.CollectiblesEntity
import com.tonkeeper.api.nft.db.NftDao
import com.tonkeeper.api.nft.db.NftEntity
import com.tonkeeper.core.tonconnect.db.AppDao
import com.tonkeeper.core.tonconnect.db.AppEntity
import com.tonkeeper.core.tonconnect.db.ManifestDao
import com.tonkeeper.core.tonconnect.db.ManifestEntity

@Database(entities = [
    JettonEntity::class,
    EventEntity::class,
    CollectiblesEntity::class,
    AccountEntity::class,
    NftEntity::class,
    ManifestEntity::class,
    AppEntity::class
], version = 16, exportSchema = false)
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
                    db.setMaxSqlCacheSize(SQLiteDatabase.MAX_SQL_CACHE_SIZE)
                    db.enableWriteAheadLogging()
                    db.query("PRAGMA journal_mode = WAL")
                    db.query("PRAGMA synchronous = OFF")
                    db.query("PRAGMA temp_store = MEMORY")
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

    abstract fun eventDao(): EventDao

    abstract fun collectiblesDao(): CollectiblesDao

    abstract fun accountDao(): AccountDao

    abstract fun nftDao(): NftDao

    abstract fun tonConnectManifestDao(): ManifestDao

    abstract fun tonConnectAppDao(): AppDao

}