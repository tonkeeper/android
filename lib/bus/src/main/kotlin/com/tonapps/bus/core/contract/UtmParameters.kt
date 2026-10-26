package com.tonapps.bus.core.contract

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import java.net.URLDecoder

private const val SOURCE_KEY = "utm_source"
private const val MEDIUM_KEY = "utm_medium"
private const val CAMPAIGN_KEY = "utm_campaign"
private const val TERM_KEY = "utm_term"
private const val CONTENT_KEY = "utm_content"
private const val DECODE_PASSES = 2

@Serializable
@Parcelize
data class UtmParameters(
    val source: String? = null,
    val medium: String? = null,
    val campaign: String? = null,
    val term: String? = null,
    val content: String? = null,
) : Parcelable {

    val isEmpty: Boolean
        get() = source == null && medium == null && campaign == null && term == null && content == null

    fun toEventProps(): Map<String, String> {
        return buildMap {
            source?.let { put(SOURCE_KEY, it) }
            medium?.let { put(MEDIUM_KEY, it) }
            campaign?.let { put(CAMPAIGN_KEY, it) }
            term?.let { put(TERM_KEY, it) }
            content?.let { put(CONTENT_KEY, it) }
        }
    }

    companion object {

        val Empty = UtmParameters()

        fun fromLink(link: String?): UtmParameters {
            val query = link?.let(::findQuery) ?: return Empty
            return fromValues(queryValues(query))
        }

        fun fromValues(values: Map<String, String>): UtmParameters {
            return UtmParameters(
                source = values.utmValue(SOURCE_KEY),
                medium = values.utmValue(MEDIUM_KEY),
                campaign = values.utmValue(CAMPAIGN_KEY),
                term = values.utmValue(TERM_KEY),
                content = values.utmValue(CONTENT_KEY),
            )
        }

        private fun Map<String, String>.utmValue(key: String): String? {
            return get(key)?.trim()?.ifEmpty { null }
        }

        private fun findQuery(link: String): String? {
            return generateSequence(link) { candidate -> decodeOrNull(candidate)?.takeIf { it != candidate } }
                .take(DECODE_PASSES + 1)
                .firstOrNull { it.contains('?') }
                ?.substringAfter('?')
                ?.substringBefore('#')
        }

        private fun queryValues(query: String): Map<String, String> {
            val rawValues = LinkedHashMap<String, String>()
            for (item in query.split('&')) {
                val name = decodeOrNull(item.substringBefore('=')) ?: continue
                rawValues.putIfAbsent(name, item.substringAfter('=', missingDelimiterValue = ""))
            }
            return rawValues.mapNotNull { (name, rawValue) ->
                decodeOrNull(rawValue)?.trim()?.ifEmpty { null }?.let { name to it }
            }.toMap()
        }

        private fun decodeOrNull(value: String): String? {
            return try {
                URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
