package com.tonapps.tonkeeper.ui.screen.browser.dapp.evm

import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.wc.WcRouteAdapter
import com.tonapps.wc.chains.WcRequestData
import com.tonapps.wc.chains.ethereum.WcEthSignMessage
import com.tonapps.wc.chains.ethereum.WcEthTransaction
import com.tonapps.wc.models.WcConnection
import com.tonapps.wc.models.WcDappConnection
import com.tonapps.wc.models.WcSessionProposal
import com.tonapps.wc.models.WcValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvmMethodParserTest {

    private val adapter = CapturingAdapter()

    private val parser = EvmMethodParser(DAPP_NAME, adapter)

    @Test
    fun `gas becomes the gas limit`() {
        parse(mapOf("to" to TO, "gas" to "0x5208", "gasPrice" to "0x1"))

        assertEquals("0x5208", adapter.transaction().gasLimit)
        assertEquals("0x1", adapter.transaction().gasPrice)
    }

    @Test
    fun `walletconnect spelling of the gas limit still works`() {
        parse(mapOf("to" to TO, "gasLimit" to "0x5208"))

        assertEquals("0x5208", adapter.transaction().gasLimit)
    }

    @Test
    fun `sender and call data fall back to the wallet and an empty call`() {
        parse(mapOf("to" to TO))

        assertEquals(ADDRESS, adapter.transaction().from)
        assertEquals("0x", adapter.transaction().data)
        assertTrue(adapter.transaction().isSend)
    }

    @Test
    fun `a transaction without params is refused`() {
        val result = parser.transaction(request(emptyList()), WALLET, CHAIN, ADDRESS)

        assertEquals(EvmMethodParser.Result.Error(INVALID_PARAMS, "Missing transaction"), result)
    }

    @Test
    fun `personal sign passes the message before the address`() {
        parser.personalSign(request(listOf(MESSAGE)), WALLET, CHAIN, ADDRESS)

        val message = adapter.message()
        assertEquals(MESSAGE, message.data)
        assertEquals(ADDRESS, message.address)
        assertEquals(WcEthSignMessage.WcSignType.PersonalMessage, message.type)
    }

    @Test
    fun `typed data keeps the raw payload and passes the address first`() {
        val payload = typedData(chainId = "1")

        parser.typedData(request(listOf(ADDRESS, payload)), WALLET, CHAIN, ADDRESS)

        val message = adapter.message()
        assertEquals(payload, message.data)
        assertEquals(ADDRESS, message.address)
        assertEquals(WcEthSignMessage.WcSignType.TypedMessage, message.type)
    }

    @Test
    fun `typed data for another chain is refused`() {
        val result = parser.typedData(
            request(listOf(ADDRESS, typedData(chainId = "8453"))),
            WALLET,
            CHAIN,
            ADDRESS,
        )

        assertEquals(UNRECOGNIZED_CHAIN, (result as EvmMethodParser.Result.Error).code)
    }

    @Test
    fun `a hex chain id in the domain is understood`() {
        val result = parser.typedData(
            request(listOf(ADDRESS, typedData(chainId = "\"0x1\""))),
            WALLET,
            CHAIN,
            ADDRESS,
        )

        assertTrue(result is EvmMethodParser.Result.Confirm)
    }

    @Test
    fun `typed data without a payload is refused`() {
        val result = parser.typedData(request(listOf(ADDRESS)), WALLET, CHAIN, ADDRESS)

        assertEquals(EvmMethodParser.Result.Error(INVALID_PARAMS, "Missing typed data"), result)
    }

    @Test
    fun `a transaction from another account is refused`() {
        val result = parse(mapOf("to" to TO, "from" to OTHER))

        assertEquals(UNAUTHORIZED, (result as EvmMethodParser.Result.Error).code)
    }

    @Test
    fun `the sender is matched regardless of case`() {
        val result = parse(mapOf("to" to TO, "from" to ADDRESS.uppercase()))

        assertTrue(result is EvmMethodParser.Result.Confirm)
    }

    @Test
    fun `a transaction addressed to another chain is refused`() {
        val result = parse(mapOf("to" to TO, "chainId" to "0x2105"))

        assertEquals(UNRECOGNIZED_CHAIN, (result as EvmMethodParser.Result.Error).code)
    }

    @Test
    fun `personal sign for another account is refused`() {
        val result = parser.personalSign(request(listOf(MESSAGE, OTHER)), WALLET, CHAIN, ADDRESS)

        assertEquals(UNAUTHORIZED, (result as EvmMethodParser.Result.Error).code)
    }

    @Test
    fun `typed data is found whichever way round the params are`() {
        val payload = typedData(chainId = "1")

        parser.typedData(request(listOf(payload, ADDRESS)), WALLET, CHAIN, ADDRESS)

        assertEquals(payload, adapter.message().data)
    }

    @Test
    fun `typed data sent as an object is encoded back to json`() {
        val payload = mapOf(
            "domain" to mapOf("name" to "Test", "chainId" to 1),
            "message" to mapOf("amount" to 1),
        )

        val result = parser.typedData(request(listOf(ADDRESS, payload)), WALLET, CHAIN, ADDRESS)

        assertTrue(result is EvmMethodParser.Result.Confirm)
        assertTrue(adapter.message().data.contains("\"chainId\":1"))
    }

    @Test
    fun `typed data for another signer is refused`() {
        val result = parser.typedData(
            request(listOf(OTHER, typedData(chainId = "1"))),
            WALLET,
            CHAIN,
            ADDRESS,
        )

        assertEquals(UNAUTHORIZED, (result as EvmMethodParser.Result.Error).code)
    }

    @Test
    fun `the request is marked as coming from the injected provider`() {
        val result = parse(mapOf("to" to TO))

        val context = (result as EvmMethodParser.Result.Confirm).request.context
        assertEquals(ConfirmContext.Dapp.Source.Injected, (context as ConfirmContext.Dapp).source)
    }

    @Test
    fun `the confirmation is keyed by our own id, not by the page's`() {
        val parser = EvmMethodParser(DAPP_NAME, adapter) { "confirm-1" }

        val result = parser.transaction(request(listOf(mapOf("to" to TO))), WALLET, CHAIN, ADDRESS)

        val confirm = result as EvmMethodParser.Result.Confirm
        assertEquals("confirm-1", confirm.requestId)
        assertEquals("confirm-1", (confirm.request.context as ConfirmContext.Dapp).requestId)
    }

    private fun parse(params: Map<String, String>): EvmMethodParser.Result {
        return parser.transaction(request(listOf(params)), WALLET, CHAIN, ADDRESS)
    }

    private fun request(params: List<Any?>): EvmProviderProtocol.Message.Request {
        return EvmProviderProtocol.Message.Request(ID, "method", params)
    }

    private fun typedData(chainId: String): String {
        return """{"domain":{"name":"Test","chainId":$chainId},"message":{}}"""
    }

    private class CapturingAdapter : WcRouteAdapter {

        private var captured: WcRequestData? = null

        fun transaction(): WcEthTransaction = captured as WcEthTransaction

        fun message(): WcEthSignMessage = captured as WcEthSignMessage

        override fun <T : WcRequestData> convertRequest(
            requestId: String,
            walletId: String,
            chain: Chain,
            meta: WcDappConnection,
            rawRequest: T,
        ): ConfirmRequest {
            captured = rawRequest
            return ConfirmRequest(
                data = CommonTransactionData(assetId = "asset", walletId = walletId),
                type = ConfirmType.Message("0x", ConfirmType.Message.Type.Personal),
                context = ConfirmContext.Dapp(requestId, "topic", "name", "url"),
            )
        }

        override fun handleMultiSession(
            requestId: String,
            proposal: WcSessionProposal,
            wcConnection: WcConnection,
            validation: WcValidation,
        ) = throw UnsupportedOperationException()
    }

    private companion object {
        private const val ID = 7L
        private const val DAPP_NAME = "Earn"
        private const val WALLET = "wallet-id"
        private const val ADDRESS = "0x7ECebAF27c989adEF9EA3DD06D316F6F463a5109"
        private const val TO = "0x0000000000000000000000000000000000000001"
        private const val OTHER = "0x00000000000000000000000000000000000000ff"
        private const val MESSAGE = "0x68656c6c6f"

        private const val INVALID_PARAMS = -32602
        private const val UNAUTHORIZED = 4100
        private const val UNRECOGNIZED_CHAIN = 4902

        private val CHAIN = Chain.Ethereum.Mainnet
    }
}
