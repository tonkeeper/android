package com.tonapps.perps.region

import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.PerpsSizeDirection
import com.tonapps.perps.screens.details.PerpsAssetDetailsTarget
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerpsRegionAccessTest {

    @Test
    fun openingOrAddingIsBlocked() {
        assertTrue(PerpsAssetDetailsTarget.Order(PerpsOrderSide.LONG).isBlockedInRestrictedRegion)
        assertTrue(PerpsAssetDetailsTarget.Order(PerpsOrderSide.SHORT).isBlockedInRestrictedRegion)
        assertTrue(
            PerpsAssetDetailsTarget.SizeChange(PerpsOrderSide.LONG, PerpsSizeDirection.ADD).isBlockedInRestrictedRegion,
        )
        assertTrue(PerpsAssetDetailsTarget.MarginChange(PerpsOrderSide.LONG, add = true).isBlockedInRestrictedRegion)
    }

    @Test
    fun closingOrReducingIsAllowed() {
        assertFalse(PerpsAssetDetailsTarget.CashOut(PerpsOrderSide.LONG).isBlockedInRestrictedRegion)
        assertFalse(
            PerpsAssetDetailsTarget.SizeChange(PerpsOrderSide.SHORT, PerpsSizeDirection.REDUCE).isBlockedInRestrictedRegion,
        )
        assertFalse(PerpsAssetDetailsTarget.MarginChange(PerpsOrderSide.SHORT, add = false).isBlockedInRestrictedRegion)
    }
}
