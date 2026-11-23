package com.tonapps.wallet.data.ledger.signing

import com.tonapps.ledgerkit.app.eth.Eip712ClearSigning
import com.tonapps.ledgerkit.app.eth.Eip712FieldFilter
import com.tonapps.ledgerkit.app.eth.Eip712FieldFormat
import com.tonapps.ledgerkit.app.eth.Eip712Filters
import com.tonapps.ledgerkit.app.eth.Eip712TypedData
import com.tonapps.wallet.data.ledger.analytics.LedgerIssueSite
import com.tonapps.wallet.data.ledger.analytics.LedgerNonFatals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal class EvmTypedDataFilters(
    private val tokenDescriptors: EvmTokenDescriptors,
    private val catalog: () -> String = ::bundledCatalog,
) {

    private val entries: Map<String, FiltersDto> by lazy {
        json.decodeFromString(MapSerializer(String.serializer(), FiltersDto.serializer()), catalog())
    }

    suspend fun clearSigning(typedDataJson: String): Eip712ClearSigning? = try {
        val typedData = Eip712TypedData.parse(typedDataJson)
        val chainId = typedData.chainId
        val filters = withContext(Dispatchers.Default) { find(typedData) }
        if (chainId == null || filters == null) {
            null
        } else {
            val tokens = coroutineScope {
                filters.tokenContracts(typedData)
                    .map { contract -> async { tokenDescriptors.forContract(chainId, contract)?.let { contract to it } } }
                    .awaitAll()
                    .filterNotNull()
            }
            Eip712ClearSigning(filters, tokens.toMap())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LedgerNonFatals.report(e, LedgerIssueSite.ClearSigning)
        null
    }

    fun find(typedData: Eip712TypedData): Eip712Filters? {
        val chainId = typedData.chainId ?: return null
        val contract = typedData.verifyingContract ?: ZERO_ADDRESS
        val entry = entries["$chainId:$contract:${typedData.schemaHash().toHexString()}"] ?: return null
        val fields = entry.fields.map { field ->
            Eip712FieldFilter(
                path = field.path,
                format = field.format.toFieldFormat() ?: return null,
                label = field.label,
                signature = field.signature.hexToBytes(),
                coinRef = field.coinRef,
            )
        }
        return Eip712Filters(entry.contractName.label, entry.contractName.signature.hexToBytes(), fields)
    }

    private fun String.toFieldFormat(): Eip712FieldFormat? = when (this) {
        "raw" -> Eip712FieldFormat.Raw
        "datetime" -> Eip712FieldFormat.DateTime
        "token" -> Eip712FieldFormat.Token
        "amount" -> Eip712FieldFormat.Amount
        else -> null
    }

    @Serializable
    private class FiltersDto(val contractName: ContractNameDto, val fields: List<FieldDto>)

    @Serializable
    private class ContractNameDto(val label: String, val signature: String)

    @Serializable
    private class FieldDto(
        val format: String,
        val label: String,
        val path: String,
        val signature: String,
        @SerialName("coin_ref") val coinRef: Int? = null,
    )

    private companion object {
        const val CATALOG = "/ledger/eip712_filters.json"
        const val ZERO_ADDRESS = "0x0000000000000000000000000000000000000000"

        val json = Json { ignoreUnknownKeys = true }

        fun bundledCatalog(): String = requireNotNull(EvmTypedDataFilters::class.java.getResourceAsStream(CATALOG)) {
            "Missing $CATALOG"
        }.bufferedReader().use { it.readText() }
    }
}
