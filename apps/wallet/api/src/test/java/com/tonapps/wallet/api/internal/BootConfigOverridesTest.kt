package com.tonapps.wallet.api.internal

import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.api.entity.PerpsAccess
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The debug `bootFlags` launch extra over the backend's `/keys/all` flags. */
class BootConfigOverridesTest {

    private val backend = ConfigEntity.default.copy(
        flags = ConfigEntity.default.flags.copy(oneClickSwapEnabled = true, txValidationEnabled = true),
    )

    @Test
    fun theOverlayTurnsBothFlagsOff() {
        val overlay = JSONObject("""{"one_click_swap_enabled": false, "tx_validation_enabled": false}""")

        val flags = BootConfigOverrides.overlaid(backend, overlay).flags

        assertFalse(flags.oneClickSwapEnabled)
        assertFalse(flags.txValidationEnabled)
    }

    @Test
    fun aKeyTheOverlayDoesNotNameKeepsTheBackendsValue() {
        val overlay = JSONObject("""{"tx_validation_enabled": false}""")

        val flags = BootConfigOverrides.overlaid(backend, overlay).flags

        assertTrue(flags.oneClickSwapEnabled)
        assertFalse(flags.txValidationEnabled)
    }

    @Test
    fun thePerpsAccessKeyForcesTheState() {
        val available = backend.copy(perpsAccess = PerpsAccess.Available)
        val overlay = JSONObject("""{"perps_access":"restricted"}""")

        assertEquals(PerpsAccess.Restricted, BootConfigOverrides.overlaid(available, overlay).perpsAccess)
    }

    @Test
    fun anUnknownOrMissingPerpsAccessKeepsTheBackendsState() {
        val available = backend.copy(perpsAccess = PerpsAccess.Available)

        assertEquals(
            PerpsAccess.Available,
            BootConfigOverrides.overlaid(available, JSONObject("""{"perps_access":"nope"}""")).perpsAccess,
        )
        assertEquals(
            PerpsAccess.Available,
            BootConfigOverrides.overlaid(available, JSONObject("""{"tx_validation_enabled": false}""")).perpsAccess,
        )
    }

    @Test
    fun withoutAnOverlayTheConfigIsTheBackends() {
        assertSame(backend, BootConfigOverrides.overlaid(backend, null))
    }

    // A second extra in the same process replaces the first whole: what it does not name is the backend's again.
    @Test
    fun aLaterOverlayReplacesTheEarlierOne() {
        try {
            BootConfigOverrides.applyOverlay("""{"one_click_swap_enabled": false}""")
            BootConfigOverrides.applyOverlay("""{"tx_validation_enabled": false}""")

            val flags = BootConfigOverrides.applyTo(backend).flags

            assertTrue(flags.oneClickSwapEnabled)
            assertFalse(flags.txValidationEnabled)
        } finally {
            BootConfigOverrides.applyOverlay(null)
        }
    }
}
