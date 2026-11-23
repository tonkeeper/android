package com.tonapps.ledgerkit.app.eth

import com.tonapps.ledgerkit.apdu.Apdu
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayOutputStream
import java.math.BigInteger

internal class Eip712LedgerEncoder(
    private val typedData: Eip712TypedData,
    private val clearSigning: Eip712ClearSigning? = null,
) {

    private val fieldFilters = clearSigning?.filters?.fields.orEmpty().associateBy { it.path }
    private val providedTokenRefs = mutableSetOf<Int>()

    fun structDefinitionCommands(): List<ByteArray> = buildList {
        for ((structName, fields) in typedData.types.toSortedMap()) {
            requireValidIdentifier(structName)
            fields.forEach { requireValidIdentifier(it.name) }
            add(Apdu.build(CLA, INS_STRUCT_DEFINITION, 0, P2_DEFINITION_NAME, structName.toByteArray(Charsets.UTF_8)))
            for (field in fields) {
                add(Apdu.build(CLA, INS_STRUCT_DEFINITION, 0, P2_DEFINITION_FIELD, encodeFieldDefinition(field)))
            }
        }
    }

    fun structImplementationCommands(): List<ByteArray> = buildList {
        providedTokenRefs.clear()
        if (clearSigning != null) {
            add(Apdu.build(CLA, INS_FILTERING, P1_FILTER_STANDARD, P2_FILTER_ACTIVATE))
        }
        add(rootCommand(Eip712TypedData.DOMAIN_TYPE))
        encodeStruct(typedData.domainFields, typedData.domain, path = null, out = this)
        clearSigning?.let { add(messageInfoCommand(it.filters)) }
        add(rootCommand(typedData.primaryType))
        encodeStruct(typedData.types.getValue(typedData.primaryType), typedData.message, path = "", out = this)
    }

    private fun rootCommand(name: String): ByteArray =
        Apdu.build(CLA, INS_STRUCT_IMPLEMENTATION, P1_COMPLETE, P2_IMPLEMENTATION_ROOT, name.toByteArray(Charsets.UTF_8))

    private fun encodeStruct(fields: List<Eip712Field>, value: JsonObject, path: String?, out: MutableList<ByteArray>) {
        for (field in fields) {
            val fieldValue = value[field.name] ?: throw IllegalArgumentException("Typed data is missing field ${field.name}")
            val fieldPath = path?.let { if (it.isEmpty()) { field.name } else { "$it.${field.name}" } }
            encodeValue(Eip712TypeDescriptor.parse(field.type), fieldValue, fieldPath, out)
        }
    }

    private fun encodeValue(descriptor: Eip712TypeDescriptor, value: JsonElement, path: String?, out: MutableList<ByteArray>) {
        if (descriptor.isArray) {
            val items = value.asArrayOrNull() ?: throw IllegalArgumentException("Expected an array for ${descriptor.baseName}")
            val expectedSize = descriptor.outermostLevel
            require(expectedSize == null || expectedSize == items.size) { "Fixed array size mismatch" }
            require(items.size <= MAX_ARRAY_SIZE) { "EIP-712 array is too long for the device" }
            out += Apdu.build(CLA, INS_STRUCT_IMPLEMENTATION, P1_COMPLETE, P2_IMPLEMENTATION_ARRAY, byteArrayOf(items.size.toByte()))
            val entryPath = path?.let { "$it.${Eip712TypedData.ARRAY_SEGMENT}" }
            if (items.isEmpty() && entryPath != null) {
                discardFilters(entryPath, out)
            }
            val element = descriptor.elementDescriptor()
            items.forEach { encodeValue(element, it, entryPath, out) }
            return
        }
        val customFields = typedData.types[descriptor.baseName]
        if (customFields != null && !descriptor.isPrimitive()) {
            val struct = value.asObjectOrNull() ?: throw IllegalArgumentException("Expected an object for ${descriptor.baseName}")
            encodeStruct(customFields, struct, path, out)
            return
        }
        path?.let(fieldFilters::get)?.let { filter ->
            tokenDescriptor(filter, value)?.let { out += Apdu.build(CLA, INS_PROVIDE_TOKEN_INFORMATION, 0, 0, it) }
            out += fieldFilterCommand(filter, discarded = false)
        }
        out += fieldCommands(encodePrimitive(descriptor, value))
    }

    private fun tokenDescriptor(filter: Eip712FieldFilter, value: JsonElement): ByteArray? {
        val coinRef = filter.coinRef ?: return null
        val contract = when {
            filter.format == Eip712FieldFormat.Token -> Eip712Values.text(value).lowercase()
            filter.format == Eip712FieldFormat.Amount && coinRef == Eip712Filters.VERIFYING_CONTRACT_REF -> typedData.verifyingContract
            else -> null
        } ?: return null
        if (!providedTokenRefs.add(coinRef)) {
            return null
        }
        return clearSigning?.tokenDescriptors?.get(contract)
    }

    private fun discardFilters(entryPath: String, out: MutableList<ByteArray>) {
        for (filter in fieldFilters.values.filter { it.path.startsWith(entryPath) }) {
            out += Apdu.build(CLA, INS_FILTERING, P1_FILTER_STANDARD, P2_FILTER_DISCARDED_PATH, lengthPrefixed(filter.path.toByteArray(Charsets.UTF_8)))
            out += fieldFilterCommand(filter, discarded = true)
        }
    }

    private fun messageInfoCommand(filters: Eip712Filters): ByteArray {
        require(filters.fields.size <= MAX_FILTERS) { "Too many EIP-712 filters" }
        val payload = lengthPrefixed(filters.messageName.toByteArray(Charsets.UTF_8)) +
            byteArrayOf(filters.fields.size.toByte()) +
            lengthPrefixed(filters.messageSignature)
        return Apdu.build(CLA, INS_FILTERING, P1_FILTER_STANDARD, P2_FILTER_MESSAGE_INFO, payload)
    }

    private fun fieldFilterCommand(filter: Eip712FieldFilter, discarded: Boolean): ByteArray {
        val label = lengthPrefixed(filter.label.toByteArray(Charsets.UTF_8))
        val signature = lengthPrefixed(filter.signature)
        val (p2, payload) = when (filter.format) {
            Eip712FieldFormat.Raw -> P2_FILTER_RAW to label + signature
            Eip712FieldFormat.DateTime -> P2_FILTER_DATE_TIME to label + signature
            Eip712FieldFormat.Token -> P2_FILTER_AMOUNT_JOIN_TOKEN to byteArrayOf(requireCoinRef(filter)) + signature
            Eip712FieldFormat.Amount -> P2_FILTER_AMOUNT_JOIN_VALUE to label + byteArrayOf(requireCoinRef(filter)) + signature
        }
        val p1 = if (discarded) { P1_FILTER_DISCARDED } else { P1_FILTER_STANDARD }
        return Apdu.build(CLA, INS_FILTERING, p1, p2, payload)
    }

    private fun requireCoinRef(filter: Eip712FieldFilter): Byte {
        val coinRef = requireNotNull(filter.coinRef) { "EIP-712 filter ${filter.path} has no token reference" }
        require(coinRef in 0..MAX_BYTE) { "EIP-712 filter ${filter.path} has an invalid token reference" }
        return coinRef.toByte()
    }

    private fun lengthPrefixed(value: ByteArray): ByteArray {
        require(value.size <= MAX_BYTE) { "EIP-712 filter value is too long for the device" }
        return byteArrayOf(value.size.toByte()) + value
    }

    private fun fieldCommands(value: ByteArray): List<ByteArray> {
        require(value.size <= MAX_FIELD_LENGTH) { "EIP-712 field is too long for the device" }
        val payload = byteArrayOf((value.size ushr 8).toByte(), value.size.toByte()) + value
        val chunks = payload.asList().chunked(Apdu.MAX_DATA_LENGTH).map { it.toByteArray() }
        return chunks.mapIndexed { index, chunk ->
            val p1 = if (index == chunks.lastIndex) { P1_COMPLETE } else { P1_PARTIAL }
            Apdu.build(CLA, INS_STRUCT_IMPLEMENTATION, p1, P2_IMPLEMENTATION_FIELD, chunk)
        }
    }

    private fun encodePrimitive(descriptor: Eip712TypeDescriptor, value: JsonElement): ByteArray = when (descriptor.baseName) {
        "int" -> encodeInteger(value, descriptor.baseSize ?: DEFAULT_INT_BITS, signed = true)
        "uint" -> encodeInteger(value, descriptor.baseSize ?: DEFAULT_INT_BITS, signed = false)
        "bool" -> byteArrayOf(if (Eip712Values.bool(value)) { 1 } else { 0 })
        "address" -> {
            val bytes = Eip712Values.hex(value)
            require(bytes.size == ADDRESS_LENGTH) { "Invalid address in typed data" }
            bytes
        }
        "string" -> {
            val text = Eip712Values.text(value)
            require(!text.contains('\u0000')) { "Strings with NUL characters cannot be signed on Ledger" }
            text.toByteArray(Charsets.UTF_8)
        }
        "bytes" -> {
            val bytes = Eip712Values.hex(value)
            val size = descriptor.baseSize
            if (size == null) {
                bytes
            } else {
                require(bytes.size <= size) { "bytes$size value is too long" }
                bytes.copyOf(size)
            }
        }
        else -> throw IllegalArgumentException("Unsupported EIP-712 type ${descriptor.baseName}")
    }

    private fun encodeInteger(value: JsonElement, bits: Int, signed: Boolean): ByteArray {
        val number = Eip712Values.integer(value)
        Eip712Values.requireIntegerRange(number, bits, signed)
        val unsigned = if (number.signum() < 0) { BigInteger.ONE.shiftLeft(bits).add(number) } else { number }
        val bytes = unsigned.toByteArray()
        return if (bytes.size > 1 && bytes[0] == 0.toByte()) { bytes.copyOfRange(1, bytes.size) } else { bytes }
    }

    private fun encodeFieldDefinition(field: Eip712Field): ByteArray {
        val descriptor = Eip712TypeDescriptor.parse(field.type)
        val isCustom = !descriptor.isPrimitive()
        val typeCode = when {
            isCustom -> TYPE_CUSTOM
            descriptor.baseName == "int" -> TYPE_INT
            descriptor.baseName == "uint" -> TYPE_UINT
            descriptor.baseName == "address" -> TYPE_ADDRESS
            descriptor.baseName == "bool" -> TYPE_BOOL
            descriptor.baseName == "string" -> TYPE_STRING
            descriptor.baseName == "bytes" && descriptor.baseSize != null -> TYPE_FIXED_BYTES
            descriptor.baseName == "bytes" -> TYPE_DYNAMIC_BYTES
            else -> throw IllegalArgumentException("Unsupported EIP-712 type ${field.type}")
        }
        val typeSize = when (typeCode) {
            TYPE_INT, TYPE_UINT -> (descriptor.baseSize ?: DEFAULT_INT_BITS) / BITS_PER_BYTE
            TYPE_FIXED_BYTES -> descriptor.baseSize
            else -> null
        }
        val out = ByteArrayOutputStream()
        var typeDescriptor = typeCode
        if (descriptor.isArray) {
            typeDescriptor = typeDescriptor or TYPE_ARRAY_FLAG
        }
        if (typeSize != null) {
            typeDescriptor = typeDescriptor or TYPE_SIZE_FLAG
        }
        out.write(typeDescriptor)
        if (isCustom) {
            val name = descriptor.baseName.toByteArray(Charsets.UTF_8)
            out.write(name.size)
            out.write(name)
        }
        if (typeSize != null) {
            out.write(typeSize)
        }
        if (descriptor.isArray) {
            out.write(descriptor.arrayLevels.size)
            for (level in descriptor.arrayLevels) {
                if (level == null) {
                    out.write(ARRAY_DYNAMIC)
                } else {
                    out.write(ARRAY_FIXED)
                    out.write(level)
                }
            }
        }
        val key = field.name.toByteArray(Charsets.UTF_8)
        out.write(key.size)
        out.write(key)
        return out.toByteArray()
    }

    private fun Eip712TypeDescriptor.isPrimitive(): Boolean = baseName in Eip712Values.PRIMITIVES

    private fun requireValidIdentifier(name: String) {
        require(name.isNotEmpty() && name.none { it == '"' || it == '\\' || it == '.' || it == '\u0000' }) {
            "EIP-712 name $name cannot be signed on Ledger"
        }
    }

    private companion object {
        const val CLA = 0xe0
        const val INS_STRUCT_DEFINITION = 0x1a
        const val INS_STRUCT_IMPLEMENTATION = 0x1c
        const val INS_FILTERING = 0x1e
        const val INS_PROVIDE_TOKEN_INFORMATION = 0x0a
        const val P2_DEFINITION_NAME = 0x00
        const val P2_DEFINITION_FIELD = 0xff
        const val P1_COMPLETE = 0x00
        const val P1_PARTIAL = 0x01
        const val P2_IMPLEMENTATION_ROOT = 0x00
        const val P2_IMPLEMENTATION_ARRAY = 0x0f
        const val P2_IMPLEMENTATION_FIELD = 0xff
        const val P1_FILTER_STANDARD = 0x00
        const val P1_FILTER_DISCARDED = 0x01
        const val P2_FILTER_ACTIVATE = 0x00
        const val P2_FILTER_DISCARDED_PATH = 0x01
        const val P2_FILTER_MESSAGE_INFO = 0x0f
        const val P2_FILTER_DATE_TIME = 0xfc
        const val P2_FILTER_AMOUNT_JOIN_TOKEN = 0xfd
        const val P2_FILTER_AMOUNT_JOIN_VALUE = 0xfe
        const val P2_FILTER_RAW = 0xff
        const val TYPE_ARRAY_FLAG = 0x80
        const val TYPE_SIZE_FLAG = 0x40
        const val TYPE_CUSTOM = 0
        const val TYPE_INT = 1
        const val TYPE_UINT = 2
        const val TYPE_ADDRESS = 3
        const val TYPE_BOOL = 4
        const val TYPE_STRING = 5
        const val TYPE_FIXED_BYTES = 6
        const val TYPE_DYNAMIC_BYTES = 7
        const val ARRAY_DYNAMIC = 0
        const val ARRAY_FIXED = 1
        const val DEFAULT_INT_BITS = 256
        const val BITS_PER_BYTE = 8
        const val ADDRESS_LENGTH = 20
        const val MAX_ARRAY_SIZE = 255
        const val MAX_FIELD_LENGTH = 0xffff
        const val MAX_BYTE = 0xff
        const val MAX_FILTERS = 0xff
    }
}
