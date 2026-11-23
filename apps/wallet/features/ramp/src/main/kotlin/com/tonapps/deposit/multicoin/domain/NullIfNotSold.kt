package com.tonapps.deposit.multicoin.domain

import io.infrastructure.ClientException

// `/v2/onramp/asset` answers 404 for an asset the onramp doesn't sell; an unsupported fiat is an empty list.
internal suspend fun <T> nullIfNotSold(block: suspend () -> T): T? {
    return try {
        block()
    } catch (e: ClientException) {
        if (e.statusCode == HTTP_NOT_FOUND) {
            null
        } else {
            throw e
        }
    }
}

private const val HTTP_NOT_FOUND = 404
