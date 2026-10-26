package com.tonapps.blockchain.tron

class TronTransactionMismatchException(
    val field: String,
    val expected: String,
    val actual: String,
) : RuntimeException("Tron transaction mismatch: $field expected=$expected actual=$actual")
