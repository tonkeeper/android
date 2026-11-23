package com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveWalletLeavesTest {

    @Test
    fun `the wallet active when the dApp opens is not a reason to close`() = runTest {
        assertTrue(flowOf("own").activeWalletLeaves("own").toList().isEmpty())
        assertTrue(flowOf("other").activeWalletLeaves("own").toList().isEmpty())
    }

    @Test
    fun `switching away from the wallet closes once`() = runTest {
        val emissions = flowOf("own", "own", "other", "third").activeWalletLeaves("own").toList()

        assertEquals(1, emissions.size)
    }

    @Test
    fun `a dApp opened for another wallet closes on the next switch, but not on a switch to it`() = runTest {
        assertTrue(flowOf("other", "own").activeWalletLeaves("own").toList().isEmpty())
        assertEquals(1, flowOf("other", "third").activeWalletLeaves("own").toList().size)
    }

    @Test
    fun `deleting every wallet closes`() = runTest {
        assertEquals(1, flowOf("own", null).activeWalletLeaves("own").toList().size)
    }
}
