package com.tonapps.ledgerkit.app.eth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

class Eip712TypedData(
    val types: Map<String, List<Eip712Field>>,
    val primaryType: String,
    val domain: JsonObject,
    val message: JsonObject,
    val source: String,
) {

    val domainFields: List<Eip712Field>
        get() = types[DOMAIN_TYPE] ?: inferDomainFields(domain)

    val chainId: Long?
        get() = domain["chainId"]?.let { value ->
            try {
                Eip712Values.integer(value).takeIf { it.signum() > 0 && it.bitLength() < Long.SIZE_BITS }?.toLong()
            } catch (_: IllegalArgumentException) {
                null
            }
        }

    val verifyingContract: String?
        get() = domain["verifyingContract"]?.asPrimitiveOrNull()?.content?.lowercase()?.takeIf { ADDRESS.matches(it) }

    fun schemaHash(): ByteArray {
        val schema = types.toSortedMap().entries.joinToString(separator = ",", prefix = "{", postfix = "}") { (name, fields) ->
            fields.joinToString(separator = ",", prefix = "\"$name\":[", postfix = "]") { field ->
                "{\"name\":\"${field.name}\",\"type\":\"${field.type}\"}"
            }
        }
        return MessageDigest.getInstance("SHA-224").digest(schema.toByteArray(Charsets.UTF_8))
    }

    fun messageValues(path: String): List<JsonElement> = path.split('.').fold(listOf<JsonElement>(message)) { values, segment ->
        values.flatMap { value ->
            if (segment == ARRAY_SEGMENT) {
                value.asArrayOrNull().orEmpty()
            } else {
                listOfNotNull(value.asObjectOrNull()?.get(segment))
            }
        }
    }

    companion object {

        const val DOMAIN_TYPE = "EIP712Domain"
        const val ARRAY_SEGMENT = "[]"

        private val json = Json { ignoreUnknownKeys = true }
        private val ADDRESS = Regex("^0x[0-9a-f]{40}$")

        private val canonicalDomainFields = listOf(
            Eip712Field("name", "string"),
            Eip712Field("version", "string"),
            Eip712Field("chainId", "uint256"),
            Eip712Field("verifyingContract", "address"),
            Eip712Field("salt", "bytes32"),
        )

        fun parse(source: String): Eip712TypedData {
            val root = json.parseToJsonElement(source).jsonObject
            val typesObject = root["types"]?.jsonObject ?: throw IllegalArgumentException("Typed data has no types")
            val types = linkedMapOf<String, List<Eip712Field>>()
            for ((typeName, fields) in typesObject) {
                types[typeName] = fields.jsonArray.map { field ->
                    val entry = field.jsonObject
                    Eip712Field(
                        name = entry.getValue("name").jsonPrimitive.content,
                        type = entry.getValue("type").jsonPrimitive.content,
                    )
                }
            }
            val primaryType = root["primaryType"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Typed data has no primaryType")
            require(types.containsKey(primaryType)) { "Primary type $primaryType is not defined" }
            val domain = root["domain"]?.jsonObject ?: JsonObject(emptyMap())
            val message = root["message"]?.jsonObject ?: throw IllegalArgumentException("Typed data has no message")
            if (!types.containsKey(DOMAIN_TYPE)) {
                types[DOMAIN_TYPE] = inferDomainFields(domain)
            }
            val ordered = linkedMapOf<String, List<Eip712Field>>()
            ordered[DOMAIN_TYPE] = types.getValue(DOMAIN_TYPE)
            types.filterKeys { it != DOMAIN_TYPE }.forEach { (name, fields) -> ordered[name] = fields }
            return Eip712TypedData(ordered, primaryType, domain, message, source)
        }

        private fun inferDomainFields(domain: JsonObject): List<Eip712Field> =
            canonicalDomainFields.filter { domain.containsKey(it.name) }
    }
}

data class Eip712Field(val name: String, val type: String)

internal data class Eip712TypeDescriptor(
    val baseName: String,
    val baseSize: Int?,
    val arrayLevels: List<Int?>,
) {

    val isArray: Boolean
        get() = arrayLevels.isNotEmpty()

    val outermostLevel: Int?
        get() = arrayLevels.last()

    fun elementDescriptor(): Eip712TypeDescriptor = copy(arrayLevels = arrayLevels.dropLast(1))

    fun typeString(): String = buildString {
        append(baseName)
        baseSize?.let(::append)
        arrayLevels.forEach { level -> append('[').append(level?.toString().orEmpty()).append(']') }
    }

    companion object {

        private const val MAX_FIXED_ARRAY_SIZE = 255
        private val pattern = Regex("^([A-Za-z_$][A-Za-z0-9_$]*?)(\\d+)?((\\[\\d*])*)$")
        private val arrayPattern = Regex("\\[(\\d*)]")

        fun parse(type: String): Eip712TypeDescriptor {
            val match = pattern.matchEntire(type.trim()) ?: throw IllegalArgumentException("Unsupported EIP-712 type $type")
            val name = match.groupValues[1]
            val size = match.groupValues[2].takeIf { it.isNotEmpty() }?.toInt()
            val levels = arrayPattern.findAll(match.groupValues[3]).map { level ->
                level.groupValues[1].takeIf { it.isNotEmpty() }?.let { value ->
                    val fixed = value.toInt()
                    require(fixed in 1..MAX_FIXED_ARRAY_SIZE) { "Unsupported fixed array size in $type" }
                    fixed
                }
            }.toList()
            val descriptor = if (size != null && !isSizedPrimitive(name)) {
                Eip712TypeDescriptor(name + size, null, levels)
            } else {
                Eip712TypeDescriptor(name, size, levels)
            }
            descriptor.validate(type)
            return descriptor
        }

        private fun Eip712TypeDescriptor.validate(type: String) {
            when (baseName) {
                "int", "uint" -> require(baseSize != null && baseSize in 8..256 && baseSize % 8 == 0) {
                    "Unsupported integer type $type"
                }
                "bytes" -> require(baseSize == null || baseSize in 1..32) { "Unsupported bytes type $type" }
            }
        }


        private fun isSizedPrimitive(name: String): Boolean = name == "int" || name == "uint" || name == "bytes"
    }
}

internal fun JsonElement.asArrayOrNull(): JsonArray? = this as? JsonArray

internal fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement.asPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive
