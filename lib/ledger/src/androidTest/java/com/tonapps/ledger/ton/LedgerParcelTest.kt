package com.tonapps.ledger.ton

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.core.os.BundleCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonapps.ledger.devices.DeviceModelId
import com.tonapps.ledger.devices.Devices
import com.tonapps.ledger.transport.Transport
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.StateInit
import org.ton.cell.CellBuilder
import org.ton.kotlin.crypto.PublicKeyEd25519
import java.math.BigInteger

@RunWith(AndroidJUnit4::class)
class LedgerParcelTest {

    private val publicKey = PublicKeyEd25519(ByteString(ByteArray(32) { it.toByte() }))
    private val accountPath = AccountPath(index = 3)
    private val contract = accountPath.contract(publicKey)
    private val walletAddress = contract.address
    private val masterchainAddress = AddrStd(-1, ByteArray(32) { (0xA0 + it).toByte() })
    private val cell = CellBuilder.createCell {
        storeUInt(0x0BADF00D, 32)
        storeRef(CellBuilder.createCell { storeUInt(7, 8) })
    }
    private val queryId = BigInteger("1234567890123")
    private val bytes32 = ByteArray(32) { (0x40 + it).toByte() }

    private val payloadsWithoutByteArrays: List<TonPayloadFormat?> = listOf(
        null,
        TonPayloadFormat.Comment("Ledger restore"),
        TonPayloadFormat.JettonTransfer(
            queryId = queryId,
            coins = Coins.ofNano(1_500_000),
            receiverAddress = walletAddress,
            excessesAddress = masterchainAddress,
            customPayload = cell,
            forwardAmount = Coins.ofNano(1),
            forwardPayload = cell,
            knownJetton = KnownJetton(jettonId = 0, workchain = 0),
        ),
        TonPayloadFormat.JettonTransfer(
            queryId = null,
            coins = Coins.ofNano(1),
            receiverAddress = walletAddress,
            excessesAddress = walletAddress,
            customPayload = null,
            forwardAmount = Coins.ofNano(0),
            forwardPayload = null,
        ),
        TonPayloadFormat.NftTransfer(
            queryId = queryId,
            newOwnerAddress = walletAddress,
            excessesAddress = masterchainAddress,
            customPayload = cell,
            forwardAmount = Coins.ofNano(1),
            forwardPayload = cell,
        ),
        TonPayloadFormat.NftTransfer(
            queryId = null,
            newOwnerAddress = walletAddress,
            excessesAddress = walletAddress,
            customPayload = null,
            forwardAmount = Coins.ofNano(0),
            forwardPayload = null,
        ),
        TonPayloadFormat.JettonBurn(queryId, Coins.ofNano(5), walletAddress, JettonBurnCustomPayload.CellPayload(cell)),
        TonPayloadFormat.JettonBurn(null, Coins.ofNano(5), walletAddress, null),
        TonPayloadFormat.SingleNominatorWithdraw(queryId, Coins.ofNano(10)),
        TonPayloadFormat.SingleNominatorChangeValidator(queryId, masterchainAddress),
        TonPayloadFormat.AddWhitelist(queryId, walletAddress),
        TonPayloadFormat.TonstakersDeposit(queryId, BigInteger.TEN),
        TonPayloadFormat.TonstakersDeposit(null, null),
        TonPayloadFormat.VoteForProposal(
            queryId = queryId,
            votingAddress = walletAddress,
            expirationDate = BigInteger.valueOf(1_800_000_000),
            vote = true,
            needConfirmation = false,
        ),
        TonPayloadFormat.ChangeDNSRecord(
            queryId,
            DNSRecord.Wallet(DNSWalletRecord(walletAddress, DnsChangeCapabilities(isWallet = true))),
        ),
        TonPayloadFormat.ChangeDNSRecord(queryId, DNSRecord.Wallet(DNSWalletRecord(walletAddress, null))),
        TonPayloadFormat.ChangeDNSRecord(queryId, DNSRecord.Wallet(null)),
        TonPayloadFormat.Unsafe(cell),
    )

    private val payloadsWithByteArrays: List<TonPayloadFormat> = listOf(
        TonPayloadFormat.JettonBurn(
            queryId,
            Coins.ofNano(5),
            walletAddress,
            JettonBurnCustomPayload.ByteArrayPayload(ByteArray(20) { it.toByte() }),
        ),
        TonPayloadFormat.ChangeDNSRecord(queryId, DNSRecord.Unknown(bytes32, cell)),
        TonPayloadFormat.ChangeDNSRecord(queryId, DNSRecord.Unknown(bytes32, null)),
        TonPayloadFormat.TokenBridgePaySwap(queryId, bytes32),
    )

    @Test
    fun transactionIsEqualAfterSavedState() {
        for (transaction in transactionsOf(payloadsWithoutByteArrays)) {
            val restored = transaction.throughSavedState()
            assertEquals(transaction.copy(stateInit = null), restored.copy(stateInit = null))
            assertEquals(
                transaction.stateInit?.let(StateInit::createCell),
                restored.stateInit?.let(StateInit::createCell),
            )
        }
    }

    @Test
    fun ledgerReceivesSameRequestAfterSavedState() {
        for (transaction in transactionsOf(payloadsWithoutByteArrays + payloadsWithByteArrays)) {
            assertEquals(
                transaction.payload.toString(),
                ledgerRequest(transaction),
                ledgerRequest(transaction.throughSavedState()),
            )
        }
    }

    @Test
    fun ledgerConnectDataIsEqualAfterSavedState() {
        val connectData = LedgerConnectData(
            accounts = listOf(AccountPath(0), AccountPath(1), AccountPath(2, workchain = -1)).map { path ->
                LedgerAccount(path.contract(publicKey).address, publicKey, path)
            },
            deviceId = "D1:6A:42:00:00:01",
            model = Devices.getDevice(DeviceModelId.STAX),
        )

        assertEquals(connectData, connectData.throughSavedState())
    }

    private fun transactionsOf(payloads: List<TonPayloadFormat?>): List<Transaction> = payloads.flatMap { payload ->
        listOf(
            Transaction(
                destination = walletAddress,
                sendMode = 3,
                seqno = 42,
                timeout = 1_800_000_000,
                bounceable = true,
                coins = Coins.ofNano(1_000_000_000),
                stateInit = contract.stateInitRef.value,
                payload = payload,
            ),
            Transaction(
                destination = masterchainAddress,
                sendMode = 1,
                seqno = 0,
                timeout = 1_800_000_060,
                bounceable = false,
                coins = Coins.ofNano(0),
                payload = payload,
            ),
        )
    }

    private fun ledgerRequest(transaction: Transaction): List<String> {
        val transport = RecordingTransport()
        val error = assertThrows(Exception::class.java) {
            runBlocking { TonTransport(transport).signTransaction(accountPath, transaction) }
        }
        val signedHash = requireNotNull(error.message)
        assertTrue(signedHash, signedHash.startsWith("Hash mismatch"))
        return transport.apdus + signedHash
    }

    private inline fun <reified T : Parcelable> T.throughSavedState(): T {
        val bundle = Bundle().apply { putParcelable(KEY, this@throughSavedState) }
        val parcel = Parcel.obtain()
        val bytes = try {
            parcel.writeBundle(bundle)
            parcel.marshall()
        } finally {
            parcel.recycle()
        }

        val restoredParcel = Parcel.obtain()
        try {
            restoredParcel.unmarshall(bytes, 0, bytes.size)
            restoredParcel.setDataPosition(0)
            val restoredBundle = requireNotNull(restoredParcel.readBundle(T::class.java.classLoader))
            return requireNotNull(BundleCompat.getParcelable(restoredBundle, KEY, T::class.java))
        } finally {
            restoredParcel.recycle()
        }
    }

    private class RecordingTransport : Transport {

        val apdus = mutableListOf<String>()

        override suspend fun exchange(apdu: ByteArray): ByteArray {
            apdus += apdu.joinToString("") { "%02x".format(it) }
            return ByteArray(SIGN_RESPONSE_SIZE) + byteArrayOf(0x90.toByte(), 0x00)
        }
    }

    private companion object {
        const val KEY = "value"
        const val SIGN_RESPONSE_SIZE = 98
    }
}
