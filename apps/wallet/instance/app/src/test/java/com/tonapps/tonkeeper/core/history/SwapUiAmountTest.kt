package com.tonapps.tonkeeper.core.history

import android.net.Uri
import com.tonapps.icu.Coins
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.tonapi.models.AccountAddress
import io.tonapi.models.JettonPreview
import io.tonapi.models.JettonSwapAction
import io.tonapi.models.JettonVerificationType
import io.tonapi.models.ScaledUI
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import java.math.BigDecimal

class SwapUiAmountTest {

    @Test
    fun `ton for a scaled jetton scales only the received jetton`() {
        val swap = swap(amountOut = "150000000", tonIn = 2_000_000_000, jettonOut = doubledStock)

        assertAmount("2", 9, swap.uiAmountIn)
        assertAmount("3", 8, swap.uiAmountOut)
    }

    @Test
    fun `scaled jetton for ton scales only the sent jetton`() {
        val swap = swap(amountIn = "150000000", jettonIn = doubledStock, tonOut = 2_000_000_000)

        assertAmount("3", 8, swap.uiAmountIn)
        assertAmount("2", 9, swap.uiAmountOut)
    }

    @Test
    fun `scaled jetton for a plain jetton keeps the plain amount`() {
        val swap = swap(amountIn = "150000000", amountOut = "1500000", jettonIn = doubledStock, jettonOut = usdt)

        assertAmount("3", 8, swap.uiAmountIn)
        assertAmount("1.5", 6, swap.uiAmountOut)
    }

    @Test
    fun `two scaled jettons are each scaled by their own multiplier`() {
        val swap = swap(amountIn = "150000000", amountOut = "100000000", jettonIn = doubledStock, jettonOut = halvedStock)

        assertAmount("3", 8, swap.uiAmountIn)
        assertAmount("0.5", 8, swap.uiAmountOut)
    }

    @Test
    fun `plain jettons and ton are shown as they are`() {
        val swap = swap(amountIn = "1500000", jettonIn = usdt, tonOut = 2_000_000_000)

        assertAmount("1.5", 6, swap.uiAmountIn)
        assertAmount("2", 9, swap.uiAmountOut)
    }

    private fun assertAmount(expected: String, decimals: Int, actual: Coins) {
        assertEquals("$actual", 0, actual.value.compareTo(BigDecimal(expected)))
        assertEquals(decimals, actual.decimals)
    }

    private fun swap(
        amountIn: String = "",
        amountOut: String = "",
        tonIn: Long? = null,
        tonOut: Long? = null,
        jettonIn: JettonPreview? = null,
        jettonOut: JettonPreview? = null,
    ) = JettonSwapAction(
        dex = "stonfi",
        amountIn = amountIn,
        amountOut = amountOut,
        userWallet = account(USER_ADDRESS),
        router = account(ROUTER_ADDRESS),
        tonIn = tonIn,
        tonOut = tonOut,
        jettonMasterIn = jettonIn,
        jettonMasterOut = jettonOut,
    )

    private fun account(address: String) = AccountAddress(
        address = address,
        isScam = false,
        isWallet = true,
    )

    companion object {
        private const val USER_ADDRESS = "0:7a4d7a8fbb1c5b0b4c41c2b6d1ea6bd2b6a1a1c3f1f3e7a1d2c4b5a6978899aa"
        private const val ROUTER_ADDRESS = "0:779dcc815138d9500e449c5291e7f12738c23d575b5310000f6a253bd607384e"

        private val doubledStock = jetton(
            address = "0:af6be39feda710754fe98800dd95c6f3b05b40fb127066872dcfc64e5c1741a1",
            symbol = "NVDAx",
            decimals = 8,
            scaledUi = ScaledUI(numerator = "2", denominator = "1"),
        )

        private val halvedStock = jetton(
            address = "0:757f204003da900cfe8b106685ddc6d4f976ebfa9e844816469ea94790720700",
            symbol = "SPYx",
            decimals = 8,
            scaledUi = ScaledUI(numerator = "1", denominator = "2"),
        )

        private val usdt = jetton(
            address = "0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe",
            symbol = "USD₮",
            decimals = 6,
            scaledUi = null,
        )

        private fun jetton(address: String, symbol: String, decimals: Int, scaledUi: ScaledUI?) = JettonPreview(
            address = address,
            name = symbol,
            symbol = symbol,
            decimals = decimals,
            image = "https://cache.tonapi.io/$symbol.png",
            verification = JettonVerificationType.whitelist,
            score = 0,
            scaledUi = scaledUi,
        )

        @BeforeClass
        @JvmStatic
        fun stubUri() {
            mockkStatic(Uri::class)
            every { Uri.parse(any()) } returns mockk(relaxed = true)
            mockkConstructor(Uri.Builder::class)
            every { anyConstructed<Uri.Builder>().scheme(any()) } answers { self as Uri.Builder }
            every { anyConstructed<Uri.Builder>().path(any()) } answers { self as Uri.Builder }
            every { anyConstructed<Uri.Builder>().build() } returns mockk(relaxed = true)
        }

        @AfterClass
        @JvmStatic
        fun unstubUri() {
            unmockkAll()
        }
    }
}
