package com.tonapps.wallet.data.backup

import org.koin.dsl.module

val backupModule = module {
    single { BackupRepository(get(), get(), get()) }
}