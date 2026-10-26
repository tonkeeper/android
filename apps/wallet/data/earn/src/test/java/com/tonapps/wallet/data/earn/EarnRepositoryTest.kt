package com.tonapps.wallet.data.earn

import org.junit.Assert.assertEquals
import org.junit.Test

class EarnRepositoryTest {

    @Test
    fun namesEveryVaultOnce() {
        val ids = EarnRepository().featuredVaults().map { "${it.network}/${it.vaultId}" }

        assertEquals(ids.size, ids.toSet().size)
    }
}