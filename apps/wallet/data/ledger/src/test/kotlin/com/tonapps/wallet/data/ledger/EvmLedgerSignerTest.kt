package com.tonapps.wallet.data.ledger

import com.tonapps.bus.core.IssueHelper
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.core
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.apdu.ApduResponse
import com.tonapps.wallet.data.ledger.signing.EvmLedgerSigner
import com.trustwallet.core.AnySigner
import com.trustwallet.core.WalletCoreLibLoader
import com.trustwallet.core.ethereum.SigningInput
import com.trustwallet.core.ethereum.SigningOutput
import com.trustwallet.core.ethereum.Transaction
import com.trustwallet.core.ethereum.TransactionMode
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Hash
import org.web3j.crypto.Sign
import java.math.BigInteger

class EvmLedgerSignerTest {

    init {
        WalletCoreLibLoader.loadLibrary()
    }

    private val privateKey = ByteArray(32) { (it + 7).toByte() }
    private val keyPair = ECKeyPair.create(BigInteger(1, privateKey))

    private val recorded = ArrayList<Throwable>()

    @Before
    fun setUp() {
        mockkObject(IssueHelper)
        every { IssueHelper.recordException(any(), any<Map<String, String>>()) } answers { recorded += firstArg<Throwable>() }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `eip1559 transfer compiled from a ledger signature equals the software signature`() = runTest {
        assertMatchesSoftware(
            chain = Chain.Base.Mainnet,
            input = input(8453, TransactionMode.Enveloped, transfer(ByteArray(0))),
        )
    }

    @Test
    fun `legacy bsc transfer compiled from a ledger signature equals the software signature`() = runTest {
        assertMatchesSoftware(
            chain = Chain.Smartchain.Mainnet,
            input = input(56, TransactionMode.Legacy, transfer(ByteArray(0))),
        )
    }

    @Test
    fun `legacy contract call with chunk boundaries equals the software signature`() = runTest {
        for (dataLength in listOf(100, 200, 214, 216, 217, 300, 470, 471, 700)) {
            assertMatchesSoftware(
                chain = Chain.Smartchain.Mainnet,
                input = input(56, TransactionMode.Legacy, contract(ByteArray(dataLength) { (it * 3).toByte() })),
            )
        }
    }

    @Test
    fun `arbitrum contract call equals the software signature`() = runTest {
        assertMatchesSoftware(
            chain = Chain.Arbitrum.Mainnet,
            input = input(42161, TransactionMode.Enveloped, contract(ByteArray(68) { it.toByte() })),
        )
    }

    @Test
    fun `token descriptor is sent before the transaction`() = runTest {
        val descriptor = ByteArray(40) { (it + 1).toByte() }
        val device = FakeEthereumDevice(8453)
        assertMatchesSoftware(
            chain = Chain.Base.Mainnet,
            input = input(8453, TransactionMode.Enveloped, contract(ByteArray(68) { it.toByte() })),
            tokenDescriptor = descriptor,
            device = device,
        )
        assertEquals(listOf(0x0a, 0x04), device.instructions.distinct())
        assertArrayEquals(descriptor, device.tokenInformation)
    }

    @Test
    fun `a rejected token descriptor does not block signing`() = runTest {
        val device = FakeEthereumDevice(8453, tokenInformationStatus = 0x6a80)
        assertMatchesSoftware(
            chain = Chain.Base.Mainnet,
            input = input(8453, TransactionMode.Enveloped, contract(ByteArray(68) { it.toByte() })),
            tokenDescriptor = ByteArray(40),
            device = device,
        )
        assertEquals(listOf("The Ethereum app rejected an ERC-20 descriptor"), recorded.map { it.message })
    }

    @Test
    fun `a locked device while sending the token descriptor stops signing`() = runTest {
        val device = FakeEthereumDevice(8453, tokenInformationStatus = 0x5515)
        val failure = try {
            assertMatchesSoftware(
                chain = Chain.Base.Mainnet,
                input = input(8453, TransactionMode.Enveloped, contract(ByteArray(68) { it.toByte() })),
                tokenDescriptor = ByteArray(40),
                device = device,
            )
            null
        } catch (e: LedgerFailureException) {
            e.failure
        }
        assertEquals(LedgerFailure.DeviceLocked, failure)
        assertEquals(listOf(0x0a), device.instructions)
    }

    private suspend fun assertMatchesSoftware(
        chain: Chain,
        input: SigningInput,
        tokenDescriptor: ByteArray? = null,
        device: FakeEthereumDevice = FakeEthereumDevice(BigInteger(1, input.chain_id.toByteArray()).toLong()),
    ) {
        val transport = FakeLedgerTransport("Ethereum", handler = device::handle)
        val compiled = EvmLedgerSigner().signTransaction(
            scope = transport.scope(),
            path = LedgerDerivation.ethereum(0),
            chain = chain,
            input = input.encode(),
            tokenDescriptor = tokenDescriptor,
            step = 1,
            totalSteps = 1,
        )
        val ledger = SigningOutput.ADAPTER.decode(compiled)
        val software = SigningOutput.ADAPTER.decode(
            AnySigner.sign(input.copy(private_key = privateKey.toByteString()).encode(), chain.coin.core),
        )
        assertEquals(software.encoded.hex(), ledger.encoded.hex())
    }

    private fun input(chainId: Long, mode: TransactionMode, transaction: Transaction) = SigningInput(
        chain_id = BigInteger.valueOf(chainId).toByteArray().stripped(),
        nonce = BigInteger.valueOf(9).toByteArray().stripped(),
        tx_mode = mode,
        gas_price = if (mode == TransactionMode.Legacy) { BigInteger.valueOf(3_000_000_000).toByteArray().stripped() } else { ByteString.EMPTY },
        gas_limit = BigInteger.valueOf(210_000).toByteArray().stripped(),
        max_inclusion_fee_per_gas = if (mode == TransactionMode.Enveloped) { BigInteger.valueOf(1_000_000).toByteArray().stripped() } else { ByteString.EMPTY },
        max_fee_per_gas = if (mode == TransactionMode.Enveloped) { BigInteger.valueOf(20_000_000).toByteArray().stripped() } else { ByteString.EMPTY },
        to_address = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed",
        transaction = transaction,
    )

    private fun transfer(data: ByteArray) = Transaction(
        transfer = Transaction.Transfer(
            amount = BigInteger("1000000000000000").toByteArray().stripped(),
            data_ = data.toByteString(),
        ),
    )

    private fun contract(data: ByteArray) = Transaction(
        contract_generic = Transaction.ContractGeneric(amount = ByteString.EMPTY, data_ = data.toByteString()),
    )

    private fun ByteArray.stripped(): ByteString = (if (size > 1 && this[0] == 0.toByte()) { copyOfRange(1, size) } else { this }).toByteString()

    private inner class FakeEthereumDevice(
        private val chainId: Long,
        private val tokenInformationStatus: Int = 0x9000,
    ) {

        private var buffer = ByteArray(0)
        val instructions = mutableListOf<Int>()
        var tokenInformation: ByteArray? = null

        fun handle(apdu: ByteArray): ApduResponse {
            val ins = apdu[1].toInt() and 0xff
            val p1 = apdu[2].toInt() and 0xff
            if (ins == 0x02) {
                return ApduResponse(ByteArray(0), 0x6d00)
            }
            instructions += ins
            if (ins == 0x0a) {
                tokenInformation = apdu.copyOfRange(5, apdu.size)
                return ApduResponse(byteArrayOf(0), tokenInformationStatus)
            }
            require(ins == 0x04) { "Unexpected instruction" }
            val data = apdu.copyOfRange(5, apdu.size)
            buffer = if (p1 == 0x00) {
                val depth = data[0].toInt()
                data.copyOfRange(1 + depth * 4, data.size)
            } else {
                buffer + data
            }
            if (buffer.size < expectedLength(buffer)) {
                return ApduResponse(ByteArray(0), 0x9000)
            }
            val isLegacy = (buffer[0].toInt() and 0xff) >= 0xc0
            val signature = Sign.signMessage(Hash.sha3(buffer), keyPair, false)
            val parity = (signature.v[0].toInt() and 0xff) - 27
            val v = if (isLegacy) {
                ((chainId * 2 + 35 + parity) and 0xff).toInt()
            } else {
                parity
            }
            return ApduResponse(byteArrayOf(v.toByte()) + signature.r + signature.s, 0x9000)
        }

        private fun expectedLength(payload: ByteArray): Int {
            val typedOffset = if ((payload[0].toInt() and 0xff) < 0xc0) { 1 } else { 0 }
            if (payload.size <= typedOffset) {
                return Int.MAX_VALUE
            }
            val header = payload[typedOffset].toInt() and 0xff
            return if (header <= 0xf7) {
                typedOffset + 1 + (header - 0xc0)
            } else {
                val lengthOfLength = header - 0xf7
                if (payload.size < typedOffset + 1 + lengthOfLength) {
                    return Int.MAX_VALUE
                }
                var length = 0
                for (index in 0 until lengthOfLength) {
                    length = (length shl 8) or (payload[typedOffset + 1 + index].toInt() and 0xff)
                }
                typedOffset + 1 + lengthOfLength + length
            }
        }
    }
}
