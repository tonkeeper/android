package com.tonapps.wallet.data.ledger

import com.tonapps.wallet.data.ledger.signing.EvmTokenCall
import com.tonapps.wallet.data.ledger.signing.EvmTokenDescriptor
import com.trustwallet.core.ethereum.SigningInput
import com.trustwallet.core.ethereum.Transaction
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class EvmTokenDescriptorTest {

    private val usdt = EvmTokenCall(1, USDT)

    @Test
    fun `payload is the ticker length, the signed data and the prod signature`() {
        val payload = requireNotNull(EvmTokenDescriptor.payload(body(), usdt))
        assertEquals("04$USDT_DATA$USDT_PROD_SIGNATURE", payload.hex())
    }

    @Test
    fun `prod signature is made by the key the Ethereum app trusts`() {
        val curve = CustomNamedCurves.getByName("secp256k1")
        val domain = ECDomainParameters(curve.curve, curve.g, curve.n, curve.h)
        val publicKey = ECPublicKeyParameters(curve.curve.decodePoint(CAL_PUBLIC_KEY.decodeHex().toByteArray()), domain)
        val signature = ASN1Sequence.getInstance(USDT_PROD_SIGNATURE.decodeHex().toByteArray())
        val verifier = ECDSASigner().apply { init(false, publicKey) }
        val digest = MessageDigest.getInstance("SHA-256").digest(USDT_DATA.decodeHex().toByteArray())
        assertTrue(
            verifier.verifySignature(
                digest,
                (signature.getObjectAt(0) as ASN1Integer).positiveValue,
                (signature.getObjectAt(1) as ASN1Integer).positiveValue,
            ),
        )
    }

    @Test
    fun `descriptor of another contract is ignored`() {
        assertNull(EvmTokenDescriptor.payload(body(), EvmTokenCall(1, "0x833589fcd6edb6e08f4c7c32d4f71b54bda02913")))
    }

    @Test
    fun `descriptor of another chain is ignored`() {
        assertNull(EvmTokenDescriptor.payload(body(), EvmTokenCall(56, USDT)))
    }

    @Test
    fun `unknown token gives no payload`() {
        assertNull(EvmTokenDescriptor.payload("[]", usdt))
    }

    @Test
    fun `descriptor without a prod signature gives no payload`() {
        val body = """[{"descriptor":{"data":"$USDT_DATA","signatures":{"test":"$USDT_PROD_SIGNATURE"}}}]"""
        assertNull(EvmTokenDescriptor.payload(body, usdt))
    }

    @Test
    fun `erc20 transfer, approve and their generic calls are token calls`() {
        val transfer = Transaction(erc20_transfer = Transaction.ERC20Transfer(to = RECIPIENT, amount = "01".decodeHex()))
        val approve = Transaction(erc20_approve = Transaction.ERC20Approve(spender = RECIPIENT, amount = "01".decodeHex()))
        val genericTransfer = Transaction(contract_generic = Transaction.ContractGeneric(data_ = "a9059cbb00".decodeHex()))
        val genericApprove = Transaction(contract_generic = Transaction.ContractGeneric(data_ = "095ea7b300".decodeHex()))
        for (transaction in listOf(transfer, approve, genericTransfer, genericApprove)) {
            val call = assertNotNullCall(input(8453, USDT_CHECKSUM, transaction))
            assertEquals(8453L, call.chainId)
            assertEquals(USDT, call.contract)
        }
    }

    @Test
    fun `native transfers and other contract calls are not token calls`() {
        val native = Transaction(transfer = Transaction.Transfer(amount = "01".decodeHex()))
        val swap = Transaction(contract_generic = Transaction.ContractGeneric(data_ = "12aa3caf00".decodeHex()))
        assertNull(EvmTokenCall.from(input(1, USDT_CHECKSUM, native)))
        assertNull(EvmTokenCall.from(input(1, USDT_CHECKSUM, swap)))
    }

    private fun assertNotNullCall(input: ByteArray): EvmTokenCall {
        val call = EvmTokenCall.from(input)
        assertNotNull(call)
        return requireNotNull(call)
    }

    private fun input(chainId: Long, to: String, transaction: Transaction): ByteArray = SigningInput(
        chain_id = ByteString.of(*java.math.BigInteger.valueOf(chainId).toByteArray().dropWhile { it == 0.toByte() }.toByteArray()),
        to_address = to,
        transaction = transaction,
    ).encode()

    private fun body(): String =
        """[{"decimals":6,"ticker":"USDT","descriptor":{"data":"$USDT_DATA","descriptorType":"token","signatures":{"prod":"$USDT_PROD_SIGNATURE","test":"$USDT_TEST_SIGNATURE"}},"live_signature":"$USDT_PROD_SIGNATURE"}]"""

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val USDT = "0xdac17f958d2ee523a2206206994597c13d831ec7"
        const val USDT_CHECKSUM = "0xdAC17F958D2ee523a2206206994597C13D831ec7"
        const val RECIPIENT = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"
        const val USDT_DATA = "55534454dac17f958d2ee523a2206206994597c13d831ec70000000600000001"
        const val USDT_PROD_SIGNATURE = "3044022078c66ccea3e4dedb15a24ec3c783d7b582cd260daf62fd36afe9a8212a344aed0220160ba8c1c4b6a8aa6565bed20632a091aeeeb7bfdac67fc6589a6031acbf511c"
        const val USDT_TEST_SIGNATURE = "30450221009cd0cbe320ae84de461bc0f8f33cab3b7a546815105d00f9b4009e87c23e05f402207539a440ecb0709cd06853447f04c54533d5d5f9fcf60b6fe25c88263237bdff"
        const val CAL_PUBLIC_KEY = "045e6c1020c14dc46442fe89f97c0b68cdb15976dc24f24c316e7b30fe4e8cc76b1489150c21514ebf440ff5dea5393d83de5358cd098fce8fd0f81daa94979183"
    }
}
