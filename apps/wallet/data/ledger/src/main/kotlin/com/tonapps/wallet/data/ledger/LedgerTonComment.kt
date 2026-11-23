package com.tonapps.wallet.data.ledger

object LedgerTonComment {

    private const val MAX_LENGTH = 120
    private val PRINTABLE_ASCII = 0x20..0x7e

    fun isClearSignable(comment: String): Boolean = comment.length <= MAX_LENGTH && isPrintableAscii(comment)

    fun isPrintableAscii(comment: String): Boolean = comment.all { it.code in PRINTABLE_ASCII }
}
