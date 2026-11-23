package com.tonapps.ledgerkit.app.btc

import java.io.ByteArrayOutputStream

class BtcRawTransaction(
    val version: Long,
    val inputs: List<Input>,
    val outputs: List<Output>,
    val witnesses: List<List<ByteArray>>,
    val lockTime: Long,
) {

    class Input(val previousTxidInternal: ByteArray, val previousIndex: Long, val scriptSig: ByteArray, val sequence: Long)

    class Output(val amount: Long, val script: ByteArray)

    val hasWitness: Boolean
        get() = witnesses.any { it.isNotEmpty() }

    val txidInternal: ByteArray
        get() = doubleSha256(serialize(includeWitness = false))

    val txidDisplay: String
        get() = txidInternal.reversedArray().joinToString("") { "%02x".format(it) }

    fun serialize(includeWitness: Boolean = hasWitness): ByteArray {
        val out = ByteArrayOutputStream()
        out.appendBytes(littleEndian(version, 4))
        if (includeWitness) {
            out.write(0x00)
            out.write(0x01)
        }
        out.appendBytes(CompactSize.encode(inputs.size))
        for (input in inputs) {
            out.appendBytes(input.previousTxidInternal)
            out.appendBytes(littleEndian(input.previousIndex, 4))
            out.appendBytes(CompactSize.encode(input.scriptSig.size))
            out.appendBytes(input.scriptSig)
            out.appendBytes(littleEndian(input.sequence, 4))
        }
        out.appendBytes(CompactSize.encode(outputs.size))
        for (output in outputs) {
            out.appendBytes(littleEndian(output.amount, 8))
            out.appendBytes(CompactSize.encode(output.script.size))
            out.appendBytes(output.script)
        }
        if (includeWitness) {
            for (index in inputs.indices) {
                val stack = witnesses.getOrElse(index) { emptyList() }
                out.appendBytes(CompactSize.encode(stack.size))
                for (item in stack) {
                    out.appendBytes(CompactSize.encode(item.size))
                    out.appendBytes(item)
                }
            }
        }
        out.appendBytes(littleEndian(lockTime, 4))
        return out.toByteArray()
    }

    companion object {

        private const val MIN_INPUT_BYTES = 41
        private const val MIN_OUTPUT_BYTES = 9
        private const val MIN_WITNESS_ITEM_BYTES = 1

        fun parse(raw: ByteArray): BtcRawTransaction {
            val reader = Reader(raw)
            val version = reader.uint(4)
            var segwit = false
            if (reader.peek() == 0x00 && reader.peek(1) == 0x01) {
                segwit = true
                reader.skip(2)
            }
            val inputs = List(reader.count(MIN_INPUT_BYTES)) {
                Input(
                    previousTxidInternal = reader.bytes(32),
                    previousIndex = reader.uint(4),
                    scriptSig = reader.bytes(reader.compactSize().toInt()),
                    sequence = reader.uint(4),
                )
            }
            val outputs = List(reader.count(MIN_OUTPUT_BYTES)) {
                Output(amount = reader.uint(8), script = reader.bytes(reader.compactSize().toInt()))
            }
            val witnesses = if (segwit) {
                List(inputs.size) { List(reader.count(MIN_WITNESS_ITEM_BYTES)) { reader.bytes(reader.compactSize().toInt()) } }
            } else {
                List(inputs.size) { emptyList() }
            }
            val lockTime = reader.uint(4)
            require(reader.isDone) { "Trailing bytes after the transaction" }
            return BtcRawTransaction(version, inputs, outputs, witnesses, lockTime)
        }
    }

    private class Reader(private val data: ByteArray) {
        private var offset = 0

        val isDone: Boolean
            get() = offset == data.size

        fun peek(ahead: Int = 0): Int {
            require(offset + ahead < data.size) { "Truncated transaction" }
            return data[offset + ahead].toInt() and 0xff
        }

        fun skip(count: Int) {
            require(offset + count <= data.size) { "Truncated transaction" }
            offset += count
        }

        fun bytes(count: Int): ByteArray {
            require(count >= 0 && offset + count <= data.size) { "Truncated transaction" }
            return data.copyOfRange(offset, offset + count).also { offset += count }
        }

        fun uint(size: Int): Long = readLittleEndian(data, offset, size).also { offset += size }

        fun compactSize(): Long {
            val (value, length) = CompactSize.decode(data, offset)
            offset += length
            return value
        }

        fun count(minElementBytes: Int): Int {
            val value = compactSize()
            require(value >= 0 && value <= (data.size - offset) / minElementBytes) { "Truncated transaction" }
            return value.toInt()
        }
    }
}
