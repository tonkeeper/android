package com.tonapps.deposit.multicoin.screens.confirm.fee

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.chain.ethereum.utils.Hermes
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesSelfCallTest {

    @Test
    fun aQuotedDepositReadsBackIntoItsApproveAndRelayCall() {
        val calls = HermesSelfCall.unbatch(SENDER, BigInteger.ZERO, QUOTED_DEPOSIT, CHAIN)

        assertEquals(listOf(USDC, RELAY), calls?.map { it.target.display.lowercase() })
        assertEquals(listOf(APPROVE, RELAY_CALL), calls?.map { it.data })
        assertTrue(calls.orEmpty().all { it.value == BigInteger.ZERO })
    }

    @Test
    fun aBatchReadsBackAsItWasWritten() {
        val written = listOf(
            Hermes.Call(Address.force(USDC, CHAIN), BigInteger.ZERO, APPROVE),
            Hermes.Call(Address.force(RELAY, CHAIN), BigInteger.fromInt(7), "0xdeadbeef"),
        )

        val calls = HermesSelfCall.unbatch(SENDER, BigInteger.ZERO, executeOf(written), CHAIN)

        assertEquals(written.map { Triple(it.target.display.lowercase(), it.value, it.data) }, calls?.map { Triple(it.target.display.lowercase(), it.value, it.data) })
    }

    @Test
    fun aSelfCallCarryingValueIsNotUnbatched() {
        assertNull(HermesSelfCall.unbatch(SENDER, BigInteger.ONE, QUOTED_DEPOSIT, CHAIN))
    }

    @Test
    fun aCallBackIntoTheSenderIsNotUnbatched() {
        val calls = listOf(Hermes.Call(Address.force(SENDER, CHAIN), BigInteger.ZERO, "0x"))

        assertNull(HermesSelfCall.unbatch(SENDER, BigInteger.ZERO, executeOf(calls), CHAIN))
    }

    @Test
    fun trailingBytesAreRefused() {
        assertNull(HermesSelfCall.unbatch(SENDER, BigInteger.ZERO, QUOTED_DEPOSIT + "00", CHAIN))
    }

    @Test
    fun anotherCallOrBrokenCalldataIsRefusedWithoutThrowing() {
        assertNull(HermesSelfCall.unbatch(SENDER, BigInteger.ZERO, APPROVE, CHAIN))
        assertNull(HermesSelfCall.unbatch(SENDER, BigInteger.ZERO, QUOTED_DEPOSIT.take(200), CHAIN))
        assertNull(HermesSelfCall.unbatch(SENDER, BigInteger.ZERO, "0xzz", CHAIN))
        assertNull(HermesSelfCall.unbatch(SENDER, BigInteger.ZERO, "0x", CHAIN))
    }

    @Test
    fun onlyACallToItselfIsASelfCall() {
        assertTrue(HermesSelfCall.isSelfCall(SENDER, SENDER.uppercase().replace("0X", "0x")))
        assertEquals(false, HermesSelfCall.isSelfCall(SENDER, RELAY))
    }

    private fun executeOf(calls: List<Hermes.Call>): String {
        return "0x" + Hermes.execute(Hermes.mode(), Hermes.encodeCalls(calls)).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val CHAIN: Chain = Chain.Base.Mainnet
        const val SENDER = "0x7ecebaf27c989adef9ea3dd06d316f6f463a5109"
        const val USDC = "0x833589fcd6edb6e08f4c7c32d4f71b54bda02913"
        const val RELAY = "0x4cd00e387622c35bddb9b4c962c136462338bc31"
        const val APPROVE = "0x095ea7b30000000000000000000000004cd00e387622c35bddb9b4c962c136462338bc3100000000000000000000000000000000000000000000000000000000001e7451"
        const val RELAY_CALL = "0xe80179520000000000000000000000007ecebaf27c989adef9ea3dd06d316f6f463a5109000000000000000000000000833589fcd6edb6e08f4c7c32d4f71b54bda0291300000000000000000000000000000000000000000000000000000000001e74519f2c172ea8127d299673190103c15f0a7beea89be02807784c22610ea0360c18"
        const val QUOTED_DEPOSIT = "0xe9ae5c530100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000002800000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000120000000000000000000000000833589fcd6edb6e08f4c7c32d4f71b54bda02913000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000044095ea7b30000000000000000000000004cd00e387622c35bddb9b4c962c136462338bc3100000000000000000000000000000000000000000000000000000000001e7451000000000000000000000000000000000000000000000000000000000000000000000000000000004cd00e387622c35bddb9b4c962c136462338bc31000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000084e80179520000000000000000000000007ecebaf27c989adef9ea3dd06d316f6f463a5109000000000000000000000000833589fcd6edb6e08f4c7c32d4f71b54bda0291300000000000000000000000000000000000000000000000000000000001e74519f2c172ea8127d299673190103c15f0a7beea89be02807784c22610ea0360c1800000000000000000000000000000000000000000000000000000000"
    }
}
