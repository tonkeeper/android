package com.tonapps.wallet.api.entity

import com.tonapps.core.flags.BootFlags
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The `flags` object of `/keys/all`, as the boot config reads it. */
class FlagsEntityTest {

    @Test
    fun oneClickSwapAndTxValidationAreReadFromTheirKeys() {
        val flags = FlagsEntity(JSONObject("""{"one_click_swap_enabled": true, "tx_validation_enabled": true}"""))

        assertTrue(flags.oneClickSwapEnabled)
        assertTrue(flags.txValidationEnabled)
    }

    @Test
    fun eachFlagIsReadOnItsOwn() {
        val swapOnly = FlagsEntity(JSONObject("""{"one_click_swap_enabled": true, "tx_validation_enabled": false}"""))
        val validationOnly = FlagsEntity(JSONObject("""{"one_click_swap_enabled": false, "tx_validation_enabled": true}"""))

        assertTrue(swapOnly.oneClickSwapEnabled)
        assertFalse(swapOnly.txValidationEnabled)
        assertFalse(validationOnly.oneClickSwapEnabled)
        assertTrue(validationOnly.txValidationEnabled)
    }

    // A config that does not carry them keeps the classic swap and still validates: each fails closed.
    @Test
    fun aMissingFlagFallsBackToItsSafeValue() {
        val flags = FlagsEntity(JSONObject("""{"perps_enabled": true}"""))

        assertFalse(flags.oneClickSwapEnabled)
        assertTrue(flags.txValidationEnabled)
    }

    // The config the app runs on before /keys/all is read, or when it never loads.
    @Test
    fun theDefaultConfigValidatesWithoutOneClickSwap() {
        val flags = FlagsEntity()

        assertFalse(flags.oneClickSwapEnabled)
        assertTrue(flags.txValidationEnabled)
    }

    @Test
    fun onlyAnExplicitFalseSwitchesValidationOff() {
        val flags = FlagsEntity(JSONObject("""{"tx_validation_enabled": false}"""))

        assertFalse(flags.txValidationEnabled)
    }

    // The new keys sit next to the existing ones without shifting what those read.
    @Test
    fun theOtherFlagsReadAsBefore() {
        val json = JSONObject(
            """{"disable_swap": true, "disable_gaseless": true, "perps_enabled": true,
               "one_click_swap_enabled": true, "tx_validation_enabled": false}""",
        )

        val flags = FlagsEntity(json)

        assertEquals(
            FlagsEntity().copy(
                disableSwap = true,
                disableGasless = true,
                oneClickSwapEnabled = true,
                txValidationEnabled = false,
            ),
            flags,
        )
    }

    // What features read: both flags of the same config, in one snapshot.
    @Test
    fun bootFlagsCarryBothFlagsOfOneConfig() {
        val flags = FlagsEntity(JSONObject("""{"one_click_swap_enabled": true, "tx_validation_enabled": false}"""))

        assertEquals(BootFlags.Values(oneClickSwap = true, txValidation = false), flags.bootFlags)
    }

    @Test
    fun theDefaultConfigGivesTheSafeBootFlags() {
        assertEquals(BootFlags.Values.Safe, FlagsEntity().bootFlags)
    }
}
