package com.tonapps.perps.funding

import java.io.ByteArrayOutputStream
import java.util.Base64

internal object TestPsbt {

    const val SENDER_SCRIPT = "00149ef643bab64ab2e869ee1109d4ccdc61179cc364"

    const val DEPOSITORY_SCRIPT = "001416d73780aae93325aae14a59fb2f01d909e6320d"
    const val DEPOSITORY = "bc1qzmtn0q92ayejt2hpffvlktcpmyy7vvsd06sefu"

    const val FOREIGN_SCRIPT = "0014211b37827fadd3f121db10767e68c66010a4caed"
    val FOREIGN_TAPROOT_SCRIPT = "5120" + "5a".repeat(32)

    const val ORDER_ID = "0xba023b545859f2141fa73f8325bc035377a6c3fc1b1096624b5112aba2687951"
    val ORDER_SCRIPT: String = opReturn(ORDER_ID)

    const val FEE = 1_398L
    const val CHANGE = 5_499L

    class Input(val script: String?, val value: Long, val sighash: Long? = null)

    class Output(val script: String, val value: Long)

    fun deposit(amount: Long, fee: Long = FEE, change: Long = CHANGE): String = build(
        inputs = listOf(Input(SENDER_SCRIPT, amount + fee + change)),
        outputs = listOf(Output(DEPOSITORY_SCRIPT, amount), Output(SENDER_SCRIPT, change), Output(ORDER_SCRIPT, 0)),
    )

    fun opReturn(text: String): String = "6a" + push(text.toByteArray(Charsets.US_ASCII))

    fun build(inputs: List<Input>, outputs: List<Output>): String {
        val unsigned = ByteArrayOutputStream().apply {
            write(littleEndian(2, 4))
            write(compactSize(inputs.size.toLong()))
            inputs.forEachIndexed { index, _ ->
                write(ByteArray(32) { (index + 1).toByte() })
                write(littleEndian(0, 4))
                write(compactSize(0))
                write(littleEndian(0xfffffffdL, 4))
            }
            write(compactSize(outputs.size.toLong()))
            outputs.forEach { write(output(it.script, it.value)) }
            write(littleEndian(0, 4))
        }.toByteArray()

        val psbt = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x70, 0x73, 0x62, 0x74, 0xff.toByte()))
            write(entry(byteArrayOf(0x00), unsigned))
            write(0x00)
            inputs.forEach { input ->
                input.script?.let { write(entry(byteArrayOf(0x01), output(it, input.value))) }
                input.sighash?.let { write(entry(byteArrayOf(0x03), littleEndian(it, 4))) }
                write(0x00)
            }
            outputs.forEach { _ -> write(0x00) }
        }.toByteArray()
        return Base64.getEncoder().encodeToString(psbt)
    }

    private fun push(data: ByteArray): String {
        val prefix = if (data.size <= 0x4b) {
            byteArrayOf(data.size.toByte())
        } else {
            byteArrayOf(0x4c, data.size.toByte())
        }
        return (prefix + data).hex()
    }

    private fun output(script: String, value: Long): ByteArray {
        val bytes = script.unhex()
        return littleEndian(value, 8) + compactSize(bytes.size.toLong()) + bytes
    }

    private fun entry(key: ByteArray, value: ByteArray): ByteArray =
        compactSize(key.size.toLong()) + key + compactSize(value.size.toLong()) + value

    private fun compactSize(value: Long): ByteArray = when {
        value < 0xfd -> byteArrayOf(value.toByte())
        value <= 0xffff -> byteArrayOf(0xfd.toByte()) + littleEndian(value, 2)
        else -> byteArrayOf(0xfe.toByte()) + littleEndian(value, 4)
    }

    private fun littleEndian(value: Long, size: Int): ByteArray = ByteArray(size) { (value ushr (8 * it)).toByte() }

    private fun String.unhex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
}
