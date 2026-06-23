package io.exchangeapi.infrastructure

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(val message: String) : ApiResult<Nothing>
}

fun extractApiErrorMessage(e: Throwable): String {
    val body = when (e) {
        is ClientException -> (e.response as? ClientError<*>)?.body as? String
        is io.infrastructure.ClientException -> (e.response as? io.infrastructure.ClientError<*>)?.body as? String
        is ServerException -> (e.response as? ServerError<*>)?.body as? String
        is io.infrastructure.ServerException -> (e.response as? io.infrastructure.ServerError<*>)?.body as? String
        else -> null
    }
    if (body != null) {
        try {
            val json = Json.parseToJsonElement(body).jsonObject
            val error = json["error"]?.jsonPrimitive?.content
            if (!error.isNullOrEmpty()) return error
        } catch (_: Exception) {}
    }
    return e.message ?: "Unknown error"
}
