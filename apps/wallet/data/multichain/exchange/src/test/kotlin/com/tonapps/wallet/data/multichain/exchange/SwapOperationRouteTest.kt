package com.tonapps.wallet.data.multichain.exchange

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import io.exchangeapi.models.CrossSwapCalldataPayloadType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SwapOperationRouteTest : BaseUnitTest() {

    @Test
    fun aSolanaPrebuiltSwapIsSignedAsTheTransactionTheRouteBuilt() {
        val route = prepared(payload(Chain.Solana.Mainnet, PayloadMeta.Solana.Prebuild(SOLANA_TX), Payload.Type.Prebuild))
            .toSwapRoute(account(Chain.Solana.Mainnet, SOLANA_SENDER), SPEND)

        val message = route.mainMessages.single()
        assertEquals(SOLANA_TX, message.data)
        assertEquals(SPEND, message.amount)
        assertEquals(SOLANA_SENDER, message.to)
        assertEquals(CrossSwapCalldataPayloadType.exact, message.calldataType)
        assertNull(route.approval)
        assertEquals(PROVIDER_ROUTE_ID, route.providerRouteId)
        assertEquals(BUY, route.buyBaseAmount)
        assertEquals(MIN_BUY, route.minimumBuyBaseAmount)
    }

    @Test
    fun aPrebuiltSwapGoesToTheDepositAddressTheRouteNames() {
        val route = prepared(payload(Chain.Tron.Mainnet, PayloadMeta.Tron.Prebuild(TRON_TX), Payload.Type.Prebuild), depositAddress = TRON_DEPOSIT)
            .toSwapRoute(account(Chain.Tron.Mainnet, TRON_SENDER), SPEND)

        val message = route.mainMessages.single()
        assertEquals(TRON_DEPOSIT, message.to)
        assertEquals(TRON_TX, message.data)
    }

    @Test
    fun anEvmApproveGoesAheadOfTheRouterCall() {
        val fee = Fee.Value(Chain.Ethereum.Mainnet.toAsset(), BigInteger.TEN)
        val route = prepared(
            payload(Chain.Ethereum.Mainnet, evmCall(USDT_ETH, APPROVE_CALL), fee = fee),
            payload(Chain.Ethereum.Mainnet, evmCall(ROUTER, SWAP_CALL), id = "1"),
        ).toSwapRoute(account(Chain.Ethereum.Mainnet, EVM_SENDER), SPEND)

        val approval = requireNotNull(route.approval)
        assertEquals(USDT_ETH, approval.to.lowercase())
        assertEquals(APPROVE_CALL, approval.data)
        assertEquals(fee, approval.fee)
        val swap = route.mainMessages.single()
        assertEquals(ROUTER, swap.to.lowercase())
        assertEquals(SWAP_CALL, swap.data)
        assertEquals(BigInteger.ZERO, swap.amount)
    }

    @Test
    fun aCallAheadOfTheSwapThatIsNoApproveRefusesTheRoute() {
        val operation = prepared(
            payload(Chain.Ethereum.Mainnet, evmCall(ROUTER, SWAP_CALL)),
            payload(Chain.Ethereum.Mainnet, evmCall(ROUTER, SWAP_CALL), id = "1"),
        )

        assertThrows(SwapRouteRefusedException::class.java) {
            operation.toSwapRoute(account(Chain.Ethereum.Mainnet, EVM_SENDER), SPEND)
        }
    }

    @Test
    fun anEvmCallUnderAnAuthorizationRefusesTheRoute() {
        val authorization = PayloadMeta.Evm.Authorization(chainId = 1, address = Address.force(ROUTER, Chain.Ethereum.Mainnet), nonce = BigInteger.ONE)
        val meta = PayloadMeta.Evm.Main(to = Address.force(ROUTER, Chain.Ethereum.Mainnet), value = BigInteger.ZERO, data = SWAP_CALL, authorization = authorization)
        val operation = prepared(payload(Chain.Ethereum.Mainnet, meta))

        assertThrows(SwapRouteRefusedException::class.java) {
            operation.toSwapRoute(account(Chain.Ethereum.Mainnet, EVM_SENDER), SPEND)
        }
    }

    @Test
    fun aTonBatchOfOneMessageIsSentAsThatMessage() {
        val batch = PayloadMeta.Ton.Batch(walletVersion = "v5r1", calls = listOf(tonMessage(BigInteger.parseString("1000000000"))))
        val route = prepared(payload(Chain.Ton.Mainnet, batch, Payload.Type.Batch))
            .toSwapRoute(account(Chain.Ton.Mainnet, TON_SENDER), SPEND)

        val message = route.mainMessages.single()
        assertEquals("1000000000", message.amount.toString())
        assertEquals(TON_BODY, message.data)
        assertEquals(3, message.mode)
    }

    @Test
    fun aTonRouteOfSeveralMessagesRefusesTheRoute() {
        val batch = PayloadMeta.Ton.Batch(
            walletVersion = "v5r1",
            calls = listOf(tonMessage(BigInteger.parseString("1000000000")), tonMessage(BigInteger.parseString("2000000000"))),
        )
        val operation = prepared(payload(Chain.Ton.Mainnet, batch, Payload.Type.Batch))

        assertThrows(SwapRouteRefusedException::class.java) {
            operation.toSwapRoute(account(Chain.Ton.Mainnet, TON_SENDER), SPEND)
        }
    }

    @Test
    fun aTonMessageWithAStateInitRefusesTheRoute() {
        val meta = PayloadMeta.Ton.Main(
            to = Address.force(TON_ROUTER, Chain.Ton.Mainnet),
            value = BigInteger.ONE,
            body = TON_BODY,
            stateInit = TON_BODY,
        )
        val operation = prepared(payload(Chain.Ton.Mainnet, meta))

        assertThrows(SwapRouteRefusedException::class.java) {
            operation.toSwapRoute(account(Chain.Ton.Mainnet, TON_SENDER), SPEND)
        }
    }

    @Test
    fun aTonJettonDepositIsSentFromTheJettonWalletToTheDepositAddress() {
        val route = prepared(payload(Chain.Ton.Mainnet, jettonDeposit()))
            .toSwapRoute(usdtTonAccount(), JETTON_SPEND)

        val message = route.mainMessages.single()
        assertEquals(TON_DEPOSIT, message.to)
        assertEquals(JETTON_SPEND, message.amount)
        assertNull(message.data)
        assertNull(message.memo)
        assertNull(route.approval)
    }

    @Test
    fun aJettonDepositOfAnotherAssetRefusesTheRoute() {
        val operation = prepared(payload(Chain.Ton.Mainnet, jettonDeposit()))

        assertThrows(SwapRouteRefusedException::class.java) {
            operation.toSwapRoute(account(Chain.Ton.Mainnet, TON_SENDER), JETTON_SPEND)
        }
    }

    @Test
    fun aJettonDepositWithACommentRefusesTheRoute() {
        val operation = prepared(payload(Chain.Ton.Mainnet, jettonDeposit().copy(comment = "q_01")))

        assertThrows(SwapRouteRefusedException::class.java) {
            operation.toSwapRoute(usdtTonAccount(), JETTON_SPEND)
        }
    }

    @Test
    fun aMessageToSignRefusesTheRoute() {
        val operation = prepared(payload(Chain.Ethereum.Mainnet, PayloadMeta.Evm.PlainMessage("hello"), Payload.Type.MessagePlain))

        assertThrows(SwapRouteRefusedException::class.java) {
            operation.toSwapRoute(account(Chain.Ethereum.Mainnet, EVM_SENDER), SPEND)
        }
    }

    private fun account(chain: Chain, address: String, asset: Asset = chain.toAsset()) =
        Account.watch(Address.force(address, chain), asset)

    private fun usdtTonAccount() = account(Chain.Ton.Mainnet, TON_SENDER, TestAssets.usdtTon.value)

    private fun jettonDeposit() = PayloadMeta.Ton.Main(
        to = Address.force(TON_DEPOSIT, Chain.Ton.Mainnet),
        value = JETTON_SPEND,
        token = Address.force(USDT_TON_MASTER, Chain.Ton.Mainnet),
    )

    private fun tonMessage(value: BigInteger) = PayloadMeta.Ton.Main(
        to = Address.force(TON_ROUTER, Chain.Ton.Mainnet),
        value = value,
        body = TON_BODY,
        mode = 3,
    )

    private fun evmCall(to: String, data: String) = PayloadMeta.Evm.Main(
        to = Address.force(to, Chain.Ethereum.Mainnet),
        value = BigInteger.ZERO,
        data = data,
    )

    private fun payload(
        chain: Chain,
        meta: PayloadMeta,
        type: Payload.Type = Payload.Type.Main,
        id: String = "0",
        fee: Fee? = null,
    ) = Payload(id = id, type = type, chain = chain, version = Payload.VERSION, fee = fee, meta = meta)

    private fun prepared(vararg payloads: Payload, depositAddress: String? = null) = PreparedSwapOperation(
        operation = Operation(id = "operation", payloads = payloads.toList(), meta = ""),
        providerRouteId = PROVIDER_ROUTE_ID,
        buyBaseAmount = BUY,
        minimumBuyBaseAmount = MIN_BUY,
        depositAddress = depositAddress,
        expiresAtMillis = 0L,
    )

    private companion object {
        val SPEND: BigInteger = BigInteger.parseString("100000000")
        val BUY: BigInteger = BigInteger.parseString("8233429")
        val MIN_BUY: BigInteger = BigInteger.parseString("8151094")
        const val PROVIDER_ROUTE_ID = "0x8c5b57041f38192bdfd245bf31051a482ac3b5447cc427c7a45daab01f1e04d1"
        const val SOLANA_SENDER = "A7APrxHwRFnjiuGrYfD9T5qXr7tHGNZoquzuni6S1izt"
        const val SOLANA_TX = "AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACAAQAHDg=="
        const val TRON_SENDER = "TUumguwaamEopV8QqbTjmDrCp3K2TLsyvD"
        const val TRON_DEPOSIT = "TRfhcmuwsUmGBsJDQGoxJHHy1s4z6ti8yM"
        const val TRON_TX = "{\"txID\":\"abc\",\"raw_data_hex\":\"0a02\"}"
        const val EVM_SENDER = "0x7802b6179c9fd58f058a946f1d1acce5a697c498"
        const val USDT_ETH = "0xdac17f958d2ee523a2206206994597c13d831ec7"
        const val ROUTER = "0x1b6257cae4192e62b629efca21771be3d759183d"
        const val APPROVE_CALL = "0x095ea7b30000000000000000000000001b6257cae4192e62b629efca21771be3d759183d0000000000000000000000000000000000000000000000000000000000989680"
        const val SWAP_CALL = "0x12345678"
        const val TON_SENDER = "UQCiJG2EX7-MZEp-v6gLR9x07fa73VkwKjqtHd9jb3bYrMh3"
        const val TON_ROUTER = "EQAH4MY1EbqP7Gg2wJkoZvoCG4BYPsEG5lVBXPZn0UqXNz4y"
        const val TON_BODY = "te6cckEBAQEAAgAAAEysuc0="
        const val TON_DEPOSIT = "UQD4FAlGPqsCkEdL_MK0UhQToDhRNSGdaO6mP4yoZbKNTXp0"
        const val USDT_TON_MASTER = "0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe"
        val JETTON_SPEND: BigInteger = BigInteger.parseString("5000000")
    }
}
