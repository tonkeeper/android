package com.tonapps.blockchain.ton.contract

import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.toAccountId
import io.ktor.util.hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.ton.cell.buildCell
import org.ton.kotlin.crypto.PrivateKeyEd25519

// Expected values are produced by tonkeeper/tongo (wallet/wallet_tg.go) for the same key and messages.
class WalletTgContractTest {

    private val publicKey = PrivateKeyEd25519(ByteArray(32) { it.toByte() }).publicKey()
    private val mainnet = WalletTgContract(publicKey, TonNetwork.MAINNET)

    @Test
    fun publicKeyMatchesTheReference() {
        assertEquals("03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8", hex(publicKey.key.toByteArray()))
    }

    @Test
    fun addressMatchesTheReference() {
        assertEquals(
            "0:73212ffaac1c4dbb4795cd869b92f0ebe4bfde95cf9f45e2299ba8438ebed6e2",
            mainnet.address.toAccountId()
        )
        assertEquals(
            "0:f7bb3e5b3c160ac6380f7dceb3a03e2c6493e80c566abfd2f1ac9647f70ae2b6",
            WalletTgContract(publicKey, TonNetwork.TESTNET).address.toAccountId()
        )
    }

    @Test
    fun versionIsResolvedOnlyForTheKeyTheAddressIsDerivedFrom() {
        val accountId = mainnet.address.toAccountId()
        val rotatedKey = PrivateKeyEd25519(ByteArray(32) { 1 }).publicKey()

        assertEquals(WalletVersion.WALLET_TG, BaseWalletContract.resolveVersion(publicKey, accountId, TonNetwork.MAINNET))
        assertEquals(WalletVersion.UNKNOWN, BaseWalletContract.resolveVersion(rotatedKey, accountId, TonNetwork.MAINNET))
    }

    @Test
    fun codeIsTheTrampoline() {
        assertEquals("9149ae51c1e4689710cebf7830297b16acfbadb363a920a537893e7ffeeca768", hex(mainnet.getCode().hash().toByteArray()))
    }

    @Test
    fun singleMessageRequestMatchesTheReference() {
        assertEquals("2aa89f2b5e4b1ff362d13b97f98241792724fbadb3d54ea4f67ef7a499516120", requestHash(1, internal = false))
        assertEquals("3da84b84e03b24813b1b7c1ecfd37e9a78b571a82ca21fbfdbe801637ceedf23", requestHash(1, internal = true))
    }

    @Test
    fun bulkRequestMatchesTheReference() {
        assertEquals("2d8cfa03dac438cbfd02386c709a04d10eb16343ab98ad083cc9fb8728978417", requestHash(2, internal = false))
        assertEquals("91ddd1795a62867fb3561c377cec3decac102aeb4a5a901e71945b9781572141", requestHash(4, internal = false))
        assertEquals("00f698862caadf5edee1a17732c8cbd7b2754cd4773d0732f61621a99f000837", requestHash(5, internal = false))
        assertEquals("47cdafe6b842d75753d9dfd2d16f61b55e7fba14a9f8bcd64744814394af2751", requestHash(8, internal = false))
        assertEquals("e65980e09b79e9880a47d9c116775388d5ad20736edfcffddd1b02d463605338", requestHash(255, internal = false))
        assertEquals("b130f12e7f3758031adfa1dff055d0c050accf59e4d7b370d1e0fbc3f5d5a859", requestHash(5, internal = true))
    }

    @Test
    fun rejectsRequestsTheContractWouldFail() {
        assertThrows(IllegalArgumentException::class.java) { request(0, internal = false) }
        assertThrows(IllegalArgumentException::class.java) { request(256, internal = false) }
        assertThrows(IllegalArgumentException::class.java) { request(1, internal = false, sendMode = 1) }
    }

    private fun requestHash(count: Int, internal: Boolean): String =
        hex(request(count, internal).hash().toByteArray())

    private fun request(count: Int, internal: Boolean, sendMode: Int = 3) = mainnet.createRequestBody(
        validUntil = 1700000000,
        seqNo = 7,
        internalMessage = internal,
        messages = List(count) { i ->
            WalletTgContract.MessageToSend(sendMode, buildCell { storeUInt(i, 32) })
        }
    )
}