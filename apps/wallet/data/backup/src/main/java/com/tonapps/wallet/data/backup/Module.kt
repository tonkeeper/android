package com.tonapps.wallet.data.backup

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val backupModule = module {
    singleOf(::BackupRepository)
}