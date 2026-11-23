package com.tonapps.ledgerkit.session

import com.tonapps.ledgerkit.LedgerAppId

data class LedgerAppInfo(val name: String, val version: String) {

    val isDashboard: Boolean
        get() = name.trimEnd(' ', '\u0000') in DASHBOARD_NAMES

    val appId: LedgerAppId?
        get() = LedgerAppId.fromOpenName(name)

    fun isAtLeast(requiredVersion: String): Boolean = LedgerVersion.compare(version, requiredVersion) >= 0

    private companion object {
        val DASHBOARD_NAMES = setOf("BOLOS", "OLOS")
    }
}

object LedgerVersion {

    fun compare(left: String, right: String): Int {
        val leftParts = parse(left)
        val rightParts = parse(right)
        for (position in 0 until maxOf(leftParts.size, rightParts.size)) {
            val difference = leftParts.getOrElse(position) { 0 } - rightParts.getOrElse(position) { 0 }
            if (difference != 0) {
                return difference
            }
        }
        return 0
    }

    private fun parse(version: String): List<Int> = version
        .substringBefore('-')
        .split('.')
        .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}
