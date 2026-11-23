package com.tonapps.ledgerkit.app.eth

import kotlinx.serialization.json.JsonNull

class Eip712ClearSigning(
    val filters: Eip712Filters,
    val tokenDescriptors: Map<String, ByteArray>,
)

class Eip712Filters(
    val messageName: String,
    val messageSignature: ByteArray,
    val fields: List<Eip712FieldFilter>,
) {

    fun tokenContracts(typedData: Eip712TypedData): List<String> = buildList {
        for (field in fields) {
            when {
                field.format == Eip712FieldFormat.Token ->
                    typedData.messageValues(field.path).firstOrNull { it !is JsonNull }?.let { add(Eip712Values.text(it).lowercase()) }
                field.format == Eip712FieldFormat.Amount && field.coinRef == VERIFYING_CONTRACT_REF ->
                    typedData.verifyingContract?.let(::add)
            }
        }
    }.distinct()

    companion object {
        const val VERIFYING_CONTRACT_REF = 0xff
    }
}

class Eip712FieldFilter(
    val path: String,
    val format: Eip712FieldFormat,
    val label: String,
    val signature: ByteArray,
    val coinRef: Int? = null,
)

enum class Eip712FieldFormat {
    Raw,
    DateTime,
    Token,
    Amount,
}
