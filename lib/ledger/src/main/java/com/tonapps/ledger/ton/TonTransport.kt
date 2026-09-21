package com.tonapps.ledger.ton

import com.tonapps.blockchain.ton.TONOpCode
import com.tonapps.blockchain.ton.extensions.storeAddress
import com.tonapps.blockchain.ton.extensions.storeCoins
import com.tonapps.blockchain.ton.extensions.storeOpCode
import com.tonapps.blockchain.ton.toBigInt
import com.tonapps.ledger.transport.LedgerAppName
import com.tonapps.ledger.transport.Transport
import com.tonapps.ledger.transport.TransportStatusException
import io.ktor.util.hex
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.bytestring.ByteString
import org.ton.block.Coins
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.kotlin.crypto.PublicKeyEd25519
import org.ton.kotlin.crypto.sha256
import org.ton.tlb.storeTlb
import java.math.BigInteger
import kotlin.math.ceil

data class ParseOptions(
    val disallowUnsafe: Boolean = false,
    val disallowModification: Boolean = false,
    val encodeJettonBurnEthAddressAsHex: Boolean = true
)

enum class OpenAppResult {
    Opened,
    Denied,
    NotInstalled,
}

class TonTransport(private val transport: Transport) {
    private var _currentVersion: String? = null

    private val lock = Mutex()

    private fun chunks(buf: ByteArray, n: Int): List<ByteArray> {
        val nc = ceil(buf.size / n.toDouble()).toInt()
        val cs = mutableListOf<ByteArray>()
        for (i in 0 until nc) {
            val start = i * n
            val end = minOf((i + 1) * n, buf.size)
            cs.add(buf.copyOfRange(start, end))
        }
        return cs
    }

    fun close() {
        transport.close()
    }

    private suspend fun doRequest(
        ins: Int,
        p1: Int,
        p2: Int,
        data: ByteArray,
        cla: Int = LEDGER_CLA
    ): ByteArray {
        return lock.withLock {
            val r = transport.send(
                cla, ins, p1, p2, data,
            )
            r.sliceArray(0 until r.size - 2)
        }
    }

    suspend fun getCurrentApp(): LedgerAppName {
        lock.withLock {
            val r = transport.send(
                LEDGER_SYSTEM, 0x01, 0x00, 0x00, ByteArray(0)
            )

            val data = r.sliceArray(0 until r.size - 2)
            if (data[0] != 0x01.toByte()) {
                throw Exception("Invalid response")
            }

            val nameLength = data[1].toInt()
            val name = data.sliceArray(2 until 2 + nameLength).toString(Charsets.UTF_8)
            val versionLength = data[2 + nameLength].toInt()
            val version = data.sliceArray(3 + nameLength until 3 + nameLength + versionLength)
                .toString(Charsets.UTF_8)

            return LedgerAppName(
                name = name,
                version = version
            )
        }
    }

    suspend fun currentApp(): LedgerAppName {
        val app = getCurrentApp()
        if (app.isTonApp) {
            _currentVersion = app.version
        }
        return app
    }

    suspend fun openTonApp(): OpenAppResult {
        return try {
            doRequest(INS_OPEN_APP, 0x00, 0x00, LedgerAppName.TON_APP_NAME.toByteArray())
            OpenAppResult.Opened
        } catch (_: TransportStatusException.DeniedByUser) {
            OpenAppResult.Denied
        } catch (_: TransportStatusException.AppNotInstalled) {
            OpenAppResult.NotInstalled
        }
    }

    suspend fun quitApp() {
        doRequest(INS_QUIT_APP, 0x00, 0x00, ByteArray(0), cla = LEDGER_SYSTEM)
    }

    suspend fun isLocked(): Boolean {
        try {
            getCurrentApp()
            return false
        } catch (e: TransportStatusException.LockedDevice) {
            return true
        }
    }

    suspend fun getVersion(): String {
        return _currentVersion ?: requestVersion().also {
            _currentVersion = it
        }
    }

    private suspend fun requestVersion(): String {
        val loaded = doRequest(INS_VERSION, 0x00, 0x00, ByteArray(0))
        if (loaded.size < 3) {
            throw Exception("Invalid response")
        }
        val major = loaded[0].toInt()
        val minor = loaded[1].toInt()
        val patch = loaded[2].toInt()
        return "$major.$minor.$patch"
    }

    suspend fun getAccount(
        path: AccountPath,
    ): LedgerAccount {
        // Get public key
        val response = doRequest(INS_ADDRESS, 0x00, 0x00, path.toByteArray())
        if (response.size != 32) {
            throw Exception("Invalid response")
        }

        val publicKey = PublicKeyEd25519(ByteString(response))
        val contract = path.contract(publicKey)

        return LedgerAccount(contract.address, publicKey, path)
    }

    suspend fun signAddressProof(
        path: AccountPath,
        domain: String,
        timestamp: BigInteger,
        payload: String
    ): ByteArray {
        val publicKey = getAccount(path).publicKey
        val domainBytes = domain.toByteArray()

        val pkg =
            path.toByteArray() + LedgerWriter.putUint8(domainBytes.size) + domainBytes + LedgerWriter.putUint64(
                timestamp
            ) + payload.toByteArray()

        val res = doRequest(INS_PROOF, 0x01, 0x00, pkg)
        val signature = res.sliceArray(1 until 1 + 64)
        val hash = res.sliceArray(2 + 64 until 2 + 64 + 32)
        if (!publicKey.verifySignature(hash, signature)) {
            throw Exception("Received signature is invalid")
        }

        return signature
    }

    suspend fun signTransaction(
        path: AccountPath, transaction: Transaction
    ): Cell {
        var pkg =
            LedgerWriter.putUint8(0) + LedgerWriter.putUint32(transaction.seqno) + LedgerWriter.putUint32(
                transaction.timeout
            ) + LedgerWriter.putVarUInt(transaction.coins.amount.toLong()) + LedgerWriter.putAddress(
                transaction.destination
            ) + LedgerWriter.putUint8(
                transaction.bounceable.asFlag()
            ) + LedgerWriter.putUint8(transaction.sendMode)

        var stateInit: Cell? = null
        if (transaction.stateInit != null) {
            stateInit = StateInit.tlbCodec().createCell(transaction.stateInit)
            pkg += LedgerWriter.putUint8(1) + LedgerWriter.putUint16(stateInit.depth()) + stateInit.hash()
                .toByteArray()
        } else {
            pkg += LedgerWriter.putUint8(0)
        }

        var payload = Cell()
        var hints = LedgerWriter.putUint8(0)
        when (transaction.payload) {
            is TonPayloadFormat.Comment -> {
                val comment = transaction.payload.text.toByteArray()
                hints =
                    LedgerWriter.putUint8(1) + LedgerWriter.putUint32(0x00) + LedgerWriter.putUint16(
                        comment.size
                    ) + comment
                payload = CellBuilder.createCell {
                    storeUInt(0, 32)
                    storeBytes(comment)
                }
            }

            is TonPayloadFormat.JettonTransfer -> {
                hints = LedgerWriter.putUint8(1) + LedgerWriter.putUint32(0x01)
                var cell = CellBuilder.beginCell().storeUInt(0x0f8a7ea5, 32)
                var bytes = ByteArray(0)

                transaction.payload.queryId?.let { queryId ->
                    bytes += LedgerWriter.putUint8(1) + LedgerWriter.putUint64(queryId)
                    cell = cell.storeUInt(queryId.toBigInt(), 64)
                } ?: run {
                    bytes += LedgerWriter.putUint8(0)
                    cell = cell.storeUInt(0, 64)
                }

                bytes += LedgerWriter.putVarUInt(transaction.payload.coins.amount.toLong()) + LedgerWriter.putAddress(
                    transaction.payload.receiverAddress
                ) + LedgerWriter.putAddress(transaction.payload.excessesAddress)
                cell = cell.storeTlb(Coins, transaction.payload.coins)
                    .storeTlb(MsgAddressInt, transaction.payload.receiverAddress)
                    .storeTlb(MsgAddressInt, transaction.payload.excessesAddress)

                if (transaction.payload.customPayload != null) {
                    bytes += LedgerWriter.putUint8(1) + LedgerWriter.putCellRef(transaction.payload.customPayload)
                    cell = cell.storeBit(true).storeRef(transaction.payload.customPayload)
                } else {
                    bytes += LedgerWriter.putUint8(0)
                    cell = cell.storeBit(false)
                }

                bytes += LedgerWriter.putVarUInt(transaction.payload.forwardAmount.amount.toLong())
                cell = cell.storeTlb(Coins, transaction.payload.forwardAmount)

                if (transaction.payload.forwardPayload != null) {
                    bytes += LedgerWriter.putUint8(1) + LedgerWriter.putCellRef(transaction.payload.forwardPayload)
                    cell = cell.storeBit(true).storeRef(transaction.payload.forwardPayload)
                } else {
                    bytes += LedgerWriter.putUint8(0)
                    cell = cell.storeBit(false)
                }

                payload = cell.endCell()
                hints += LedgerWriter.putUint16(bytes.size) + bytes
            }

            is TonPayloadFormat.NftTransfer -> {
                hints = LedgerWriter.putUint8(1) + LedgerWriter.putUint32(0x02)
                var cell = CellBuilder.beginCell().storeOpCode(TONOpCode.NFT_TRANSFER)
                var bytes = ByteArray(0)

                transaction.payload.queryId?.let { queryId ->
                    bytes += LedgerWriter.putUint8(1) + LedgerWriter.putUint64(queryId)
                    cell = cell.storeUInt(queryId.toBigInt(), 64)
                } ?: run {
                    bytes += LedgerWriter.putUint8(0)
                    cell = cell.storeUInt(0, 64)
                }

                bytes += LedgerWriter.putAddress(transaction.payload.newOwnerAddress) + LedgerWriter.putAddress(
                    transaction.payload.excessesAddress
                )
                cell = cell.storeTlb(MsgAddressInt, transaction.payload.newOwnerAddress)
                    .storeTlb(MsgAddressInt, transaction.payload.excessesAddress)

                if (transaction.payload.customPayload != null) {
                    bytes += LedgerWriter.putUint8(1) + LedgerWriter.putCellRef(transaction.payload.customPayload)
                    cell = cell.storeBit(true).storeRef(transaction.payload.customPayload)
                } else {
                    bytes += LedgerWriter.putUint8(0)
                    cell = cell.storeBit(false)
                }

                bytes += LedgerWriter.putVarUInt(transaction.payload.forwardAmount.amount.toLong())
                cell = cell.storeTlb(Coins, transaction.payload.forwardAmount)

                if (transaction.payload.forwardPayload != null) {
                    bytes += LedgerWriter.putUint8(1) + LedgerWriter.putCellRef(transaction.payload.forwardPayload)
                    cell = cell.storeBit(true).storeRef(transaction.payload.forwardPayload)
                } else {
                    bytes += LedgerWriter.putUint8(0)
                    cell = cell.storeBit(false)
                }

                payload = cell.endCell()
                hints += LedgerWriter.putUint16(bytes.size) + bytes
            }

            is TonPayloadFormat.JettonBurn -> {
                hints = LedgerWriter.putUint8(1) + LedgerWriter.putUint32(0x03)
                var cell = CellBuilder.beginCell().storeOpCode(TONOpCode.LIQUID_TF_BURN)
                var bytes = ByteArray(0)

                transaction.payload.queryId?.let { queryId ->
                    bytes += LedgerWriter.putUint8(1) + LedgerWriter.putUint64(queryId)
                    cell = cell.storeUInt(queryId.toBigInt(), 64)
                } ?: run {
                    bytes += LedgerWriter.putUint8(0)
                    cell = cell.storeUInt(0, 64)
                }

                bytes += LedgerWriter.putVarUInt(transaction.payload.coins.amount.toLong()) + LedgerWriter.putAddress(
                    transaction.payload.responseDestination
                )
                cell = cell.storeTlb(Coins, transaction.payload.coins)
                    .storeTlb(MsgAddressInt, transaction.payload.responseDestination)

                if (transaction.payload.customPayload != null) {
                    when (val customPayload = transaction.payload.customPayload) {
                        is JettonBurnCustomPayload.ByteArrayPayload -> {
                            val customPayloadBytes = customPayload.byteArray
                            bytes += LedgerWriter.putUint8(2) + LedgerWriter.putUint8(
                                customPayloadBytes.size
                            ) + customPayloadBytes
                            cell = cell.storeBit(true)
                                .storeRef(CellBuilder.createCell { storeBytes(customPayloadBytes) })
                        }

                        is JettonBurnCustomPayload.CellPayload -> {
                            bytes += LedgerWriter.putUint8(1) + LedgerWriter.putCellRef(
                                customPayload.cell
                            )
                            cell = cell.storeBit(true).storeRef(customPayload.cell)
                        }

                        null -> {}
                    }
                } else {
                    bytes += LedgerWriter.putUint8(0)
                    cell = cell.storeBit(false)
                }

                payload = cell.endCell()
                hints += LedgerWriter.putUint16(bytes.size) + bytes
            }

            is TonPayloadFormat.AddWhitelist -> {
                hints = LedgerWriter.putUint8(1) + LedgerWriter.putUint32(0x04)
                var cell = CellBuilder.beginCell().storeUInt(0x7258a69b, 32)
                var bytes = ByteArray(0)

                transaction.payload.queryId?.let { queryId ->
                    bytes += LedgerWriter.putUint8(1) + LedgerWriter.putUint64(queryId)
                    cell = cell.storeUInt(queryId.toBigInt(), 64)
                } ?: run {
                    bytes += LedgerWriter.putUint8(0)
                    cell = cell.storeUInt(0, 64)
                }

                bytes += LedgerWriter.putAddress(transaction.payload.address)
                cell = cell.storeTlb(MsgAddressInt, transaction.payload.address)

                payload = cell.endCell()
                hints += LedgerWriter.putUint16(bytes.size) + bytes
            }

            is TonPayloadFormat.ChangeDNSRecord -> {
                hints = LedgerWriter.putUint8(1) + LedgerWriter.putUint32(0x09)
                var cell = CellBuilder.beginCell().storeUInt(0x4eb1f0f9, 32)
                var bytes = ByteArray(0)

                transaction.payload.queryId?.let { queryId ->
                    bytes += LedgerWriter.putUint8(1) + LedgerWriter.putUint64(queryId)
                    cell = cell.storeUInt(queryId.toBigInt(), 64)
                } ?: run {
                    bytes += LedgerWriter.putUint8(0)
                    cell = cell.storeUInt(0, 64)
                }

                when (val record = transaction.payload.record) {
                    is DNSRecord.Wallet -> {
                        cell = cell.storeBytes(sha256("wallet".toByteArray()))

                        if (record.wallet != null) {
                            val wallet = record.wallet
                            bytes += LedgerWriter.putUint8(1) + LedgerWriter.putUint8(0) +
                                    LedgerWriter.putAddress(wallet.address) +
                                    LedgerWriter.putUint8((wallet.capabilities != null).asFlag())
                            val rb = CellBuilder.beginCell()
                                .storeUInt(0x9fd3, 16)
                                .storeTlb(MsgAddressInt, wallet.address)
                                .storeUInt((wallet.capabilities != null).asFlag(), 8)

                            if (wallet.capabilities != null) {
                                bytes += LedgerWriter.putUint8(wallet.capabilities.isWallet.asFlag())
                                if (wallet.capabilities.isWallet) {
                                    rb.storeBit(true).storeUInt(0x2177, 16)
                                }
                                rb.storeBit(false)
                            }
                            cell = cell.storeRef(rb.endCell())
                        } else {
                            bytes += LedgerWriter.putUint8(0) + LedgerWriter.putUint8(0)
                        }
                    }

                    is DNSRecord.Unknown -> {
                        if (record.key.size != 32) {
                            throw Exception("DNS record key length must be 32 bytes long")
                        }

                        bytes += LedgerWriter.putUint8((record.value != null).asFlag())
                        bytes += LedgerWriter.putUint8(1)
                        bytes += record.key

                        cell = cell.storeBytes(record.key)

                        if (record.value != null) {
                            bytes += LedgerWriter.putCellRef(record.value)
                            cell = cell.storeRef(record.value)
                        }
                    }
                }

                payload = cell.endCell()
                hints += LedgerWriter.putUint16(bytes.size) + bytes
            }

            is TonPayloadFormat.SingleNominatorChangeValidator -> {
                hints = LedgerWriter.putUint8(1) + LedgerWriter.putUint32(0x06)
                var cell = CellBuilder.beginCell().storeUInt(0x1001, 32)
                var bytes = ByteArray(0)

                transaction.payload.queryId?.let { queryId ->
                    bytes += LedgerWriter.putUint8(1) + LedgerWriter.putUint64(queryId)
                    cell = cell.storeUInt(queryId.toBigInt(), 64)
                } ?: run {
                    bytes += LedgerWriter.putUint8(0)
                    cell = cell.storeUInt(0, 64)
                }

                bytes += LedgerWriter.putAddress(transaction.payload.address)
                cell = cell.storeTlb(MsgAddressInt, transaction.payload.address)

                payload = cell.endCell()
                hints += LedgerWriter.putUint16(bytes.size) + bytes
            }

            is TonPayloadFormat.SingleNominatorWithdraw -> {
                hints = LedgerWriter.putUint8(1) + LedgerWriter.putUint32(0x05)
                var cell = CellBuilder.beginCell().storeUInt(0x1000, 32)
                var bytes = ByteArray(0)

                transaction.payload.queryId?.let { queryId ->
                    bytes += LedgerWriter.putUint8(1) + LedgerWriter.putUint64(queryId)
                    cell = cell.storeUInt(queryId.toBigInt(), 64)
                } ?: run {
                    bytes += LedgerWriter.putUint8(0)
                    cell = cell.storeUInt(0, 64)
                }

                bytes += LedgerWriter.putVarUInt(transaction.payload.coins.amount.toLong())
                cell = cell.storeTlb(Coins, transaction.payload.coins)

                payload = cell.endCell()
                hints += LedgerWriter.putUint16(bytes.size) + bytes
            }

            is TonPayloadFormat.TokenBridgePaySwap -> {
                hints = LedgerWriter.putUint8(1) + LedgerWriter.putUint32(0x0A)
                var cell = CellBuilder.beginCell().storeUInt(0x8, 32)
                var bytes = ByteArray(0)

                transaction.payload.queryId?.let { queryId ->
                    bytes += LedgerWriter.putUint8(1) + LedgerWriter.putUint64(queryId)
                    cell = cell.storeUInt(queryId.toBigInt(), 64)
                } ?: run {
                    bytes += LedgerWriter.putUint8(0)
                    cell = cell.storeUInt(0, 64)
                }

                if (transaction.payload.swapId.size != 32) {
                    throw Exception("Swap ID must be 32 bytes long")
                }

                bytes += transaction.payload.swapId
                cell = cell.storeBytes(transaction.payload.swapId)

                payload = cell.endCell()
                hints += LedgerWriter.putUint16(bytes.size) + bytes
            }

            is TonPayloadFormat.TonstakersDeposit -> {
                hints = LedgerWriter.putUint8(1) + LedgerWriter.putUint32(0x07)
                var cell = CellBuilder.beginCell().storeOpCode(TONOpCode.LIQUID_TF_DEPOSIT)
                var bytes = ByteArray(0)

                transaction.payload.queryId?.let { queryId ->
                    bytes += LedgerWriter.putUint8(1) + LedgerWriter.putUint64(queryId)
                    cell = cell.storeUInt(queryId.toBigInt(), 64)
                } ?: run {
                    bytes += LedgerWriter.putUint8(0)
                    cell = cell.storeUInt(0, 64)
                }

                if (transaction.payload.appId != null) {
                    bytes += LedgerWriter.putUint8(1) + LedgerWriter.putUint64(transaction.payload.appId)
                    cell = cell.storeUInt(transaction.payload.appId.toBigInt(), 64)
                } else {
                    bytes += LedgerWriter.putUint8(0)
                }

                payload = cell.endCell()
                hints += LedgerWriter.putUint16(bytes.size) + bytes
            }

            is TonPayloadFormat.Unsafe -> {
                payload = transaction.payload.message
            }

            is TonPayloadFormat.VoteForProposal -> {
                hints = LedgerWriter.putUint8(1) + LedgerWriter.putUint32(0x08)
                var cell = CellBuilder.beginCell().storeUInt(0x69fb306c, 32)
                var bytes = ByteArray(0)

                transaction.payload.queryId?.let { queryId ->
                    bytes += LedgerWriter.putUint8(1) + LedgerWriter.putUint64(queryId)
                    cell = cell.storeUInt(queryId.toBigInt(), 64)
                } ?: run {
                    bytes += LedgerWriter.putUint8(0)
                    cell = cell.storeUInt(0, 64)
                }

                bytes += LedgerWriter.putAddress(transaction.payload.votingAddress) + LedgerWriter.putUint48(
                    transaction.payload.expirationDate
                ) + LedgerWriter.putUint8(transaction.payload.vote.asFlag()) + LedgerWriter.putUint8(
                    transaction.payload.needConfirmation.asFlag()
                )
                cell = cell.storeTlb(MsgAddressInt, transaction.payload.votingAddress)
                    .storeUInt(transaction.payload.expirationDate.toBigInt(), 48)
                    .storeBit(transaction.payload.vote)
                    .storeBit(transaction.payload.needConfirmation)

                payload = cell.endCell()
                hints += LedgerWriter.putUint16(bytes.size) + bytes
            }

            null -> {}
        }

        pkg += if (!payload.isEmpty()) {
            LedgerWriter.putUint8(1) + LedgerWriter.putUint16(payload.depth()) + payload.hash()
                .toByteArray() + hints
        } else {
            LedgerWriter.putUint8(0) + LedgerWriter.putUint8(0)
        }

        doRequest(INS_SIGN_TX, 0x00, 0x03, path.toByteArray())
        val pkgCs = chunks(pkg, 255)
        pkgCs.dropLast(1).forEach {
            doRequest(INS_SIGN_TX, 0x00, 0x02, it)
        }
        val res = doRequest(INS_SIGN_TX, 0x00, 0x00, pkgCs.last())

        // Parse response
        val orderCell = CellBuilder.createCell {
            storeBit(false)
            storeBit(true)
            storeBit(transaction.bounceable)
            storeBit(false)
            storeUInt(0, 2)
            storeAddress(transaction.destination)
            storeCoins(transaction.coins)
            storeBit(false)
            storeTlb(Coins, Coins.ofNano(0))
            storeTlb(Coins, Coins.ofNano(0))
            storeUInt(0, 64)
            storeUInt(0, 32)

            // State Init
            if (stateInit != null) {
                storeBit(true)
                storeBit(true)
                storeRef(stateInit)
            } else {
                storeBit(false)
            }

            // Payload
            if (!payload.isEmpty()) {
                storeBit(true)
                storeRef(payload)
            } else {
                storeBit(false)
            }
        }

        // Transfer message
        val transfer = CellBuilder.createCell {
            storeUInt(698983191, 32)
            storeUInt(transaction.timeout, 32)
            storeUInt(transaction.seqno, 32)
            storeUInt(0, 8)
            storeUInt(transaction.sendMode, 8)
            storeRef(orderCell)
        }

        // Parse result
        val signature = res.slice(1 until 65).toByteArray()
        val hash = res.slice(66 until 98).toByteArray()
        if (!hash.contentEquals(transfer.hash().toByteArray())) {
            throw Exception(
                "Hash mismatch. Expected: ${hex(transfer.hash().toByteArray())}, got: ${
                    hex(
                        hash
                    )
                }"
            )
        }

        // Build a message
        return CellBuilder.createCell {
            storeBytes(signature)
            storeSlice(transfer.beginParse())
        }
    }

    companion object {
        const val LEDGER_SYSTEM = 0xB0
        const val LEDGER_CLA = 0xE0
        const val INS_VERSION = 0x03
        const val INS_OPEN_APP = 0xd8
        const val INS_QUIT_APP = 0xA7
        const val INS_ADDRESS = 0x05
        const val INS_SIGN_TX = 0x06
        const val INS_PROOF = 0x08
    }
}

private fun Boolean.asFlag(): Int = if (this) {
    1
} else {
    0
}
