package com.tonapps.migration

import com.tonapps.migration.data.MigrationEmulationMapper
import com.tonapps.migration.data.MigrationRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val migrationModule = module {
    singleOf(::MigrationRepository)
    singleOf(::MigrationEmulationMapper)
}
