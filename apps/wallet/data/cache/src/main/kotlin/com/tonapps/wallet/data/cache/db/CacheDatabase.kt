package com.tonapps.wallet.data.cache.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tonapps.wallet.data.cache.JsonResponseCacheDao
import com.tonapps.wallet.data.cache.JsonResponseCacheEntity
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        JsonResponseCacheEntity::class,
    ],
    version = 1,
)
internal abstract class CacheDatabase : RoomDatabase() {

    abstract fun jsonResponseCacheDao(): JsonResponseCacheDao

    companion object {

        private const val DATABASE_NAME = "json_cache"

        @Volatile
        private var INSTANCE: CacheDatabase? = null

        fun instance(context: Context): CacheDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CacheDatabase::class.java,
                    DATABASE_NAME,
                )
                    .setDriver(BundledSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
