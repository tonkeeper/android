package com.tonapps.wallet.data.settings

@JvmInline
value class BatteryTransaction(val code: Int) {

    companion object {
        val UNKNOWN = BatteryTransaction(-1)
        val SWAP = BatteryTransaction(0)
        val JETTON = BatteryTransaction(1)
        val NFT = BatteryTransaction(2)
        val TRC20 = BatteryTransaction(3)

        val entries = arrayOf(SWAP, JETTON, NFT)

        fun Array<BatteryTransaction>.toIntArray(): IntArray {
            return map { it.code }.toIntArray()
        }

        fun List<BatteryTransaction>.toIntArray(): IntArray {
            return map { it.code }.toIntArray()
        }

        fun of(code: Int): BatteryTransaction {
            return entries.firstOrNull { it.code == code } ?: UNKNOWN
        }

    }

}