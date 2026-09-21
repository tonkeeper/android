package com.tonapps.wallet.data.cache

import com.tonapps.wallet.data.cache.db.CacheDatabase
import org.koin.dsl.module

val cacheModule = module {
    single { CacheDatabase.instance(get()) }
    single { get<CacheDatabase>().jsonResponseCacheDao() }
    single { JsonCacheRepository(get()) }
}
