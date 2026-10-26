package com.tonapps.core.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class SolanaTransferLinkTest {

    private val recipient = "HLT1cw2FTfgMJ2DNZHQpcr19n5bqrfqBGxEuFSEWgE4r"
    private val usdcMint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
    private val reference = "82ZJ7nbGpixjeDCmEhUcmwXYfvurzAgGdtSMuHnUgyny"

    @Test
    fun parsesBareRecipientAsSolTransfer() {
        val transfer = SolanaTransferLink.parse("solana:$recipient")!!

        assertEquals(recipient, transfer.recipient)
        assertNull(transfer.mint)
        assertNull(transfer.amount)
        assertEquals("sol/mainnet/coin", transfer.assetId)
    }

    @Test
    fun parsesSolAmountInDisplayUnits() {
        val transfer = SolanaTransferLink.parse("solana:$recipient?amount=0.001")!!

        assertEquals(BigDecimal("0.001"), transfer.amount)
        assertEquals(BigInteger.valueOf(1_000_000), transfer.baseUnits(9))
    }

    @Test
    fun parsesSplTokenTransferKeepingMintCase() {
        val transfer = SolanaTransferLink.parse("solana:$recipient?amount=1.5&spl-token=$usdcMint")!!

        assertEquals(recipient, transfer.recipient)
        assertEquals(usdcMint, transfer.mint)
        assertEquals("sol/mainnet/spl/$usdcMint", transfer.assetId)
        assertEquals(BigInteger.valueOf(1_500_000), transfer.baseUnits(6))
    }

    @Test
    fun acceptsUppercaseSchemeAndSurroundingSpaces() {
        assertEquals(recipient, SolanaTransferLink.parse("  SOLANA:$recipient  ")!!.recipient)
    }

    @Test
    fun acceptsKeyWithLeadingZeroBytes() {
        val systemProgram = "11111111111111111111111111111111"

        assertEquals(systemProgram, SolanaTransferLink.parse("solana:$systemProgram")!!.recipient)
    }

    @Test
    fun dropsDisplayOnlyParameters() {
        val transfer = SolanaTransferLink.parse(
            "solana:$recipient?amount=2&label=Coffee%20shop&message=Thanks%21"
        )!!

        assertEquals(recipient, transfer.recipient)
        assertEquals(BigDecimal("2"), transfer.amount)
    }

    @Test
    fun keepsFirstOfRepeatedParameters() {
        val transfer = SolanaTransferLink.parse("solana:$recipient?amount=1&amount=2")!!

        assertEquals(BigDecimal("1"), transfer.amount)
    }

    @Test
    fun rejectsOtherSchemes() {
        assertNull(SolanaTransferLink.parse("ethereum:$recipient"))
        assertNull(SolanaTransferLink.parse("ton://transfer/$recipient"))
        assertNull(SolanaTransferLink.parse(recipient))
    }

    @Test
    fun rejectsTransactionRequests() {
        assertNull(SolanaTransferLink.parse("solana:https://example.com/pay"))
        assertNull(SolanaTransferLink.parse("solana:https%3A%2F%2Fexample.com%2Fpay%3Forder%3D12345"))
    }

    @Test
    fun rejectsInvalidRecipient() {
        assertNull(SolanaTransferLink.parse("solana:"))
        assertNull(SolanaTransferLink.parse("solana:0xde0B295669a9FD93d5F28D9Ec85E40f4cb697BAe"))
        // 0, O, I and l are not base58.
        assertNull(SolanaTransferLink.parse("solana:0LT1cw2FTfgMJ2DNZHQpcr19n5bqrfqBGxEuFSEWgE4r"))
        // Valid base58, but not 32 bytes.
        assertNull(SolanaTransferLink.parse("solana:HLT1cw2FTfgMJ2DNZHQpcr19n5bqrfqB"))
        assertNull(SolanaTransferLink.parse("solana:${recipient}zz"))
        assertNull(SolanaTransferLink.parse("solana:TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"))
    }

    @Test
    fun rejectsInvalidAmount() {
        for (amount in listOf("", ".5", "-1", "+1", "1e3", "1,5", "1.2.3", "abc", "0x10", "1%2000")) {
            assertNull(amount, SolanaTransferLink.parse("solana:$recipient?amount=$amount"))
        }
    }

    @Test
    fun acceptsAmountWithBareSeparatorOrLeadingZeros() {
        assertEquals(BigInteger.valueOf(100), SolanaTransferLink.parse("solana:$recipient?amount=1.")!!.baseUnits(2))
        assertEquals(BigInteger.valueOf(75), SolanaTransferLink.parse("solana:$recipient?amount=007.5")!!.baseUnits(1))
    }

    @Test
    fun rejectsInvalidSplToken() {
        assertNull(SolanaTransferLink.parse("solana:$recipient?spl-token=USDC"))
        assertNull(SolanaTransferLink.parse("solana:$recipient?spl-token="))
    }

    @Test
    fun rejectsReference() {
        assertNull(SolanaTransferLink.parse("solana:$recipient?amount=1&reference=$reference"))
        assertNull(SolanaTransferLink.parse("solana:$recipient?amount=1&reference"))
    }

    @Test
    fun rejectsMemo() {
        assertNull(SolanaTransferLink.parse("solana:$recipient?amount=1&memo=order-42"))
    }

    @Test
    fun treatsEmptyMemoAsNoMemo() {
        assertEquals(recipient, SolanaTransferLink.parse("solana:$recipient?amount=1&memo=")!!.recipient)
    }

    @Test
    fun refusesAmountFinerThanDecimals() {
        val transfer = SolanaTransferLink.parse("solana:$recipient?amount=0.0000001&spl-token=$usdcMint")!!

        assertNull(transfer.baseUnits(6))
        assertEquals(BigInteger.valueOf(100), transfer.baseUnits(9))
    }

    @Test
    fun ignoresTrailingZerosBeyondDecimals() {
        val transfer = SolanaTransferLink.parse("solana:$recipient?amount=1.50000000000")!!

        assertEquals(BigInteger.valueOf(1_500_000), transfer.baseUnits(6))
    }

    @Test
    fun hasNoBaseUnitsWithoutAmount() {
        assertNull(SolanaTransferLink.parse("solana:$recipient")!!.baseUnits(9))
    }
}
