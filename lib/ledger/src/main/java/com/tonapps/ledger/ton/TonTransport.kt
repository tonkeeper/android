package com.tonapps.ledger.ton

import com.tonapps.ledger.transport.Transport
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.Coins
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.contract.SmartContract
import org.ton.crypto.hex
import org.ton.tlb.storeTlb
import kotlin.math.ceil

class TonTransport(private val transport: Transport) {
    companion object {
        const val LEDGER_SYSTEM = 0xB0
        const val LEDGER_CLA = 0xE0
        const val INS_VERSION = 0x03
        const val INS_ADDRESS = 0x05
        const val INS_SIGN_TX = 0x06
        const val INS_PROOF = 0x08
        const val INS_SIGN_DATA = 0x09
    }

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

    private val lock = Mutex()

    private suspend fun doRequest(ins: Int, p1: Int, p2: Int, data: ByteArray): ByteArray {
        return lock.withLock {
            val r = transport.send(
                LEDGER_CLA, ins, p1, p2, data, null
            )
            r.sliceArray(0 until r.size - 2)
        }
    }

    private suspend fun getCurrentApp(): Pair<String, String> {
        return lock.withLock {
            val r = transport.send(
                LEDGER_SYSTEM, 0x01, 0x00, 0x00, ByteArray(0), listOf(0x9000)
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
            Pair(name, version)
        }
    }

    suspend fun isAppOpen(): Boolean {
        return getCurrentApp().first == "TON"
    }

    suspend fun getVersion(): String {
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

        val publicKey = PublicKeyEd25519(response)
        val contract = path.contract(publicKey)

        return LedgerAccount(contract.address, publicKey, path)
    }

    suspend fun signTransaction(
        path: AccountPath, transaction: Transaction
    ): Cell {
        val publicKey = getAccount(path).publicKey

        var pkg =
            LedgerWriter.putUint8(0) + LedgerWriter.putUint32(transaction.seqno) + LedgerWriter.putUint32(
                transaction.timeout
            ) + LedgerWriter.putVarUInt(transaction.amount.amount.toLong()) + LedgerWriter.putAddress(
                transaction.to
            ) + LedgerWriter.putUint8(
                (if (transaction.bounce) 1 else 0)
            ) + LedgerWriter.putUint8(transaction.sendMode)

        var stateInit: Cell? = null
        if (transaction.stateInit != null) {
            stateInit = StateInit.tlbCodec().createCell(transaction.stateInit)
            pkg += LedgerWriter.putUint8(1) + LedgerWriter.putUint16(stateInit.depth()) + stateInit.hash()
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
                    cell = cell.storeUInt(queryId, 64)
                } ?: run {
                    bytes += LedgerWriter.putUint8(0)
                    cell = cell.storeUInt(0, 64)
                }

                bytes += LedgerWriter.putVarUInt(transaction.payload.amount.amount.toLong()) + LedgerWriter.putAddress(
                    transaction.payload.destination
                ) + LedgerWriter.putAddress(transaction.payload.responseDestination)
                cell = cell.storeTlb(Coins, transaction.payload.amount)
                    .storeTlb(MsgAddressInt, transaction.payload.destination)
                    .storeTlb(MsgAddressInt, transaction.payload.responseDestination)

                if (transaction.payload.customPayload != null) {
                    bytes += LedgerWriter.putUint8(1) + LedgerWriter.putCellRef(transaction.payload.customPayload)
                    cell = cell.storeRef(transaction.payload.customPayload)
                } else {
                    bytes += LedgerWriter.putUint8(0)
                    cell = cell.storeBit(false)
                }

                bytes += LedgerWriter.putVarUInt(transaction.payload.forwardAmount.amount.toLong())
                cell = cell.storeTlb(Coins, transaction.payload.forwardAmount)

                if (transaction.payload.forwardPayload != null) {
                    bytes += LedgerWriter.putUint8(1) + LedgerWriter.putCellRef(transaction.payload.forwardPayload)
                    cell = cell.storeRef(transaction.payload.forwardPayload)
                } else {
                    bytes += LedgerWriter.putUint8(0)
                    cell = cell.storeBit(false)
                }

                payload = cell.endCell()
                hints += LedgerWriter.putUint16(bytes.size) + bytes
            }

            is TonPayloadFormat.NftTransfer -> {
                hints = LedgerWriter.putUint8(1) + LedgerWriter.putUint32(0x02)
                var cell = CellBuilder.beginCell().storeUInt(0x5fcc3d14, 32)
                var bytes = ByteArray(0)

                transaction.payload.queryId?.let { queryId ->
                    bytes += LedgerWriter.putUint8(1) + LedgerWriter.putUint64(queryId)
                    cell = cell.storeUInt(queryId, 64)
                } ?: run {
                    bytes += LedgerWriter.putUint8(0)
                    cell = cell.storeUInt(0, 64)
                }

                bytes += LedgerWriter.putAddress(transaction.payload.newOwner) + LedgerWriter.putAddress(
                    transaction.payload.responseDestination
                )
                cell = cell.storeTlb(MsgAddressInt, transaction.payload.newOwner)
                    .storeTlb(MsgAddressInt, transaction.payload.responseDestination)

                if (transaction.payload.customPayload != null) {
                    bytes += LedgerWriter.putUint8(1) + LedgerWriter.putCellRef(transaction.payload.customPayload)
                    cell = cell.storeRef(transaction.payload.customPayload)
                } else {
                    bytes += LedgerWriter.putUint8(0)
                    cell = cell.storeBit(false)
                }

                bytes += LedgerWriter.putVarUInt(transaction.payload.forwardAmount.amount.toLong())
                cell = cell.storeTlb(Coins, transaction.payload.forwardAmount)

                if (transaction.payload.forwardPayload != null) {
                    bytes += LedgerWriter.putUint8(1) + LedgerWriter.putCellRef(transaction.payload.forwardPayload)
                    cell = cell.storeRef(transaction.payload.forwardPayload)
                } else {
                    bytes += LedgerWriter.putUint8(0)
                    cell = cell.storeBit(false)
                }

                payload = cell.endCell()
                hints += LedgerWriter.putUint16(bytes.size) + bytes
            }

            null -> TODO()
        }

        pkg += if (!payload.isEmpty()) {
            LedgerWriter.putUint8(1) + LedgerWriter.putUint16(payload.depth()) + payload.hash() + hints
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
            storeBit(transaction.bounce)
            storeBit(false)
            storeUInt(0, 2)
            storeTlb(MsgAddressInt, transaction.to)
            storeTlb(Coins, transaction.amount)
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
        if (!hash.contentEquals(transfer.hash())) {
            throw Error("Hash mismatch. Expected: ${hex(transfer.hash())}, got: ${hex(hash)}")
        }
        if (!publicKey.verify(hash, signature)) {
            throw Error("Received signature is invalid")
        }

        // Build a message
        return CellBuilder.createCell {
            storeBytes(signature)
            storeSlice(transfer.beginParse())
        }
    }
}