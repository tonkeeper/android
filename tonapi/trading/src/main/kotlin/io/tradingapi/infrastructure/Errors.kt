@file:Suppress("unused")
package io.tradingapi.infrastructure

import java.lang.RuntimeException
open class ClientException(message: String? = null, val statusCode: Int = -1, val response: Response? = null) : RuntimeException(message) {
    companion object {
        private const val serialVersionUID: Long = 123L
    }
}

open class ServerException(message: String? = null, val statusCode: Int = -1, val response: Response? = null) : RuntimeException(message) {
    companion object {
        private const val serialVersionUID: Long = 456L
    }
}
