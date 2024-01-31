package com.tonapps.signer.core.di

import com.tonapps.signer.core.Database
import com.tonapps.signer.core.repository.KeyRepository
import com.tonapps.signer.core.source.KeyDataSource
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val coreModule = module {
    single<Database> { Database(androidContext()) }

    single { KeyDataSource(get<Database>()) }
    single { KeyRepository(get<KeyDataSource>()) }
}