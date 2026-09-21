package com.tonapps.tonkeeper.manager.migration

import com.tonapps.migration.data.MigrationRepository
import com.tonapps.portfolio.screens.wallet.MigrationStatusProvider

class WalletMigrationStatusProvider(
    private val migrationRepository: MigrationRepository,
) : MigrationStatusProvider {

    override suspend fun migratableWalletsCount(): Int {
        return migrationRepository.migratableWalletsCount()
    }
}
