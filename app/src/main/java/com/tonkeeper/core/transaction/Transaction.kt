package com.tonkeeper.core.transaction

data class Transaction(
    val id: String,
    val status: Status,
    val destination: String?,
    val name: String?,
    val jettonAddress: String?,
    val amount: Float?,
    val comment: String?
) {

    enum class Status {
        Draft, Pending, Done
    }

}