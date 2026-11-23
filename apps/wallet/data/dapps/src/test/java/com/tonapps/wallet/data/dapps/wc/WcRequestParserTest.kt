package com.tonapps.wallet.data.dapps.wc

import android.net.Uri
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.ConfirmAction
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wc.chains.ethereum.WcEthTransaction
import com.tonapps.wc.models.WcAppData
import com.tonapps.wc.models.WcDappConnection
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * An EVM transaction a dapp asks for becomes the operation it reads as: chainkit signs a payload as
 * it is, so the calldata stays and the dapp's gas is left for the wallet to price.
 */
class WcRequestParserTest : BaseUnitTest() {

    private val parser = WcRequestParser()

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk { every { host } returns "app.uniswap.org" }
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun aTokenTransferIsTheTokenContractsCall() {
        val request = parse(WcEthTransaction(from = SENDER, to = USDT_CONTRACT, data = TRANSFER_CALLDATA, value = "0x0", isSend = true))

        val type = request.type as ConfirmType.Transfer
        val meta = type.operation.payloads.single().meta as PayloadMeta.Evm.Main
        assertEquals(USDT_CONTRACT.lowercase(), meta.to.display.lowercase())
        assertEquals(BigInteger.ZERO, meta.value)
        assertEquals(TRANSFER_CALLDATA, meta.data)
        assertEquals(RECIPIENT.lowercase(), type.tokenTransfer?.to?.lowercase())
        assertEquals(BigInteger.fromInt(1_000_000), type.tokenTransfer?.amount)
        assertEquals("eth/mainnet/erc20/${USDT_CONTRACT.lowercase()}", request.data.assetId)
    }

    @Test
    fun aTokenTransferThatAlsoMovesTheCoinIsACall() {
        val request = parse(WcEthTransaction(from = SENDER, to = USDT_CONTRACT, data = TRANSFER_CALLDATA, value = "0xde0b6b3a7640000", isSend = true))

        val type = request.type as ConfirmType.Call
        val meta = type.operation.payloads.single().meta as PayloadMeta.Evm.Main
        assertEquals(USDT_CONTRACT.lowercase(), meta.to.display.lowercase())
        assertEquals(BigInteger.parseString("1000000000000000000"), meta.value)
        assertEquals(TRANSFER_CALLDATA, meta.data)
        assertEquals(Chain.Ethereum.Mainnet.coinAssetId, request.data.assetId)
    }

    @Test
    fun aTransactionWithoutCalldataIsACoinTransfer() {
        val request = parse(WcEthTransaction(from = SENDER, to = RECIPIENT, data = "0x", value = "0xde0b6b3a7640000", isSend = true))

        val type = request.type as ConfirmType.Transfer
        val meta = type.operation.payloads.single().meta as PayloadMeta.Evm.Main
        assertEquals(RECIPIENT.lowercase(), meta.to.display.lowercase())
        assertEquals(BigInteger.parseString("1000000000000000000"), meta.value)
        assertNull(meta.data)
        assertNull(type.tokenTransfer)
        assertEquals(Chain.Ethereum.Mainnet.coinAssetId, request.data.assetId)
    }

    @Test
    fun theWalletPricesTheTransactionWhateverGasTheDappNames() {
        val request = parse(
            WcEthTransaction(
                from = SENDER,
                to = RECIPIENT,
                data = "0xd0e30db0",
                value = "0x0",
                gasLimit = "0x5208",
                gasPrice = "0x3b9aca00",
                isSend = true,
            ),
        )

        val payload = (request.type as ConfirmType.Call).operation.payloads.single()
        assertNull(payload.fee)
        assertNull((payload.meta as PayloadMeta.Evm.Main).gas)
        assertEquals("0xd0e30db0", (payload.meta as PayloadMeta.Evm.Main).data)
    }

    @Test
    fun aTransactionToSignOnlyIsNotSent() {
        val request = parse(WcEthTransaction(from = SENDER, to = RECIPIENT, data = "0xd0e30db0", isSend = false))

        assertEquals(ConfirmAction.Sign, request.action)
    }

    private fun parse(transaction: WcEthTransaction): ConfirmRequest {
        return parser.convertRequest(
            requestId = "request-1",
            walletId = "wallet-1",
            chain = Chain.Ethereum.Mainnet,
            meta = WcDappConnection(topic = "topic-1", app = WcAppData(name = "Uniswap", url = "https://app.uniswap.org")),
            rawRequest = transaction,
        )!!
    }

    private companion object {
        const val SENDER = "0x0eE3CdEE4f010BF6c49aF86E9e67ef2bc80a508b"
        const val USDT_CONTRACT = "0xdAC17F958D2ee523a2206206994597C13D831ec7"
        const val RECIPIENT = "0x3B4D794a66304F130a4Db8F2551B0070dfCf5ca7"
        const val TRANSFER_CALLDATA = "0xa9059cbb" +
            "0000000000000000000000003b4d794a66304f130a4db8f2551b0070dfcf5ca7" +
            "00000000000000000000000000000000000000000000000000000000000f4240"
    }
}
