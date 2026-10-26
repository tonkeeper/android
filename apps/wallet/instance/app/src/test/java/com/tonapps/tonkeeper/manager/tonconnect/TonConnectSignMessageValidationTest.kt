package com.tonapps.tonkeeper.manager.tonconnect

import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.ton.block.AddrStd

class TonConnectSignMessageValidationTest {

    private val now = 1_800_000_000L
    private val walletAccountId = "0:0000000000000000000000000000000000000000000000000000000000000001"
    private val friendlyRecipient = "Ef8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADAU"
    private val rawRecipient = "-1:0000000000000000000000000000000000000000000000000000000000000000"
    private val otherAccountId = "-1:0000000000000000000000000000000000000000000000000000000000000000"

    @Test
    fun acceptsRequestAddressedToConnectedWallet() {
        assertNull(validate(input(from = walletAccountId, network = -239, validUntil = now + 60)))
    }

    @Test
    fun acceptsRequestWithoutOptionalFields() {
        assertNull(validate(input()))
    }

    @Test
    fun rejectsWhenFeatureIsDisabled() {
        assertEquals(TonConnectSignMessageError.NotSupported, validate(input(featureEnabled = false)))
    }

    @Test
    fun rejectsWalletWithoutInternalSignedMessages() {
        assertEquals(TonConnectSignMessageError.NotSupported, validate(input(walletSupported = false)))
    }

    @Test
    fun rejectsExpiredRequest() {
        assertEquals(TonConnectSignMessageError.Expired, validate(input(validUntil = now)))
    }

    @Test
    fun rejectsRequestForAnotherNetwork() {
        assertEquals(TonConnectSignMessageError.NetworkMismatch, validate(input(network = -3)))
    }

    @Test
    fun rejectsRequestFromAnotherWallet() {
        assertEquals(TonConnectSignMessageError.AddressMismatch, validate(input(from = otherAccountId)))
    }

    /** A `from` that does not parse is a mismatch, not a crash on the HTTP bridge. */
    @Test
    fun rejectsUnparsableFromAsAnotherWallet() {
        listOf("abc", "0:zz", "EQ-not-an-address").forEach { value ->
            val from = signMessageFromAccountId { AddrStd.parse(value) }
            assertEquals(value, TonConnectSignMessageError.AddressMismatch, validate(input(from = from)))
        }
    }

    @Test
    fun readsTheAccountAParsableFromNames() {
        assertEquals(walletAccountId, signMessageFromAccountId { AddrStd.parse(walletAccountId) })
        assertNull(signMessageFromAccountId { null })
    }

    @Test
    fun rejectsRawDestinationAddress() {
        assertEquals(TonConnectSignMessageError.InvalidMessages, validate(input(addresses = listOf(rawRecipient))))
    }

    @Test
    fun rejectsEmptyMessages() {
        assertEquals(TonConnectSignMessageError.InvalidMessages, validate(input(addresses = emptyList())))
    }

    @Test
    fun rejectsMoreMessagesThanWalletAccepts() {
        val addresses = List(256) { friendlyRecipient }
        assertEquals(TonConnectSignMessageError.InvalidMessages, validate(input(addresses = addresses)))
    }

    @Test
    fun checksSupportBeforeMessages() {
        assertEquals(TonConnectSignMessageError.NotSupported, validate(input(featureEnabled = false, addresses = emptyList())))
    }

    @Test
    fun mapsRejectionsToTonConnectErrorCodes() {
        assertEquals(BridgeError.METHOD_NOT_SUPPORTED, TonConnectSignMessageError.NotSupported.bridgeError.code)
        assertEquals(BridgeError.BAD_REQUEST, TonConnectSignMessageError.InvalidMessages.bridgeError.code)
        assertEquals(BridgeError.BAD_REQUEST, TonConnectSignMessageError.Expired.bridgeError.code)
        assertEquals(BridgeError.BAD_REQUEST, TonConnectSignMessageError.NetworkMismatch.bridgeError.code)
        assertEquals(BridgeError.BAD_REQUEST, TonConnectSignMessageError.AddressMismatch.bridgeError.code)
    }

    private fun validate(input: TonConnectSignMessageValidation.Input): TonConnectSignMessageError? {
        return TonConnectSignMessageValidation.validate(input, now)
    }

    private fun input(
        featureEnabled: Boolean = true,
        walletSupported: Boolean = true,
        addresses: List<String> = listOf(friendlyRecipient),
        from: String? = null,
        network: Int? = null,
        validUntil: Long = 0,
    ) = TonConnectSignMessageValidation.Input(
        isFeatureEnabled = featureEnabled,
        isWalletSupported = walletSupported,
        walletMaxMessages = 255,
        walletAccountId = walletAccountId,
        walletNetworkValue = -239,
        messageAddresses = addresses,
        validUntil = validUntil,
        requestedNetworkValue = network,
        fromAccountId = from,
    )
}
