package com.tonapps.portfolio.screens.wallet

interface MigrationStatusProvider {
    suspend fun migratableWalletsCount(): Int
}
