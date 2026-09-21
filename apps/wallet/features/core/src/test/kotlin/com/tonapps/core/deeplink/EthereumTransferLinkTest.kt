package com.tonapps.core.deeplink

import com.tonapps.chainkit.core.chain.model.account.Chain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigInteger

class EthereumTransferLinkTest {

    private val recipient = "0xde0B295669a9FD93d5F28D9Ec85E40f4cb697BAe"
    private val usdtContract = "0xdAC17F958D2ee523a2206206994597C13D831ec7"

    @Test
    fun parsesBareAddressAsNativeTransferWithoutChain() {
        val transfer = EthereumTransferLink.parse("ethereum:$recipient")!!

        assertEquals(recipient, transfer.recipient)
        assertNull(transfer.contract)
        assertNull(transfer.chain)
        assertNull(transfer.amount)
    }

    @Test
    fun parsesEthSchemeAndPayPrefix() {
        assertEquals(recipient, EthereumTransferLink.parse("eth:$recipient")!!.recipient)
        assertEquals(recipient, EthereumTransferLink.parse("ethereum:pay-$recipient")!!.recipient)
        assertEquals(recipient, EthereumTransferLink.parse("ETHEREUM:$recipient")!!.recipient)
    }

    @Test
    fun parsesNativeTransferWithChainAndValue() {
        val transfer = EthereumTransferLink.parse("ethereum:$recipient@56?value=1e16")!!

        assertEquals(recipient, transfer.recipient)
        assertNull(transfer.contract)
        assertEquals(Chain.Smartchain.Mainnet, transfer.chain)
        assertEquals(BigInteger.TEN.pow(16), transfer.amount)
    }

    @Test
    fun parsesErc20TransferWithAddressAndAmount() {
        val transfer = EthereumTransferLink.parse(
            "ethereum:$usdtContract@1/transfer?address=$recipient&uint256=1.5e6"
        )!!

        assertEquals(recipient, transfer.recipient)
        assertEquals(usdtContract, transfer.contract)
        assertEquals(Chain.Ethereum.Mainnet, transfer.chain)
        assertEquals(BigInteger.valueOf(1_500_000), transfer.amount)
    }

    @Test
    fun parsesErc20TransferWithoutChain() {
        val transfer = EthereumTransferLink.parse(
            "ethereum:$usdtContract/transfer?address=$recipient&uint256=1000000"
        )!!

        assertEquals(usdtContract, transfer.contract)
        assertNull(transfer.chain)
        assertEquals(BigInteger.valueOf(1_000_000), transfer.amount)
    }

    @Test
    fun buildsAssetIds() {
        val native = EthereumTransferLink.parse("ethereum:$recipient@1")!!
        assertEquals("eth/mainnet/coin", native.assetId(native.chain!!))

        val erc20 = EthereumTransferLink.parse("ethereum:$usdtContract@1/transfer?address=$recipient")!!
        assertEquals(
            "eth/mainnet/erc20/${usdtContract.lowercase()}",
            erc20.assetId(erc20.chain!!)
        )

        val bep20 = EthereumTransferLink.parse("ethereum:$usdtContract@56/transfer?address=$recipient")!!
        assertEquals(
            "bsc/mainnet/bep20/${usdtContract.lowercase()}",
            bep20.assetId(bep20.chain!!)
        )
    }

    @Test
    fun rejectsUnknownChainId() {
        assertNull(EthereumTransferLink.parse("ethereum:$recipient@2"))
        assertNull(EthereumTransferLink.parse("ethereum:$recipient@abc"))
        assertNull(EthereumTransferLink.parse("ethereum:$recipient@"))
    }

    @Test
    fun rejectsNonTransferFunctionsAndEnsNames() {
        assertNull(EthereumTransferLink.parse("ethereum:$usdtContract@1/approve?address=$recipient&uint256=1"))
        assertNull(EthereumTransferLink.parse("ethereum:vitalik.eth"))
        assertNull(EthereumTransferLink.parse("ethereum:foo-$recipient"))
        assertNull(EthereumTransferLink.parse("bitcoin:$recipient"))
    }

    @Test
    fun rejectsMalformedAddresses() {
        assertNull(EthereumTransferLink.parse("ethereum:0xde0B29"))
        assertNull(EthereumTransferLink.parse("ethereum:${recipient}ff"))
        assertNull(EthereumTransferLink.parse("ethereum:${recipient.dropLast(1)}Z"))
        assertNull(EthereumTransferLink.parse("ethereum:$usdtContract@1/transfer?address=vitalik.eth&uint256=1"))
        assertNull(EthereumTransferLink.parse("ethereum:$usdtContract@1/transfer?uint256=1"))
    }

    @Test
    fun rejectsAmountsThatAreNotWholeAtomicUnits() {
        assertNull(EthereumTransferLink.parse("ethereum:$recipient?value=1.5"))
        assertNull(EthereumTransferLink.parse("ethereum:$recipient?value=1.55e1"))
        assertNull(EthereumTransferLink.parse("ethereum:$recipient?value=1e79"))
        assertNull(EthereumTransferLink.parse("ethereum:$recipient?value=-1"))
        assertNull(EthereumTransferLink.parse("ethereum:$recipient?value=abc"))
    }

    @Test
    fun parsesScientificAmounts() {
        assertEquals(
            BigInteger("2014000000000000000"),
            EthereumTransferLink.parse("ethereum:$recipient?value=2.014e18")!!.amount
        )
        assertEquals(
            BigInteger.valueOf(123),
            EthereumTransferLink.parse("ethereum:$recipient?value=+1.23e2")!!.amount
        )
        assertEquals(
            BigInteger.TEN.pow(18),
            EthereumTransferLink.parse("ethereum:$recipient?value=1e+18")!!.amount
        )
        assertEquals(
            BigInteger.valueOf(7),
            EthereumTransferLink.parse("ethereum:$recipient?value=7e")!!.amount
        )
    }

    @Test
    fun rejectsAmountsAboveUint256() {
        assertNull(EthereumTransferLink.parse("ethereum:$recipient?value=999e78"))
        assertNull(EthereumTransferLink.parse("ethereum:$recipient?value=${"9".repeat(80)}"))
        assertNull(EthereumTransferLink.parse("ethereum:$recipient?value=1e-18"))
    }

    @Test
    fun rejectsTestnetChainIds() {
        assertNull(EthereumTransferLink.parse("ethereum:$recipient@11155111"))
        assertNull(EthereumTransferLink.parse("ethereum:$recipient@97"))
    }

    @Test
    fun firstQueryOccurrenceWins() {
        val transfer = EthereumTransferLink.parse("ethereum:$recipient?value=1&value=2")!!
        assertEquals(BigInteger.ONE, transfer.amount)
    }
}
