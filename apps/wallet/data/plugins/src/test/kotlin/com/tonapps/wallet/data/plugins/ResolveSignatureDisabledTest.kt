package com.tonapps.wallet.data.plugins

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveSignatureDisabledTest {

    private var fetches = 0

    private fun answer(value: Boolean?): suspend () -> Boolean? = {
        fetches++
        value
    }

    @Test
    fun `cached no signs without asking`() = runTest {
        assertFalse(resolveSignatureDisabled(cached = false, fetch = answer(true)))
        assertEquals(0, fetches)
    }

    @Test
    fun `nothing cached follows the fresh answer`() = runTest {
        assertTrue(resolveSignatureDisabled(cached = null, fetch = answer(true)))
        assertFalse(resolveSignatureDisabled(cached = null, fetch = answer(false)))
    }

    @Test
    fun `nothing cached and no answer signs`() = runTest {
        assertFalse(resolveSignatureDisabled(cached = null, fetch = answer(null)))
    }

    @Test
    fun `cached yes blocks only when the fresh answer confirms it`() = runTest {
        assertTrue(resolveSignatureDisabled(cached = true, fetch = answer(true)))
        assertFalse(resolveSignatureDisabled(cached = true, fetch = answer(false)))
        assertEquals(2, fetches)
    }

    @Test
    fun `cached yes and no answer signs`() = runTest {
        assertFalse(resolveSignatureDisabled(cached = true, fetch = answer(null)))
    }
}
